# Implementation Plan: 第 22 节 Memory（统一门面 + 三档后端 + MemoryTools）

**Branch**: `agent/run-learn-dev-22-6daeab96c1f9` | **Date**: 2026-11-04 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/022-lesson22-memory/spec.md`；课件 `docs/class/第22节：Memory 实现与代码讲解.md`；前序设计 `docs/class/第21节：Memory 原理解析、业界方案与 OryxOS 设计评审.md`；技术方案对位 §5（Memory）、§9.2（SQLite 手工建表）、§10（模块职责）。

## Summary

交付 Memory 核心能力三：**`MemoryService` 门面**（core 既有最小接口补 `remember`/`recall`，`buildContext` 只返回长期记忆）+ **`LongTermMemoryStore` 可插拔后端接口** + **三档实现一次交付**（`MarkdownMemoryStore` 默认、`SqliteMemoryStore` 结构化升级、`Mem0MemoryStore` 自托管外部集成）+ **`MemoryTools`**（`save_memory`/`recall_memory`，经 20 节注解管道注册）+ **装配接线**（`memory.backend` 选择后端、`PromptBuilder` 注入门面、`ToolRegistryConfiguration` 注册 Tools、18 节 init 模板 header 对齐）。四条行为契约由**参数化契约测试对三档统一跑**：不缓存、核心区永不截断、scope 显式路由、recall 只搜归档区。上层（`PromptBuilder`/`ReActLoop`/`ToolExecutor`）换后端零改动。

## Technical Context

**固定技术栈句**：JDK 21 + Spring Boot 3.x + Spring AI Alibaba（动手前先跑 `mvn dependency:tree` 确认锁定 BOM 里目标依赖存在）、SQLite + Spring Data JPA。凭证走环境变量占位，不落明文。SQLite 用手工建表脚本，不依赖 `hibernate.ddl-auto=update`。

- **Language/Version**: Java 21；全同步阻塞，零 Reactor/`CompletableFuture`/自建线程池（H4⑤）。
- **Primary Dependencies**（本地锁已核）:
  - `spring-web` 6.2.11（Boot BOM）：`org.springframework.web.client.RestClient`——Mem0 同步 HTTP 客户端（19 节已在链；memory 模块显式声明）。
  - `spring-data-jpa` / `sqlite-jdbc` / `hibernate-community-dialects`（storage 既有依赖，memory 经 `oryxos-storage` 传递）：`JpaRepository`、`Pageable`、SQLite 方言。
  - `spring-ai-model` 1.1.8（core 传递，memory 显式化）：`@Tool`/`@ToolParam` 注解，`MethodToolCallbackProvider` 生成 schema（20 节 `registerAnnotated` 路径）。
  - 测试：JUnit 5 / Mockito / AssertJ / `spring-boot-starter-test`；`com.squareup.okhttp3:mockwebserver` **4.12.0**（19/20 节先例，显式锁版本，Boot 3.5 BOM 已不管）用于本地假 Mem0 HTTP 服务。
  - **H3 说明**：Mem0 是外部服务，Java 侧只能核实到 `RestClient` 与 JSON 层；REST 端点按课件（`POST /v1/memories/`、`GET /v1/memories/`、`POST /v1/memories/search/`）实现，真 server 连通属人工项（spec R5）。不新增第三方依赖家族。
- **Storage**: SQLite `memory_entries` 表（手工脚本 `001-init.sql` 追加）；实体/仓储落 `oryxos-storage`（技术方案 §10）；`created_at` 沿用项目口径 ISO-8601 TEXT，实体层写入，不依赖 DB 默认值。
- **Testing**: 单测默认跑、无 `@Tag("integration")`（假 Mem0/内存 SQLite/`@TempDir`）；实现完成的定义是 `mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD）。
- **Target Platform**: 企业内网单实例；9 模块结构不变。
- **语法禁区句**：避开 P3C/ASM 解析不了的 Java 18+ 语法形态（如增强 switch 的 `default ->` 写法），静态检查是构建门禁。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design. 原则来源 `.specify/memory/constitution.md`。*

