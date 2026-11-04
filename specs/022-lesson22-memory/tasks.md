---
description: "Task list for 第 22 节 Memory（MemoryService 门面 + 三档 LongTermMemoryStore + MemoryTools）"
---

# Tasks: 第 22 节 Memory

**Input**: Design documents from `/specs/022-lesson22-memory/`（spec.md + plan.md）；课件 `docs/class/第22节：Memory 实现与代码讲解.md`；前序设计第 21 节评审；技术方案 §5/§9.2/§10。

**Prerequisites**: plan.md（required）、spec.md（required）。前序依赖：17 节 `MemoryService` 最小接口/`PromptBuilder`/`Session`；18 节 `SessionManager`/`InitCommand`/`EngineConfiguration`；20 节 `ToolRegistry`/`DefaultToolRegistry.registerAnnotated`/`ToolRegistryConfiguration`/`OryxToolContractTest`（H0 已核对存在）。

**Tests**: harness 先行/伴随——测试任务与对应实现任务成对出现；`mvn clean verify` 全绿为实现完成定义；本节无 `@Tag("integration")` 项（Mem0 用本地假服务，真 server 属人工项）。

**Organization**: 按依赖序组织（core/storage 基座 → memory 依赖 → 三档实现 → 契约测试 → 门面/Tool → 装配接线 → 收尾）；包根 `com.oryxos`，标识符一律英文 ASCII（宪法 §9）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 不同文件、无未完成依赖，可并行
- **[Story]**: US1（门面+契约）/ US2（Markdown）/ US3（SQLite）/ US4（Mem0）/ US5（Tools+装配）/ 共享底座
- 路径相对仓库根；包根 `com.oryxos`

## Path Conventions

- Maven 多模块：`oryxos-{core,storage,memory,tool,cli,boot}/src/{main,test}/java/...`
- 测试：对应模块 `src/test/java/...`
- **装配纪律**：Memory 新 Spring 触点全部在 oryxos-memory（`MemoryConfiguration`）；core 仅 `MemoryService` 演进 + `EngineConfiguration` 注入参数；tool 仅追加 `MemoryTools` 注册；storage 仅新增实体/仓储 + DDL 追加；cli 仅 init 模板 header。
- **零触碰**：`ReActLoop`/`ToolExecutor`/`Session`/`SessionManager`/`PromptBuilder` 本体/`DefaultToolRegistry` 本体/既有 12 个工具实现。

---

## Phase 0: 前置核对与基线

- [x] T-R0 H0 依赖检查：17 节 `MemoryService`（仅 `buildContext`）/`PromptBuilder`/`Session`；18 节 `SessionManager`/`InitCommand.MEMORY_MD`/`EngineConfiguration`（`MemoryService=null` 过渡）；20 节 `ToolRegistry` 接口/`DefaultToolRegistry.registerAnnotated`/`ToolRegistryConfiguration`/`OryxToolContractTest` 逐项存在性核对通过；技术方案 §5/§9.2/§10 与课件/第 21 节评审已读。
- [ ] T-R0b 基线验证：`mvn clean verify` 基线绿（20 节收尾态）——implement 第一个动作先跑；红了当场报告不前进。

## Phase 1: core 门面基座（US1 前置，唯一前序接口改动点）

**Purpose**: 把 17 节最小门面演进为本节交付的三方法门面；`buildContext` 签名不变、`PromptBuilder` 行为不变。

- [ ] T-C1 [P] `MemoryScope`（core `com.oryxos.core.memory`）：`enum { CORE, ARCHIVAL }`；javadoc 契约三（Agent 显式指定、缺省 ARCHIVAL）。
- [ ] T-C2 `MemoryService` 演进（core `com.oryxos.core.memory`）：保留 `String buildContext(Session)`；增 `void remember(String content, MemoryScope scope)`、`List<String> recall(String keyword)`；javadoc 说明 `buildContext` 只返回长期记忆、会话历史由 `PromptBuilder` 独立负责；17 节 javadoc「22 节补齐」兑现。
- [ ] T-C2T `PromptBuilderTest` 兼容改造（core test）：把 `MemoryService` 单方法 lambda 改为显式三方法 stub（`buildContext` 返回原字符串、`remember`/`recall` 空实现），原四段顺序断言逐条保留。**伴随回归**：`mvn -pl oryxos-core test` 绿。
- [ ] T-C3 `EngineConfiguration` 注入（core `com.oryxos.core.agent`）：`promptBuilder(ContextLoader, ToolRegistry, MemoryService)`，删 `MemoryService=null` 过渡与「22 节替换」注释；其余 Bean 零改动。**依赖** T-C2 与 Phase 5 `MemoryService` Bean（编译期只需接口，运行期由 memory 装配）。

