---

description: "Task list for 第 16 节 Agent Provider（薄适配层 + Profile 解析 + llm_calls 审计）"
---

# Tasks: 第 16 节 Agent Provider（薄适配层 + Profile 解析）

**Input**: Design documents from `/specs/016-lesson16-provider/`（spec.md + plan.md）；课件 `docs/class/第16节：Agent Provider 原理解析、实现与代码讲解.md`。

**Prerequisites**: plan.md（required）、spec.md（required）。**无前序代码课交付物**（本节为逐节开发第一节）。

**Tests**: harness 先行/伴随——测试任务与对应实现任务成对出现；`mvn clean verify` 全绿为实现完成定义。

**Organization**: 按 spec.md 的 P1→P2 组织；包根 `com.oryxos`，标识符一律英文 ASCII（宪法 §9）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 不同文件、无未完成依赖，可并行
- **[Story]**: US1（Provider 薄适配层）/ US2（Profile 解析）/ 共享底座
- 路径相对仓库根；包根：`com.oryxos`

## Path Conventions

- Maven 多模块：`<module>/src/main/java/com/oryxos/<domain>/...`
- 测试：`<module>/src/test/java/com/oryxos/<domain>/...`
- 配置：`oryxos-boot/src/main/resources/application.yaml`；建表脚本：`oryxos-storage/src/main/resources/scripts/sql/001-init.sql`

---

## Phase 0: Reset（已完成，基线 BUILD SUCCESS）

- [x] T-R0 移除仓库此前 US-1+US-2 合并实现（core/provider/storage/tool/channel-cli/cli 的特性类，保留 package-info/web 脚手架/boot main/Maven 与质量门禁）；`mvn clean verify` 基线绿（仅 boot context smoke）。

## Phase 1: Shared Infrastructure（全仓门禁基座）

**Purpose**: 落地 spec R2（两档跑法）+ 课件「坑三」依赖审慎。

- [ ] T-S1 根 `pom.xml` 的 `maven-surefire-plugin` 配 `<configuration><excludedGroups>integration</excludedGroups></configuration>`：日常 `mvn test` / CI 跳 `@Tag("integration")`；手动 `mvn test -Dgroups=integration` 跑集成冒烟。（影响全仓跑法，spec R2；属课件测试策略句直接要求）
- [ ] T-S2 [P] `mvn dependency:tree` +javap 核 Spring AI 1.1.8 真实 API（`ChatModel.call(Prompt)→ChatResponse`、`ToolCallingChatOptions.internalToolExecutionEnabled(false)/toolCallbacks(..)`、`ToolCallback`/`ToolDefinition` 抽象方法集），把核到的签名写进 plan 的 H3 证据区。核实不到 → 软门禁停下。

---

## Phase 2: 存储底座——`llm_calls`（US1 审计落库的地基，harness 先行）

**Purpose**: 宪法 VI「审计 Day One 落库」；`llm_calls` 表 + 实体 + 仓储 + 手工建表脚本。

- [ ] T-ST1 手工建表脚本 `oryxos-storage/src/main/resources/scripts/sql/001-init.sql`：**仅** `llm_calls` 表（含 `success`/`error_message` 列 + session/provider/model/tokens/elapsed 等列 + `session_id` 索引），`CREATE TABLE IF NOT EXISTS`。
- [ ] T-ST2 [P] `LlmCallEntity`（`com.oryxos.storage.entity`，JPA 实体，`@Table(name="llm_calls")`，字段与脚本对齐，含 `success`/`error_message`） + `LlmCallRepository`（`com.oryxos.storage.repository`，`JpaRepository<LlmCallEntity, Long>`）。
- [ ] T-ST2T `LlmCallRepositoryTest`（`com.oryxos.storage`，单测）：真 SQLite，**执行 001-init.sql 脚本建表**后存一条 success / 存一条 failure，断言读回字段对齐且 `success`/`error_message` 列真实存在（断言逻辑逐条保真）。harness 先行/伴随。

---

## Phase 3: Core 域抽象 + Profile 解析（US2 + US1 的输入侧）

**Purpose**: spec R1 最小类型 + Profile 解析（课件「第零步」）。core 已依赖 `spring-ai-model`，`Prompt` 复用 Spring AI `Message`。