| # | 原则 | 判定 | 证据/说明 |
|---|------|------|-----------|
| I | 固定技术栈与九模块结构 | ✅ | 只在既有 core/memory/tool/storage/cli/boot 内改动，不增删模块；SQLite 仍走 Spring Data JPA（实体/仓储 storage 落位）。 |
| II | 核心阶段只做运行时内核 | ✅ | 不做自动抽取、向量库、情景记忆、Memory Wiki、记忆压缩、知识图谱（spec 边界；技术方案 §5.5）。 |
| III | 自实现 ReAct 循环 | ✅ | 循环/执行器零触碰；Memory 只作为 Prompt 的长期记忆段输入。 |
| IV | Spring AI 只用一半 | ✅ | 只用 `@Tool`/`@ToolParam` 注解与 schema 生成（20 节 `registerAnnotated`）；Tool 执行仍唯一收口 `ToolExecutor`，无自动 tool 执行。 |
| V | Plugin Tool 三档接入·零代码优先 | ✅ | `MemoryTools` 是内置 Tool，走 20 节注解注册管道；不新增接入机制。 |
| VI | 审计数据 Day One 落库 | ✅ | 工具执行成败走 17 节 `ToolExecutor` 既有 `tool_invocations` 审计路径；Memory 自身无新审计表需求（记忆内容不是审计事件）。**门面收窄说明**：原则 VI 要求 `MemoryService` 是三层统一门面、内部委托 `SessionManager` + 长期记忆，禁止把 Memory 与 Session 合并。本节按 22 节 harness 的验收条（`buildContext` 只返回长期记忆、会话历史由 `PromptBuilder` 独立负责）实现——门面仍是独立层、不与 Session 合并，只是 `buildContext` 不重复输出会话历史；会话记忆仍由 `SessionManager` 持有、`PromptBuilder` 消费，未违反「不合并」的本意。该收窄在 spec Q2 已记录。 |
| VII | 每个 User Story 必须可演示 | ✅ | `save_memory` 写入后下一轮 `buildContext` 带上；`memory.backend` 切 markdown/sqlite 同体感；harness + `mvn clean verify` 全绿。 |

## 明确不做（边界）

- **不做自动抽取/自动提炼**——写入时机完全由 Agent 主动调 `save_memory`（第 21 节第十二节；技术方案 §5.5）。
- **不引向量库/自建向量层**——语义检索只在 Mem0 档由外部服务承担。
- **不做情景记忆、Memory Wiki、记忆压缩、知识图谱后端**——全部扩展阶段。
- **不写 `USER.md`**——`USER.md` 全程只读（技术方案 §5.4）。
- **不改 `PromptBuilder`/`SessionManager`/`Session` 行为**——只改 `EngineConfiguration` 的注入参数；会话历史仍由 PromptBuilder 独立负责（spec Q2）。
- **不在 CI 连真 Mem0 server**——本地假服务验证协议；真连通人工项。
- **不做旧 `MEMORY.md` header 自动迁移**——只更新 init 模板，旧工作区人工调整（spec 人工项）。

## Project Structure（模块落位 + 文件级清单）

**模块落位裁定**（skill 落位表「22 全部→oryxos-memory」与依赖方向冲突处的处理，spec Q3/Q4/R2）：
- `MemoryService`、`MemoryScope` → **oryxos-core**（`PromptBuilder` 只认 core 接口，core 不能反向依赖 memory；接口是 17 节既有最小形态的演进，javadoc 点名）。
- `LongTermMemoryStore`、`MemoryServiceImpl`、三档实现、`MemoryTools`、`MemoryConfiguration` → **oryxos-memory**。
- `MemoryEntryEntity`、`MemoryEntryRepository` → **oryxos-storage**（技术方案 §10 持久化层 + 既有 Session 先例）；`memory_entries` DDL → storage 的 `001-init.sql`。
- 装配接线：`EngineConfiguration`（core）、`ToolRegistryConfiguration`（tool，追加 `MemoryTools`）、`InitCommand`（cli，header 模板）、`application.yaml`（boot，`memory.backend`）。