## Phase 2: storage 表与仓储（US3 前置，清单外支撑类型 R2）

- [ ] T-S1 `MemoryEntryEntity`（storage `com.oryxos.storage.entity`）：`@Entity @Table(name="memory_entries")`；`Long id`（`@Id @GeneratedValue(strategy=IDENTITY)`）、`String scope`、`String content`、`String createdAt`（ISO-8601，实体层写入）；无参构造 + getter/setter；javadoc 注明手工脚本维护、不依赖 ddl-auto。
- [ ] T-S2 [P] `MemoryEntryRepository`（storage `com.oryxos.storage.repository`）：`findByScopeOrderByIdAsc(String)`、`findByScopeOrderByIdDesc(String, Pageable)`、`findByScopeAndContentContainingOrderByIdAsc(String, String)` 三个派生查询。
- [ ] T-S3 [P] `001-init.sql` 追加 `memory_entries`（storage `src/main/resources/scripts/sql`）：`id INTEGER PRIMARY KEY AUTOINCREMENT`、`scope VARCHAR(16) NOT NULL`、`content TEXT NOT NULL`、`created_at TEXT NOT NULL`；`CREATE INDEX IF NOT EXISTS idx_memory_scope ON memory_entries (scope)`；注释口径对齐 sessions/审计表。

## Phase 3: memory 模块依赖（共享底座）

- [ ] T-M0 `oryxos-memory/pom.xml`：main 增 `com.oryxos:oryxos-storage`、`org.springframework:spring-web`、`org.springframework.ai:spring-ai-model`（显式化既有锁定链工件）；test 增 `spring-boot-starter-test`、`com.squareup.okhttp3:mockwebserver:4.12.0`（19/20 节先例显式锁）。加完跑 `mvn -pl oryxos-memory -am dependency:tree` 核对无 convergence 冲突。

## Phase 4: 后端接口 + Markdown 档（US2）

- [ ] T-M1 `LongTermMemoryStore`（memory `com.oryxos.memory.store`）：三方法 `append`/`load`/`recallByKeyword`；接口中立（不出现 md/sqlite/mem0 字样）；javadoc 四条契约（不缓存/核心不截断/scope 显式/recall 只搜归档）。
- [ ] T-M2 `MarkdownMemoryStore`（memory `com.oryxos.memory.store`）：构造 `Path memoryFile`；`## 核心记忆`/`## 归档记忆` 两区块；条目 `- [yyyy-MM-dd] content`；`MAX_ARCHIVE_CHARS=4000` 只裁归档段尾部；缺文件自建骨架（含 `（暂无）` 占位）、`append` 删除目标区占位；`load` 每次 `Files.readString` 不缓存；`recallByKeyword` 只扫归档行。
- [ ] T-M2T `MarkdownMemoryStoreTest`（memory test）：①首次 append 自建两区块 + 日期前缀；②归档超 4000 字符只裁归档、核心完整；③recall 只搜归档（核心命中不返回）；④写后立读。

## Phase 5: SQLite 档（US3）

- [ ] T-M3 `SqliteMemoryStore`（memory `com.oryxos.memory.store`）：构造注入 `MemoryEntryRepository`；`MAX_ARCHIVE_ROWS=100`；核心全量、归档最近 100（`PageRequest.of(0,100)` + 反转渲染）；检索 `Containing`；每次查库不缓存。
- [ ] T-M3T `SqliteMemoryStoreTest`（memory test，`@SpringBootTest(classes=MemoryTestApplication.class)` + 内存 SQLite + 真实 `001-init.sql`）：①手工脚本建表、核心/归档两行能存能读；②150 条归档只返回最近 100；③`LIKE` 只命中归档、核心不参与；④每测 `repository.deleteAll()` 清态。