- [ ] T-C1 `Prompt`（`com.oryxos.core.prompt.Prompt`，record：`List<org.springframework.ai.chat.messages.Message> messages` + `List<OryxTool> availableTools`；防御拷贝）。[spec R1]
- [ ] T-C1b [P] `OryxTool` 最小接口（`com.oryxos.core.tool.OryxTool`：`String name()` / `String description()` / `String getInputSchema()`——返回工具参数 JSON Schema；**不含 execute**，执行留给后续节）。[spec R1]
- [ ] T-C1c [P] `LlmCallAudit` 接口（`com.oryxos.core.audit.LlmCallAudit`：`record(String sessionId, String provider, String model, Usage usage, boolean success, String errorMessage, long elapsedMillis)`——DIP 门面，/provider 依赖它、storage 实现）。Usage 用 Spring AI `org.springframework.ai.chat.metadata.Usage`（1.1.8 已验证 `getPromptTokens()/getCompletionTokens()`）；传 `null` 表示无 token 信息。
- [ ] T-C2 [P] `Profile`（`com.oryxos.core.profile.Profile`，record 承载课件全字段：`name/description/identity/provider(name/model/temperature)/tools/skills/mcp_servers/channels/notify_channels/schedules/bootstrap/settings`；嵌套 record `Provider`/`Settings` 等）。
- [ ] T-C3 [P] `ProfileRegistry`（`com.oryxos.core.profile.ProfileRegistry`，`Map<String,Profile>`，`register(Profile)`/`find(name): Optional<Profile>`；本节仅启动扫单条注册路径，运行时 `register()` 留 29 节）。
- [ ] T-C4 `ProfileLoader`（`com.oryxos.core.profile.ProfileLoader`，启动扫 `.oryxos/profiles/*.yaml`，SnakeYAML 解析成 `Profile`，逐个校验「`provider.name` 在全局层 `oryxos.providers` 找得到同名项」、`response` 内 `${ENV}` 占位从 `System.getenv` 解析，坏文件记错误日志、不阻断其余）。
- [ ] T-C4T `ProfileLoaderTest`（`com.oryxos.core.profile`，单测，4 守点逐条保真）：①合法 YAML 全字段解析对齐；②引用不存在 provider 报错清晰（指向文件 + 缺失 name）；③坏文件不阻断其余加载；④`${ENV}` 占位从环境变量解析（用 `System.setProperty`/`getenv` 可控手段）。harness 先行/伴随。

---

## Phase 4: Provider 薄适配层（US1 主体）

**Purpose**: 课件「坑一/坑二/第三步」——显式映射、关自动执行、成功失败都审计。

- [ ] T-P1 `ProviderProperties`（`com.oryxos.provider`，`@ConfigurationProperties("oryxos")`，绑 **list 形态** `List<ProviderEntry> providers`；`ProviderEntry{name, apiKey /* ${ENV} 占位，Spring 解析 */ + 可选 baseUrl/model/type}`）。[R-MAP form：课件示意 list，非旧 map 形态]
- [ ] T-P2 `ChatModelFactory`（`com.oryxos.provider`，按 `ProviderEntry` **程序化构造** `ChatModel`：deepseek→`DeepSeekChatModel`+`DeepSeekApi`、dashscope→`DashScopeChatModel`、openai-compatible→`OpenAiChatModel`+`OpenAiApi` + `baseUrl`；`api-key` 从已解析值传入；**不引 `*-starter`/autoconfigure**）。需 H3 核 1.1.8 构造器签名。
- [ ] T-P3 `ProviderRegistry`（`com.oryxos.provider`，启动读 `ProviderProperties` 逐条 `ChatModelFactory.build` → `Map<String, ChatModel>` 显式映射 + `Map<String,String> modelOf`；`resolve(name): ChatModel` / `modelOf(name)`，未知名抛 `ProviderNotFoundException`）。
- [ ] T-P4 `ToolSchemaAdapter`（`com.oryxos.provider`，`List<ToolCallback> toSpringAiTools(List<OryxTool> tools)`——把 `OryxTool.name()/description()/getInputSchema()` 翻译成 Spring AI `ToolCallback`/`ToolDefinition` 格式；**只翻译、不实现 `call()` 的执行路径**，按 1.1.8 `ToolCallback` 真实抽象方法落地）。
- [ ] T-P4T `ToolSchemaAdapterTest`（`com.oryxos.provider`，单测）：`OryxTool` schema 翻译成 Spring AI 格式后 name/description/parameterSchema 字段一一对齐；产物里**不含任何执行逻辑**（断言翻译结果只是 schema 载体）。harness 先行/伴随。
- [ ] T-P5 `ProviderService`（`com.oryxos.provider`，构造注入 `ProviderRegistry` + `ToolSchemaAdapter` + `LlmCallAudit`；`ChatResponse chat(String sessionId, Profile profile, Prompt prompt)`：①registry.resolve(provider.name)→ChatModel（未知名抛）；②adapter.toSpringAiTools(prompt.availableTools())；③建 `ToolCallingChatOptions.builder().toolCallbacks(...).internalToolExecutionEnabled(false)`（+ model/temperature from Profile）；④包成 `org.springframework.ai.chat.prompt.Prompt(prompt.messages(), options)`；⑤`long t=now; try{ resp=model.call(p); audit.record(...,true,null,...); return resp; }catch(RuntimeException e){ audit.record(...,false,e.getMessage(),...); throw e; }`）。
- [ ] T-P5T `ProviderServiceTest`（`com.oryxos.provider`，单测，mock `ChatModel`/`LlmCallAudit`/adapter）：**4 守点 + 课件 3 个关键回归方法逐条保真**，方法名译英文 + `@DisplayName` 承载课件原文：
  - `routesByProviderName_twoProvidersDoNotCrossover`（坑一回归：双 provider 不串台）
  - `callFails_auditRecordsSuccessFalseThenRethrows`（失败审计回归：抛异常且 audit record success=false + contains 错误信息）
  - `callWithToolSchema_disablesAutoExecution`（坑二回归：捕获 Spring AI `Prompt`，断言 options `isInternalToolExecutionEnabled()==false` 且 `getToolCallbacks()` 非空）
  - 加 `unknownProvider_throwsClearError`（未知名抛清晰异常）