| 组件 | 模块/包 | 说明 |
|---|---|---|
| `MemoryService` 演进 | core `com.oryxos.core.memory` | 既有 `buildContext(Session)` 保留；增 `remember(String, MemoryScope)`、`recall(String)`。javadoc 说明 `buildContext` 只返回长期记忆、会话历史由 PromptBuilder 独立负责；`remember` 对 null scope 缺省 ARCHIVAL（与 Tool 层一致）。 |
| `MemoryScope` | core `com.oryxos.core.memory` | `enum { CORE, ARCHIVAL }`；javadoc 契约三（Agent 显式指定，系统不猜）。 |
| `LongTermMemoryStore` | memory `com.oryxos.memory.store` | `void append(String, MemoryScope)` / `String load()` / `List<String> recallByKeyword(String)`；接口中立，不出现 md/sqlite/mem0 字样；javadoc 四条契约。 |
| `MemoryServiceImpl` | memory `com.oryxos.memory` | 实现门面：`buildContext` → `store.load()`（忽略 Session 参数，javadoc 说明）；`remember`/`recall` 转发；不做缓存。 |
| `MarkdownMemoryStore` | memory `com.oryxos.memory.store` | 文件 `.oryxos/memory/MEMORY.md`；`## 核心记忆` / `## 归档记忆`；条目 `- [yyyy-MM-dd] content`；`MAX_ARCHIVE_CHARS=4000`，只裁归档段尾部；缺文件自建骨架；`append` 删除目标区 `（暂无）` 占位；`recall` 只扫归档行。 |
| `SqliteMemoryStore` | memory `com.oryxos.memory.store` | 构造注入 `MemoryEntryRepository`；`MAX_ARCHIVE_ROWS=100`；核心 `findByScopeOrderByIdAsc("CORE")` 全量、归档 `findByScopeOrderByIdDesc("ARCHIVAL", PageRequest.of(0,100))` 反转后渲染；检索 `findByScopeAndContentContaining...`；每次直查不缓存。 |
| `Mem0MemoryStore` | memory `com.oryxos.memory.store` | 构造 `(RestClient, baseUrl, userId, apiKey)`；`append` → `POST {base}/v1/memories/`（messages/user_id/metadata.scope，可选 Bearer）；`load` → `GET {base}/v1/memories/?user_id=` 解析 `results[].memory/metadata.scope`，核心全量、归档取最近 100；`recall` → `POST {base}/v1/memories/search/`；异常上抛不吞；`baseUrl` 去尾斜杠。 |
| `MemoryTools` | memory `com.oryxos.memory.tool` | `@Tool(name="save_memory")` / `@Tool(name="recall_memory")`；scope 解析 `null/空白→ARCHIVAL`、`trim().toUpperCase(Locale.ROOT)`、非法抛 `IllegalArgumentException`；未命中返回「没有找到相关记忆」；只依赖 `MemoryService`。 |
| `MemoryConfiguration` | memory `com.oryxos.memory` | `@Configuration`：`@Bean LongTermMemoryStore` 按 `${memory.backend:markdown}` switch（markdown/sqlite/mem0，未知值抛 `IllegalArgumentException`）；`@Bean MemoryService`；`@Bean MemoryTools`；sqlite 分支经 `ObjectProvider<MemoryEntryRepository>` 懒取；mem0 分支经 `@Value("${MEM0_BASE_URL:http://localhost:8888}")` 等环境变量 + `RestClient.create()`。 |
| `MemoryEntryEntity` | storage `com.oryxos.storage.entity` | `@Entity @Table(name="memory_entries")`；`Long id` `@GeneratedValue(IDENTITY)`、`String scope`（CORE/ARCHIVAL）、`String content`、`String createdAt`（ISO-8601，实体层写入）；无参构造 + getter/setter。 |
| `MemoryEntryRepository` | storage `com.oryxos.storage.repository` | 三个派生查询：`findByScopeOrderByIdAsc`、`findByScopeOrderByIdDesc(String, Pageable)`、`findByScopeAndContentContainingOrderByIdAsc`。 |
| `001-init.sql` 追加 | storage `src/main/resources/scripts/sql` | `CREATE TABLE IF NOT EXISTS memory_entries (...)` + `idx_memory_scope`；列名/类型按 spec FR6，`created_at TEXT NOT NULL` 沿用项目口径。 |
| `EngineConfiguration` | core `com.oryxos.core.agent` | `promptBuilder(ContextLoader, ToolRegistry, MemoryService)`——删掉 `null` 过渡与「22 节替换」注释；其余 Bean 零改动。 |
| `ToolRegistryConfiguration` | tool `com.oryxos.tool.registry` | `toolRegistry(...)` 追加 `MemoryTools` 参数并 `registerAnnotated(...)`；装配仍是显式列表，不做容器魔法扫描。 |
| `oryxos-tool/pom.xml` | tool | 新增 `com.oryxos:oryxos-memory` 编译依赖（20 节「22 节交付后注册」的兑现）；无环。 |
| `oryxos-memory/pom.xml` | memory | 新增 `oryxos-storage`、`spring-web`、`spring-ai-model`（显式化）；test：`spring-boot-starter-test`、`mockwebserver 4.12.0`。 |
| `InitCommand.MEMORY_MD` | cli `com.oryxos.cli.command` | header 改为 `## 核心记忆` / `## 归档记忆`（spec R3；用户工作区不自动迁移）。 |
| `application.yaml` | boot `src/main/resources` | 增 `memory.backend: ${MEMORY_BACKEND:markdown}` 及注释（`markdown | sqlite | mem0`）；Mem0 三个环境变量不写 yaml、由 `@Value` 直读。 |
| `OryxToolContractTest` | tool test | `buildFullRegistry()` 追加 `MemoryTools`；期望工具名清单补 `save_memory`/`recall_memory`；契约参数化自动覆盖。 |
| `PromptBuilderTest` | core test | `MemoryService` lambda（单方法）改为显式三方法 stub，保持原断言。 |

