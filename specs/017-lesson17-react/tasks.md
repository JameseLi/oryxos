---

description: "Task list for 第 17 节 ReAct 循环（想-做-看引擎 + 统一入口 + tool_invocations 审计）"
---

# Tasks: 第 17 节 ReAct 循环

**Input**: Design documents from `/specs/017-lesson17-react/`（spec.md + plan.md）；课件 `docs/class/第17节：ReAct 原理解析、实现与代码讲解.md`。

**Prerequisites**: plan.md（required）、spec.md（required）。前序依赖：**第 16 节全部交付物**（H0 已存在性核对：Profile/ProfileLoader/ProfileRegistry/ProviderService/ToolSchemaAdapter/LlmCall 审计链/001-init.sql/surefire 两档跑法均就位）。

**Tests**: harness 先行/伴随--测试任务与对应实现任务成对出现；`mvn clean verify` 全绿为实现完成定义；本节无集成冒烟（课件：全部不碰网络）。

**Organization**: 按 spec.md 的依赖序组织（存储底座 -> 域抽象 -> 供给者/组装器 -> 执行器 -> 主循环 -> 编排者）；包根 `com.oryxos`，标识符一律英文 ASCII（宪法 §9）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 不同文件、无未完成依赖，可并行
- **[Story]**: US1（ReAct 循环）/ US2（Prompt 组装与上下文）/ US3（工具执行与审计）/ US4（统一入口编排）/ 共享底座
- 路径相对仓库根；包根：`com.oryxos`

## Path Conventions

- Maven 多模块：`<module>/src/main/java/com/oryxos/<domain>/...`
- 测试：`<module>/src/test/java/com/oryxos/<domain>/...`
- 建表脚本：`oryxos-storage/src/main/resources/scripts/sql/001-init.sql`（追加，不新建文件）
- **装配纪律（spec R5）**：core 交付类一律不加 Spring 注解（纯 POJO）；唯一 Spring 触点是 storage 的 `JpaToolInvocationAudit`（`@Component`）

---

## Phase 0: 前置核对（已完成）

- [x] T-R0 H0 依赖检查：16 节交付物逐项存在性核对通过；分支 `017-lesson17-react` 就位；Spring AI 1.1.8 关键 API 已 javap 预核（`AssistantMessage$ToolCall`/`hasToolCalls`/`getText`/`ToolResponseMessage$ToolResponse`+Builder/`ChatResponse(List<Generation>)`/`Generation(AssistantMessage)`/`AssistantMessage.builder()`）。`mvn clean verify` 基线绿（16 节收尾态）。

## Phase 1: 存储底座--`tool_invocations`（US3 审计落库的地基，harness 先行）

**Purpose**: 宪法 VI 审计 Day One 落库；与 `llm_calls` 同口径（spec R6）。

- [ ] T-ST1 手工建表脚本 `001-init.sql` **追加** `tool_invocations` 表（`id/session_id/tool_name/arguments/result/elapsed_ms/success/error_message/created_at`，含 `success`/`error_message` 两列 + `session_id` 索引 + `tool_name` 索引），`CREATE TABLE IF NOT EXISTS` 幂等；脚本头注释更新"第 17 节"说明。
- [ ] T-ST2 [P] `ToolInvocationEntity`（`com.oryxos.storage.entity`，JPA 实体 `@Table(name="tool_invocations")`，字段与脚本逐列对齐，`success` 用 `Boolean` 映射 INTEGER，`created_at` 实体层写入 ISO-8601 字符串--口径照抄 `LlmCallEntity`） + `ToolInvocationRepository`（`com.oryxos.storage.repository`，`JpaRepository<ToolInvocationEntity, Long>`）。
- [ ] T-ST2T `ToolInvocationRepositoryTest`（`com.oryxos.storage`，单测真 SQLite，经 test yaml `spring.sql.init` 执行脚本建表）：存一条 success / 存一条 failure，断言读回字段对齐且 `success`/`error_message` 列真实存在。harness 先行/伴随。
- [ ] T-ST3 [P] `ToolInvocationAudit` 端口（`com.oryxos.core.audit`，接口 `record(String sessionId, String toolName, String arguments, String result, long elapsedMillis, boolean success, String errorMessage)`--与 `LlmCallAudit` 同构 DIP） + `JpaToolInvocationAudit`（`com.oryxos.storage.audit`，`@Component implements ToolInvocationAudit`，映射成 `ToolInvocationEntity` 存库）。
- [ ] T-ST3T `JpaToolInvocationAudit` 落库断言（并入 `ToolInvocationRepositoryTest` 或独立小节）：成功/失败两条 `record(...)` 真落 `tool_invocations` 且可读回（success=false 那条 `error_message` 带原因）。