## Phase 6: Mem0 档（US4，外部集成）

- [ ] T-M4 `Mem0MemoryStore`（memory `com.oryxos.memory.store`）：构造 `(RestClient, String baseUrl, String userId, String apiKey)`（提供无 apiKey 重载）；`append` → `POST /v1/memories/`（messages/user_id/metadata.scope，可选 `Authorization: Bearer`）；`load` → `GET /v1/memories/?user_id=` 解析 `results[].memory`（兼容 `content`）与 `metadata.scope`，核心全量、归档最近 100；`recall` → `POST /v1/memories/search/`；异常上抛不吞；`baseUrl` 去尾斜杠。
- [ ] T-M4H `FakeMem0Server`（memory test helper，`com.oryxos.memory.store` test-only）：MockWebServer `Dispatcher` 内存实现 add/get_all/search；支持 `reset()`/`url()`/`close()`；不碰真 server。
- [ ] T-M4T `Mem0MemoryStoreTest`（memory test）：①`append` 请求体含 content/user_id/metadata.scope；②`load` 按 scope 分组、归档截断最近 100；③`recall` 转发 search 并返回命中；④HTTP 错误上抛（假服务 500）。

## Phase 7: 参数化契约测试（US1 核心，harness 最值钱）

- [ ] T-M5 `MemoryStoreContractTest`（memory test）：`@SpringBootTest(classes=MemoryTestApplication.class)` + `@TestInstance(PER_CLASS)` + `@MethodSource("allStores")` 同时产出 Markdown（`@TempDir` 唯一文件）/SQLite（注入仓储）/Mem0（真实 `Mem0MemoryStore` + `FakeMem0Server`）；每参数前清库/重置假服务。断言：①`appendIsVisibleOnNextRead_noCache`（契约一）；②`truncationOnlyKeepsArchiveTail_coreMemoryIntact`（核心一条 + 200 条归档，核心完整、最早被裁、最近保留；契约二）；③`scopeRoutesToCorrectSection`（CORE/ARCHIVAL 路由 + recall 只搜归档；契约三/四）；④recall 未命中空集不抛。
- [ ] T-M5A `MemoryTestApplication` + `src/test/resources/application.yaml`（memory test）：`@SpringBootApplication(scanBasePackages={"com.oryxos.memory","com.oryxos.storage"})` + `@EntityScan("com.oryxos.storage.entity")` + `@EnableJpaRepositories("com.oryxos.storage.repository")`；SQLite 内存库 + `spring.sql.init` 执行 `classpath:scripts/sql/001-init.sql`；必要时 `spring.datasource.hikari.maximum-pool-size: 1`（仅测试，R-sqlite）。

## Phase 8: 门面实现 + MemoryTools（US1/US5）

- [ ] T-M6 `MemoryServiceImpl`（memory `com.oryxos.memory`）：实现门面——`buildContext` 返回 `store.load()`（忽略 Session 参数，javadoc 说明）；`remember` 对 null scope 兜底 ARCHIVAL 后转发；`recall` 转发；无缓存字段。
- [ ] T-M6T `MemoryServiceImplTest`（memory test）：fake store——`buildContext` 返回核心全量+归档截断后的组合、不重复会话历史；remember/recall 转发；null scope 兜底 ARCHIVAL。
- [ ] T-M7 `MemoryTools`（memory `com.oryxos.memory.tool`）：`@Tool(name="save_memory")`（content + scope，`null/空白→ARCHIVAL`、`trim().toUpperCase(Locale.ROOT)`、非法抛 `IllegalArgumentException`，返回「已记住」）；`@Tool(name="recall_memory")`（未命中返回「没有找到相关记忆」，命中按行拼接）；只依赖 `MemoryService`。
- [ ] T-M7T `MemoryToolsTest`（memory test）：①scope 缺省/空白写归档；②`core`/`CORE` 映射 CORE；③未命中固定文案不抛；④命中按行拼接；⑤非法 scope 抛 `IllegalArgumentException`。

## Phase 9: 装配与集成（US5）