## 关键设计决定（implement 期逐字执行的对齐锚）

1. **门面只做长期记忆**：`buildContext` 返回 `LongTermMemoryStore.load()`，不拼会话历史（spec Q2；harness `MemoryServiceImplTest` 条）。`PromptBuilder` 仍按 17 节四段顺序：system → 长期记忆 → 会话历史 → 工具。
2. **契约测试参数化三档**：`MemoryStoreContractTest` 用 `@MethodSource("allStores")` 同时产出 Markdown / SQLite / Mem0（真实 `Mem0MemoryStore` 指向本地假 Mem0 服务）；`@TestInstance(PER_CLASS)` 以注入仓储与 `@TempDir`；每个参数生成独立临时文件/清库/重置假服务，避免状态串扰。断言逐条对齐课件：写后立读、核心一字不少、归档最早被裁、scope 路由、recall 只搜归档。
3. **不缓存**：Markdown 每次 `Files.readString`；SQLite 每次查库；Mem0 每次 HTTP；`MemoryServiceImpl` 不持缓存字段。
4. **核心区不截断**：Markdown `truncateIfNeeded` 只接收归档段字符串；SQLite `LIMIT` 只加在归档查询；Mem0 归档只取最近 `MAX_ARCHIVE_ROWS` 再客户端裁。核心区永远全量。
5. **scope 路由**：`MemoryScope` 只二值；`MemoryTools` 负责字符串解析与缺省；`MemoryServiceImpl.remember` 对 null 再兜底 ARCHIVAL。
6. **关键词检索**：Markdown `line.contains`；SQLite `Containing`（`LIKE %kw%`）；Mem0 search（语义加强版）；均只作用归档区，不做正则/分词。
7. **SQLite 表与实体**：表 DDL 手工脚本，`created_at TEXT`（ISO-8601，实体层写入，与 `llm_calls`/`sessions` 同口径）；`scope VARCHAR(16) NOT NULL`；`id INTEGER PRIMARY KEY AUTOINCREMENT`；索引 `idx_memory_scope`。`MemoryEntryEntity` 不实现 `Serializable`、不建关联。
8. **Mem0 协议**：只用课件点名的三个端点；`metadata.scope` 作为分区；`results[].memory` 作为内容（兼容 `content` 字段）；`page_size` 不依赖服务端分页——客户端取回后截最近 100，保证三档契约一致；可选 `Authorization: Bearer ${MEM0_API_KEY}`。
9. **装配顺序**：`MemoryConfiguration` 产 `LongTermMemoryStore` → `MemoryService` → `MemoryTools`；`ToolRegistryConfiguration` 取 `MemoryTools` 注册；`EngineConfiguration` 取 `MemoryService` 注入 `PromptBuilder`。sqlite 仓储用 `ObjectProvider` 懒取，markdown 档不触发 DB。
10. **测试方法命名**：英文驼峰/snake_case，课件原文进 `@DisplayName`（宪法 §9）；假 Mem0 服务是 test-only helper，不产生产品概念。