## Phase 2: core 域抽象（最小类型集，spec R1/R2/R3）

**Purpose**: 课件正文与 harness 引用、交付物清单未列的最小类型--先立骨头，后续节（18/20/22）在其上演进。

- [ ] T-C1 `ToolResult`（`com.oryxos.core.tool`，record：`boolean success` + `String output` + `String errorMessage`；工厂 `ok(output)`/`failure(errorMessage)`；`toModelText()` 回填模型--成功为 output、失败为 `"ERROR: " + 原因`）。
- [ ] T-C2 [P] `OryxTool` 增补 `execute`（`ToolResult execute(String input)`--JSON 参数入、统一结果出；§6.1 终局四方法的前进，16 节 javadoc 预告的 §17 改造点；javadoc 注明"是否可重试 20 节补"）。
- [ ] T-C3 [P] `ToolRegistry` 接口（`com.oryxos.core.tool`：`Optional<OryxTool> find(String name)` + `List<OryxTool> all()`；实现与内置 Tool 是 20 节交付，javadoc 注明）。
- [ ] T-C4 [P] `Session` 域类型（`com.oryxos.core.session`：`id()`/`profileName()`/`messages()`（防御拷贝）+ `append(String userMessage)`/`append(ChatResponse)`（取 `getResult().getOutput()`）/`appendToolResult(AssistantMessage.ToolCall, ToolResult)`（构造 `ToolResponseMessage`：`ToolResponse(call.id(), call.name(), result.toModelText())`）；javadoc 注明 18 节加 session_id 公式与持久化）。
- [ ] T-C5 [P] `SessionManager` 最小接口（`com.oryxos.core.session`：`void save(Session session)`；javadoc 注明完整形态 18 节交付）。
- [ ] T-C6 [P] `MemoryService` 最小接口（`com.oryxos.core.memory`：`String buildContext(Session session)`；签名对齐 22 节课件门面，javadoc 注明 remember/recall 22 节补、实现归 oryxos-memory）。
- [ ] T-C7 [P] `ChatInvoker` LLM 调用端口（`com.oryxos.core.chat`：`ChatResponse chat(String sessionId, Profile profile, Prompt prompt)`；javadoc 注明为破 provider->core 依赖方向所立、16 节 `ProviderService` 实现之）。

## Phase 3: 上下文供给 + Prompt 组装（US2，harness 先行/伴随）

**Purpose**: 课件「配角一/供给者」--四部分组装、截断、日期时间、无缓存重读。

- [ ] T-CL1 `ContextLoader`（`com.oryxos.core.agent`，构造注入工作区根 `Path`；`String load(Profile profile)`：每次调用重读文件**不缓存**；bootstrap 段--未声明或文件缺失记 WARN 后跳过；skills 段--引用文件缺失抛 `IllegalStateException`（消息含文件路径与 Profile 名）；拼接 Bootstrap+Skill 文本返回）。
- [ ] T-CL1T `ContextLoaderTest`（`com.oryxos.core.agent`，单测 `@TempDir`）：①无缓存回归--写文件 load、改文件再 load，断言读到新内容；②Skill 引用缺失报错（断言异常与消息）；③Bootstrap 缺失 WARN（断言不抛异常 + 经 Logback ListAppender 捕获 WARN 事件）。
- [ ] T-PB1 `PromptBuilder`（`com.oryxos.core.react`，构造注入 `ContextLoader` + `ToolRegistry` + 可空 `MemoryService` + `Clock`（默认 `systemDefaultZone()`）；`Prompt build(Session session, Profile profile)`：①SystemMessage = identity（agentName+prompt，未配置跳过）+ contextLoader.load(profile) + 末行 `当前时间：yyyy-MM-dd HH:mm:ss`；②memory 非空装配时追加第二条 SystemMessage（`buildContext(session)`），未装配整体缺席；③历史按"最近 N 轮"（轮=消息条数，默认 20、`settings.maxHistoryTurns` 覆盖）截断；④`availableTools` = registry.all() 按 `profile.tools()` 过滤（空=空子集）；产出 `Prompt(messages, availableTools)`）。
- [ ] T-PB1T `PromptBuilderTest`（`com.oryxos.core.react`，单测）：①四部分顺序正确（system 首位、memory 次位（装配时）、history 随后；tools 在 availableTools）；②历史超 N 轮被截断（造 25 条、N=20，断言只含最近 20 条）；③system prompt 末尾含当前日期时间（固定 `Clock` 断言精确后缀）；④未装配 memory 时无 memory 消息段；⑤Profile.tools 过滤（含不存在的工具名被忽略）。