- [ ] T-W1 `MemoryConfiguration`（memory `com.oryxos.memory`）：`@Bean LongTermMemoryStore` 按 `${memory.backend:markdown}` switch（markdown/sqlite/mem0；未知值抛 `IllegalArgumentException`）；sqlite 经 `ObjectProvider<MemoryEntryRepository>` 懒取；mem0 经 `@Value("${MEM0_BASE_URL:http://localhost:8888}")`/`${MEM0_USER_ID:oryxos}`/`${MEM0_API_KEY:}` + `RestClient.create()`；`@Bean MemoryService`；`@Bean MemoryTools`。
- [ ] T-W2 `ToolRegistryConfiguration`（tool `com.oryxos.tool.registry`）：`toolRegistry(...)` 追加 `MemoryTools` 参数并纳入 `registerAnnotated(...)`；`oryxos-tool/pom.xml` 增 `com.oryxos:oryxos-memory`（R4）；无环。
- [ ] T-W3 `InitCommand.MEMORY_MD`（cli）：header 改为 `## 核心记忆` / `## 归档记忆` + `（暂无）` 占位（R3）。
- [ ] T-W4 `application.yaml`（boot）：增 `memory.backend: ${MEMORY_BACKEND:markdown}` 与注释（`markdown | sqlite | mem0`）；Mem0 三环境变量不写 yaml。
- [ ] T-W5 `OryxToolContractTest`（tool test）：`buildFullRegistry()` 追加 `MemoryTools`（mock `MemoryService`）；期望工具清单补 `save_memory`/`recall_memory`，类名/DisplayName 的「12 个」改为「14 个」。
- [ ] T-W6 装配冒烟（boot test）：`OryxOsApplicationTests.contextLoads` 在 Memory 装配后仍绿（`memory.backend=markdown`、不触发 DB 读写）；CLI 重命令上下文（`OryxOsCliTest` 既有覆盖）不因 tool→memory 依赖回归。

## Phase 10: 收尾与六项证据 DoD

- [ ] T-Z1 `mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD），保存关键输出（surefire 汇总/各模块 BUILD SUCCESS）。
- [ ] T-Z2 课件 harness 映射表逐类核对存在且非空：`MemoryStoreContractTest`/`MarkdownMemoryStoreTest`/`SqliteMemoryStoreTest`/`Mem0MemoryStoreTest`/`MemoryToolsTest`/`MemoryServiceImplTest`；5 个关键回归测试逐个对号（英文方法名 + `@DisplayName` 原文）。
- [ ] T-Z3 「本节交付物」逐项 ls/grep 存在性核对：门面/枚举/后端接口/三档实现/`MemoryTools`/`memory_entries` 表/`MEMORY.md`/`memory.backend`/`PromptBuilder` 集成点。
- [ ] T-Z4 前序节全部测试回归绿（16~20 节跨节契约证据）。
- [ ] T-Z5 H4 六条全局不变量自查：①涉外 IO 首行过 `Sandbox.enforce`（本节 Memory 的文件/HTTP 是否属于"工具涉外 IO"？——`MarkdownMemoryStore` 写工作区记忆文件、`Mem0MemoryStore` 调外部 API，按 22 节设计属于 Memory 后端而非 Tool 调用；在 plan/报告注明「24 节 Sandbox 接线只约束 Tool 工具，Memory 后端是受信装配件」并保持不绕过 `ToolExecutor` 审计路径）；②LLM/工具审计路径零改动；③grep 无明文 key（Mem0 全 env 占位）；④`session_id` 只在 `SessionManager` 内拼接（本节不碰）；⑤无 Reactor/`CompletableFuture`/自建线程池；⑥无 Spring AI 自动工具执行路径（`@Tool` 仅经 `registerAnnotated` 生成 schema）。
- [ ] T-Z6 验收报告：六项证据 + 课件「五、做完怎么验」剩余人工项（三档切换、Mem0 真连、真模型链路、USER.md 只读 review、旧 header 迁移）；**变更总结**（改动点/重点 review 清单/如何验证）按 `git status --short` / `git diff --stat` 实测，输出在对话里。
- [ ] T-Z7 工单交付：commit 作者 `Super-Lee093`；从工单分支推 PR（实际 URL 绑定工单）；PR 描述含改动说明与检查结果；不 merge/不 approval（按工单 Delivery requirements）。