## Testing strategy（按课件「验收 harness」执行）

测试策略句：测试策略按课件「验收 harness」执行：`MemoryStoreContractTest`（参数化三档）、`MarkdownMemoryStoreTest`、`SqliteMemoryStoreTest`、`Mem0MemoryStoreTest`、`MemoryToolsTest`、`MemoryServiceImplTest`（覆盖四条契约、scope 缺省/未命中、buildContext 只含长期记忆），单测默认跑、集成冒烟打 `@Tag("integration")` CI 跳过；实现完成的定义是 `mvn clean verify` 全绿。

**harness 映射表**（测试类 ↔ 覆盖点 ↔ 模块）：

| 测试类 | 覆盖的验收点 | 模块 | 跑法 |
|---|---|---|---|
| `MemoryStoreContractTest` | **参数化遍历三档 `LongTermMemoryStore`**：写后立读（契约一）、截断只裁归档核心区一字不少（契约二）、scope 路由（契约三）、recall 只搜归档（契约四）；Mem0 用本地假 Mem0 HTTP 服务 | memory（test） | 单测默认跑 |
| `MarkdownMemoryStoreTest` | 区块解析/自建、4000 字符截断边界、recall 只搜归档、写后立读 | memory | 单测默认跑 |
| `SqliteMemoryStoreTest` | 手工建表脚本能建能读、归档 `LIMIT 100` 生效、`LIKE` 检索、核心区全量 | memory（+storage 仓储） | 单测默认跑（`@SpringBootTest` 最小上下文） |
| `Mem0MemoryStoreTest` | 假 Mem0：`append` 请求体带 scope/user_id、`load` 按 scope 分组、`recall` 转发 search、归档截断、异常上抛 | memory | 单测默认跑 |
| `MemoryToolsTest` | scope 缺省写归档、`core` 映射 CORE、关键词未命中返回「没有找到相关记忆」、命中按行拼接 | memory | 单测默认跑 |
| `MemoryServiceImplTest` | `buildContext` 返回长期记忆（核心全量 + 归档截断后），不重复会话历史；remember/recall 转发 | memory | 单测默认跑 |
| `OryxToolContractTest` | 两个新 Tool 自动纳入契约三件套；注册表期望清单补齐 | tool | 单测默认跑（既有类扩展） |
| `PromptBuilderTest` | 注入门面后第二段仍是长期记忆、四段顺序不变 | core | 单测默认跑（既有类改 stub） |

**课件关键回归测试（写出代码的，断言逻辑逐条保真落地，方法名译英文 + `@DisplayName` 承载课件原文）**：

1. `truncationOnlyKeepsArchiveTail_coreMemoryIntact`（@DisplayName「截断只裁归档区 核心记忆一字不能少」）——核心一条 + 200 条归档流水；`load` 含核心原文、不含最早一条、含最近一条；三档都过。
2. `appendIsVisibleOnNextRead_noCache`（@DisplayName「写入后立刻可读 不允许有缓存」）——`append` 后 `load` 含新内容、`recallByKeyword` 立即命中；三档都过。
3. `scopeRoutesToCorrectSection`（@DisplayName「写核心还是写归档 由 scope 显式指定」）——CORE/ARCHIVAL 分别落对区块，recall 不返回核心区内容。
4. `missReturnsMessageNotException`（@DisplayName「关键词未命中返回『没有找到相关记忆』而不抛异常」）——`MemoryTools.recallMemory` 未命中返回固定文案。
5. `scopeDefaultsToArchival`（@DisplayName「scope 缺省写归档」）——`saveMemory(content, null)` / `""` 都走 ARCHIVAL。