## Phase 4: 工具执行器（US3 主体，harness 先行/伴随）

**Purpose**: 执行权唯一收口 + 成败双路审计 + 沙箱调用位。

- [ ] T-TE1 `ToolExecutor`（`com.oryxos.core.tool`，构造注入 `ToolRegistry` + `ToolInvocationAudit`；`ToolResult execute(String sessionId, AssistantMessage.ToolCall toolCall)`：注册表按名查找（未知工具按失败路径）；**沙箱调用位**--查找之后、执行之前留显式注释锚点（"Sandbox.enforce 接线点，24 节交付"）；`tool.execute(arguments)`；`RuntimeException` 捕获转失败 `ToolResult`（异常不吞=审计+日志留痕）；无论成败 `audit.record(sessionId, toolName, arguments, result 文本, elapsed, success, errorMessage)`；返回 `ToolResult`）。
- [ ] T-TE1T `ToolExecutorTest`（`com.oryxos.core.tool`，单测 mock 审计端口 + 假注册表/假工具）：①成功写审计 `success=true`（verify record 参数逐项）；②失败也写 `success=false` 带原因（假工具抛 `RuntimeException("boom")`，verify `error_message` 含原因；断言返回的 `ToolResult` 为失败态且 `toModelText()` 前缀 ERROR）；③未知工具走失败路径（原因含"未知工具"）。

## Phase 5: ReAct 主循环（US1 灵魂，harness 先行/伴随）

**Purpose**: 课件骨架逐字对齐--调度/停止/累积/兜底。

- [ ] T-R1 `ReActLoop`（`com.oryxos.core.react`，构造注入 `PromptBuilder` + `ChatInvoker` + `ToolExecutor`；`String run(Session session, String userMessage, Profile profile)`：①`session.append(userMessage)` 先入史；②for i < maxIterations（`settings.maxIterations`，null/未配取默认 **10**，<=0 抛非法参数）：`promptBuilder.build(session, profile)` -> `chatInvoker.chat(session.id(), profile, prompt)` -> `session.append(resp)` -> 无 toolCalls 返回 `getResult().getOutput().getText()`；有则逐个（按序）`toolExecutor.execute(session.id(), call)` -> `session.appendToolResult(call, result)` 进下一轮；③转满上限返回常量 **"达到最大轮数，已停止"**；④LLM 异常不捕获直接上抛。核心循环保持数十行，注释只写"为什么"）。
- [ ] T-R1T `ReActLoopTest`（`com.oryxos.core.react`，单测 mock `ChatInvoker`/`ToolExecutor` + 真 `Session`）：①无工具调用一轮收尾（chat 恰 1 次、返回文本、assistant 消息入史）；②有工具调用则执行并回填进下一轮（首轮带 toolCall、次轮纯文本：chat 恰 2 次、executor 执行 1 次、第二轮收到的 Prompt 消息里含 ToolResponseMessage、session 含工具结果消息）；③**转满最大轮数强制停**（`modelKeepsRequestingTools_stopsAtMaxIterations`，@DisplayName「模型一直要调工具_转满最大轮数强制停」：每轮都回 toolCall 响应、maxIterations=10，断言 `verify(chatInvoker, times(10)).chat(any(), any(), any())` 且 reply 含"达到最大轮数"）；④每轮响应和工具结果都累积进 Session（多轮后 messages 含全部 user/assistant/tool 消息）；⑤Profile.settings 覆盖 maxIterations 生效。