- [ ] T-ST3 `JpaLlmCallAudit`（`com.oryxos.storage.audit`，`@Component implements LlmCallAudit`，注入 `LlmCallRepository`，`record(...)` 映射成 `LlmCallEntity` 存库；`usage` null 时 token 列写 null）。
- [ ] T-ST3T `JpaLlmCallAuditTest`（或并入 `LlmCallRepositoryTest`）：成功/失败两条 `record(...)` 真落 `llm_calls` 且可读回。
- [ ] T-P6 `ProviderSmokeIT`（`com.oryxos.provider`，`@Tag("integration")`，`@SpringBootTest`）：从 env 读真 key、真调一次、断言拿到非空 `ChatResponse` 且 `llm_calls` 多一条 `success=true`。手动 `DEEPSEEK_API_KEY=xxx mvn test -Dgroups=integration -pl oryxos-provider`。

---

## Phase 5: 配置与示例（US1/US2 产出物的运行时具象）

- [ ] T-CF1 `oryxos-boot/src/main/resources/application.yaml` 加 `oryxos.providers` 全局层，按课件 list 形态：`- name: deepseek / api-key: ${DEEPSEEK_API_KEY}` + 第二家 `- name: kimi / api-key: ${KIMI_API_KEY}` 注释示例；不在文件里留真 key。恢复/不恢复 `spring.sql.init.mode` 在此定（与 T-ST1 脚本配合，由本节决定生产是否自动建表）。
- [ ] T-CF2 Profile YAML 示例 `provider` 段——以内联形式出现在 `ProfileLoaderTest` 测试资源（`oryxos-core/src/test/resources/profiles/*.yaml`）里（`.oryxos/` gitignored，不入库），覆盖合法/引用不存在/${ENV} 占位三类样例。

---

## Phase 6: 节级收尾 DoD

- [ ] T-D1 `mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD/Spotless/Checkstyle）；贴关键输出。
- [ ] T-D2 harness 映射表 5 个测试类存在且非空，3 个关键回归方法逐个对号。
- [ ] T-D3 课件「本节交付物」逐项 ls/grep 存在性核对。
- [ ] T-D4 前序节全部测试回归绿（本节为第一节：仅 boot context smoke，已绿）。
- [ ] T-D5 H4 六条全局不变量逐条自查：①涉外 IO 首行过 `Sandbox.enforce`（Sandbox 未就位：本节无涉外工具，注明 24 节接线）②LLM 调用成败都落 `llm_calls`③`grep -r` 无明文 key④`session_id` 只在 `SessionManager` 内拼接（本节无 SessionManager，sessionId 由调用方传入，无拼接点）⑤无 Reactor/`CompletableFuture`/自建线程池⑥无 Spring AI 自动工具执行路径（`internalToolExecutionEnabled(false)`+直 `ChatModel.call` 双保险）。
- [ ] T-D6 验收报告 + 课件「做完怎么验」剩余人工项清单（真模型冒烟、`grep` 明文 key、`mvn dependency:tree`）+ 变更总结（给 reviewer 的导读）。