> 课件 harness 的方法名是中文（如「截断只裁归档区_核心记忆一字不能少」），按 skill 规则译成语义等价英文并保留 `@DisplayName` 原文。Mem0 契约参数使用真实 `Mem0MemoryStore` + 本地假 Mem0 服务（`MockWebServer` Dispatcher），既满足「内存假 Mem0」又不把真 REST 交互漏出测试。

## Risks & Mitigations

- **R-facade（`MemoryService` 接口扩展的编译影响）**：唯一前序接口改动（17 节 javadoc 点名）；`PromptBuilderTest` lambda 改显式 stub；16~20 节全量测试回归绿兜底。`buildContext` 签名不变、`PromptBuilder` 行为不变。
- **R-module（tool → memory 依赖）**：`ToolRegistryConfiguration` 显式注册 `MemoryTools` 需要 tool 编译依赖 memory；无环（memory 不依赖 tool）。若不接受，替代方案是改 `SpringLauncher` 扫描 boot 装配层，plan 不采用（改动面更大）。
- **R-sqlite（`:memory:` 多连接）**：内存 SQLite 每个连接一个库；测试沿用 storage 既有 `jdbc:sqlite::memory:` + `spring.sql.init` 模式；若出现表不存在，测试配置加 `spring.datasource.hikari.maximum-pool-size: 1`（仅测试配置，非产品配置键）。
- **R-mem0（外部 API 不可本地核实）**：按课件三个端点实现；测试用假服务验证协议与契约；真 server 连接、分页/字段差异列人工项；`RestClient` 异常不吞，走调用方审计。
- **R-init（旧工作区 header）**：只改 init 模板；`MarkdownMemoryStore` 只认 canonical header，旧文件不自动迁移（避免覆盖用户内容），人工项注明。
- **R-static（P3C/SpotBugs）**：测试方法名英文 + `@DisplayName`；无 `System.out`（用 SLF4J 或不打日志）；`Map.of` 不含 null；实体 getter/setter 常规；`Locale.ROOT` 大小写；异常不空 catch；无 Java 18+ 解析禁区。
- **R-registration（注册顺序/重名）**：`MemoryTools` 与 20 节工具一起在 `ToolRegistryConfiguration` 显式注册；重名由 `DefaultToolRegistry` 拒绝；`save_memory`/`recall_memory` 名字与 20 节「12 工具」不冲突。

## Pre-Implement Verification Gate（H3）

**已验证（2026-11-04，本 plan 期）**：
- `spring-web` 6.2.11（Boot 3.5.16 BOM）在锁定链，`RestClient` 为同步阻塞客户端；`spring-ai-model` 1.1.8 提供 `@Tool`/`@ToolParam`；`JpaRepository`/`Pageable` 由 storage 既有 `spring-data-jpa` 提供；`mockwebserver` 4.12.0 已在 19/20 节测试锁版本（显式锁先例）。
- `oryxos-memory` 当前仅依赖 core；新增 storage/spring-web/spring-ai-model 均为既有锁定链内工件，非新第三方家族。
- 基线 `mvn clean verify` 在 implement 第一个动作跑；红了当场报告，不前进。

**implement 期逐任务再核**：`RestClient.post()/get()/uri()/body()/retrieve().toBodilessEntity()` 的 6.2.x 签名；`PageRequest.of` / Spring Data 派生查询 `Containing` 语义；SQLite `AUTOINCREMENT` + JPA `IDENTITY` 在本项目方言下可用（storage 既有 `LlmCallEntity` 先例）；MockWebServer `Dispatcher` API。核实不到 → 软门禁停下问用户。

## 停点与交付

- **Step 5 固定停点**：tasks 与课件「本节交付物」逐项比对（代码/测试/配置/表/文件/集成点），列出齐/缺/多；重点确认 spec R1~R6（接口演进、清单外支撑类型、init 模板、tool→memory 依赖、Mem0 手工项、基线门禁）。
- 用户确认后 implement；每任务实现+测试成对落地，跑模块测试；最后 `mvn clean verify` 全绿 + 六项证据 DoD + 变更总结。按工单要求 commit 作者 `Super-Lee093`，PR 从工单分支开；skill 的「不自动 commit/push」与工单交付要求冲突处以工单交付要求为准（PR 由用户 review/merge）。