## Phase 6: 统一入口编排（US4 + 16 节接线，harness 先行/伴随）

**Purpose**: 三源归一 + ThreadLocal 卫生 + ProviderService 接端口。

- [ ] T-A1 `ProfileContext`（`com.oryxos.core.agent`，final 工具类 + 私有构造：`ThreadLocal<Profile>`；`set(Profile)`/`current()`（可空）/`clear()`；javadoc 写清"虚拟线程每请求独立、用完必须清"）。
- [ ] T-A2 `AgentService`（`com.oryxos.core.agent`，构造注入 `ProfileRegistry` + `ReActLoop` + `SessionManager`；`String process(Session session, String userMessage)`：`registry.find(session.profileName()).orElseThrow(IllegalStateException)` -> `ProfileContext.set` -> try{ `reActLoop.run` -> `sessionManager.save(session)` -> 返回 } finally{ `ProfileContext.clear()` }）。
- [ ] T-A2T `AgentServiceTest`（`com.oryxos.core.agent`，单测 mock Registry/Loop/SessionManager）：①处理期间 `ProfileContext` 可取到当前 Profile（loop 执行期间经 stub 捕获 `ProfileContext.current()` 断言相等）；②**处理抛异常 finally 也清掉**（`exceptionDuringProcess_profileContextStillCleared`，@DisplayName「处理中抛异常_ProfileContext也必须被清掉」：`when(reActLoop.run(...)).thenThrow(RuntimeException("boom"))`，`assertThrows` + `assertNull(ProfileContext.current())`）；③结束后 Session 被持久化（verify `sessionManager.save(session)`）；④Profile 名不存在抛清晰错误。
- [ ] T-A3 `ProviderService` 接端口（`oryxos-provider`：仅追加 `implements ChatInvoker` + import，**不改任何既有方法/字段/注释语义**；16 节 `ProviderServiceTest` 回归须保持全绿）。

## Phase 7: 节级收尾 DoD（六项证据）

- [ ] T-D1 `mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD/Spotless/Checkstyle）；贴关键输出。
- [ ] T-D2 harness 映射表 6 个测试类存在且非空；两个关键回归方法（`modelKeepsRequestingTools_stopsAtMaxIterations` / `exceptionDuringProcess_profileContextStillCleared`）逐个对号（方法名英文 + @DisplayName 承载课件原文）。
- [ ] T-D3 课件「本节交付物」逐项 ls/grep 存在性核对（代码 8 项 / 测试 5 项 / 表 1 项 / 约定 3 项）。
- [ ] T-D4 前序节（16 节）全部测试回归绿（跨节契约证据：`ProviderServiceTest`/`ToolSchemaAdapterTest`/`ProfileLoaderTest`/`LlmCallRepositoryTest` + boot 冒烟）。
- [ ] T-D5 H4 六条全局不变量逐条自查：①涉外 IO 首行过 `Sandbox.enforce`（本节无涉外工具；ToolExecutor 已留调用位注明 24 节接线）②LLM 调用成败都落 `llm_calls`（16 节 ProviderService 内，循环带 sessionId 串起）、工具执行成败都落 `tool_invocations`（本节 ToolExecutor）③grep 无明文 key ④`session_id` 只在 `SessionManager` 内拼接（18 节交付；本节 sessionId 由 Session 携带传入、无拼接点）⑤无 Reactor/`CompletableFuture`/自建线程池 ⑥无 Spring AI 自动工具执行路径（16 节已关 + 本节执行只经 ToolExecutor）。
- [ ] T-D6 验收报告 + 课件「做完怎么验」剩余人工项清单（Demo 一对话版真模型跑通多轮工具调用；循环未用框架 Agent 封装的 code review 确认）+ 变更总结（给 reviewer 的导读，git status/diff 实测三段结构）。
