# Feature Specification: 第 22 节 Memory（统一门面 + 三档长期记忆后端 + MemoryTools）

**Feature Branch**: `agent/run-learn-dev-22-6daeab96c1f9`（部署工单分支；课程 feature 目录名沿用 `022-lesson22-memory`）

**Created**: 2026-11-04

**Status**: Draft

**Input**: 课件 `docs/class/第22节：Memory 实现与代码讲解.md`——本节是 OryxOS 核心能力三（Memory）的实现课：一个 `MemoryService` 门面 + 可插拔的 `LongTermMemoryStore` 后端接口 + 一次交付三档实现（Markdown / SQLite / Mem0）+ 两个内置 Tool（`save_memory` / `recall_memory`）；技术方案对位 `TechnicalSolution` §5（Memory 三层记忆）、§9.2（SQLite 手工建表）、§10（模块职责）。设计结论以第 21 节评审（`docs/class/第21节：Memory 原理解析、业界方案与 OryxOS 设计评审.md`）为前序依据。

**Source-of-truth note**: 延续 16~20 节裁定的逐节开发形态——**课件为唯一事实源**，实现形态逐字对齐课件骨架（`MemoryService` 三方法、`LongTermMemoryStore` 三方法、`MemoryScope` 二值、`MEMORY.md` 的 `## 核心记忆` / `## 归档记忆` 两区块、harness 断言逻辑如「截断只裁归档区，核心记忆一字不能少」）。与课件示意代码的既有差异按前序节裁定继续沿用（包根 `com.oryxos` 非 `io.oryxos`；`MemoryService` 为第 17 节已在 core 前向交付的最小接口——其 javadoc 明文点名 `remember`/`recall` 与实现归第 22 节补齐，属交付物既定改造点；`Session`/`SessionManager` 沿用第 18 节形态）。

---

## Clarifications

### Session 2026-11-04（逐条采自课件「一、二、三、四、五」+ 第 21 节评审 + 技术方案 §5/§9.2/§10，无外部答案外推）

- **Q1: `MemoryService` 本节是新造还是演进？** → A（17 节交付 javadoc）：17 节已交付 `buildContext(Session)` 最小接口，并写明「`remember`/`recall`（供 `MemoryTools` 用）与实现归第 22 节在 `oryxos-memory` 模块补齐」。本节在既有接口上补两方法、补实现，不另造门面、不改 `buildContext` 签名。
- **Q2: `buildContext` 到底拼不拼会话历史？** → A（课件「验收 harness」`MemoryServiceImplTest` 条 + 17 节 `PromptBuilder` 既有实现）：**不拼**。会话历史由 `PromptBuilder` 的会话历史段独立负责（17 节已落地）；`buildContext` 只返回长期记忆（核心区全量 + 归档区截断后），两段一起进 system prompt。若 `buildContext` 再拼一次历史会造成重复注入，故以 harness 条与既有消费端为准（课件「第一步」的示意叙述按此收窄）。
- **Q3: 门面与后端接口分别放哪？** → A（技术方案 §5.1/§10 + 模块依赖方向）：`MemoryService` 与 `MemoryScope` 留在 **oryxos-core**（`PromptBuilder` 只认 core 接口，core 不能反向依赖 memory 模块）；`LongTermMemoryStore`、`MemoryServiceImpl`、三档实现、`MemoryTools` 落 **oryxos-memory**。换后端只换注入实现，上层零改动。
- **Q4: `memory_entries` 的实体/仓储放哪？** → A（技术方案 §10「oryxos-storage 持久化层」+ 既有 `SessionEntity`/`SessionRepository` 先例）：表 DDL 追加进 `oryxos-storage/src/main/resources/scripts/sql/001-init.sql`；`MemoryEntryEntity` + `MemoryEntryRepository` 落 oryxos-storage，`SqliteMemoryStore` 在 oryxos-memory 使用该仓储（memory → storage 单向依赖）。这两个支撑类型是「本节交付物」清单未点名的实现件，属 Step 5 停点需用户知悉项（见 Resolve R2）。
- **Q5: 三档后端的定位与默认值？** → A（课件「二」第二）：Markdown 是默认档（零依赖、人可读、git 可跟踪）；SQLite 是记忆量变大后的结构化升级（复用已有 SQLite、零外部依赖）；Mem0 是「真需要自动抽取/语义检索」时的自托管外部集成档。`application.yaml` 的 `memory.backend` 决定装配哪一个，默认 `markdown`。
- **Q6: 四条行为契约如何落地？** → A（课件「二」第三 + 技术方案 §5.1）：①不缓存——每次 `load`/`recall` 重新读文件、查库、调 API；②核心区永不截断——截断只接收归档段（Markdown 裁字符串、SQLite 归档查询 `LIMIT 100`、Mem0 归档结果取最近 N 条）；③ `scope` 由 Agent 显式指定（`core` / `archival`，缺省 `archival`）；④ `recall` 只搜归档区、只做关键词包含匹配，不上正则/分词。
- **Q7: `MemoryTools` 怎么注册进统一注册表？** → A（20 节 `ToolRegistryConfiguration` 显式注册先例 + 20 节 spec「`MemoryTools` 归 22 节交付后注册」）：`MemoryTools` 在 oryxos-memory 交付，装配处在 `ToolRegistryConfiguration` 现有的显式 `registerAnnotated(...)` 列表中追加它；oryxos-tool 因此新增对 oryxos-memory 的编译依赖（无环：memory → core/storage，tool → core/memory）。不走全容器魔法扫描。
- **Q8: `MemoryTools` 的 `scope` 参数缺省/非法怎么处理？** → A（课件「契约三」+ `MemoryTools` 骨架）：`null`/空白 → `ARCHIVAL`；大小写不敏感；无法识别的值抛 `IllegalArgumentException`，由 17 节 `ToolExecutor` 既有失败审计路径记 `tool_invocations`（success=false）。
- **Q9: `recall_memory` 未命中怎么办？** → A（课件 `MemoryTools` 骨架逐字）：返回「没有找到相关记忆」，不抛异常——检索无结果是正常业务语义，不打断对话。
- **Q10: `MEMORY.md` 的 header 与 18 节 init 模板不一致怎么办？** → A（本节交付物「`## 核心记忆` / `## 归档记忆` 两区块约定」+ 技术方案 §5.2）：以课件/技术方案的两级 `##` header 为准，更新 18 节 `InitCommand` 的 `MEMORY_MD` 模板；`MarkdownMemoryStore` 只认这两个 canonical header，文件缺失时按该格式自建。
- **Q11: Mem0 的 REST API 版本差异怎么处理？** → A（课件「第四步」注释 +「五」人工项）：Java 侧按课件给出的 `POST /v1/memories/`（add）、`GET /v1/memories/`（get all）、`POST /v1/memories/search/`（search）三个端点实现；具体分页/过滤参数以部署的 Mem0 版本为准，真 server 连通属人工项，CI 只用本地假 Mem0 服务验证协议与契约。
- **Q12: `MEMORY.md` 与 `USER.md` 的写权限边界？** → A（技术方案 §5.4 + 课件「五」）：`USER.md` 全程只读，代码中不得出现写 `USER.md` 的路径；`MEMORY.md`（或 SQLite/Mem0）由 Agent 经 `save_memory` 写入。

### Resolve（在 implement 前需用户知悉，Step 5 停点对账）

- **R1（`MemoryService` 门面演进 = 唯一前序公共接口改动点）**：`MemoryService` 增补 `remember(String, MemoryScope)`、`recall(String)`，新增 `MemoryScope` 枚举；17 节 javadoc 已点名由本节补齐。`PromptBuilderTest` 的 lambda 假实现需同步改为显式 stub（编译期强制，非行为回归）。
- **R2（清单外支撑类型）**：`MemoryEntryEntity`、`MemoryEntryRepository`（oryxos-storage）以及 `MemoryConfiguration`（oryxos-memory 装配类）不在课件「本节交付物」逐项清单内，但为表落地与三档装配所必需；按技术方案 §10 放 storage/装配层。Step 5 停点单独列出待确认。
- **R3（18 节 init 模板变更）**：`InitCommand.MEMORY_MD` 的 header 从 `# 核心记忆区（永不截断，全量注入）`/`# 归档记忆区（参与检索）` 改为 `## 核心记忆`/`## 归档记忆`（本节交付物点名的文件约定）。这是对前序节文件的触碰，但非公共接口变更；已有用户工作区不做自动迁移（人工项注明）。
- **R4（`ToolRegistryConfiguration` 触碰）**：在 20 节显式注册列表追加 `MemoryTools`，并给 oryxos-tool 增加 oryxos-memory 编译依赖；20 节「`MemoryTools` 22 节交付后注册」的预留兑现。若不接受 tool → memory 依赖，替代方案是把 `MemoryTools` 注册移到 CLI 启动类可扫描的装配层（需改 18 节 `SpringLauncher` 扫描范围），Step 5 停点可改选。
- **R5（Mem0 真 server 不可在本地核实）**：Mem0 是外部服务，H3 本地依赖核实只能到 Java `RestClient` 与 HTTP JSON 层；端点/字段按课件与 Mem0 公开 REST 形态实现，CI 用本地假服务验证，真连通列人工项。
- **R6（`mvn clean verify` 基线）**：implement 前先跑全量基线；P3C/SpotBugs/FindSecBugs/PMD 是构建门禁，测试方法名英文 + `@DisplayName` 中文，避开 Java 18+ P3C 解析不了的语法形态。

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Memory 门面 + 契约测试：同一套规矩，三档后端都得守（Priority: P1）

上层（`PromptBuilder` / `MemoryTools`）只认一个 `MemoryService` 门面；门面之下，长期记忆抽成可插拔的 `LongTermMemoryStore` 后端接口。三档实现（Markdown / SQLite / Mem0）遵守同一套四条行为契约：①不缓存，写后下一轮立刻可见；②核心记忆区永远完整、截断只作用于归档区；③写核心还是写归档由 Agent 用 `scope` 显式指定；④`recall` 只搜归档区、只做关键词匹配。契约测试参数化遍历三档实现——任何一档破坏契约，对应参数立刻红，而且一眼能看出是哪一档破的规矩。

**Why this priority**: 课件「二」第一、第三与「四」：门面 + 后端接口 + 契约是本节最值钱的设计；没有它，三档实现只是一堆各自为政的类。

**Independent Test**: `MemoryStoreContractTest`（参数化三档：写后立读、截断只裁归档、scope 路由、recall 只搜归档）+ `MemoryServiceImplTest`（`buildContext` 只返回长期记忆且核心区完整）。

**Acceptance Scenarios**:

1. **Given** 任一档 `LongTermMemoryStore`，**When** `append` 一条归档记忆后立刻 `load`/`recallByKeyword`，**Then** 下一次读取立即可见（无进程内缓存）。
2. **Given** 核心区一条记忆 + 远超阈值的归档流水，**When** `load`，**Then** 核心记忆完整在内、归档区最早的被裁掉、最近的保留。
3. **Given** `scope=CORE` 与 `scope=ARCHIVAL` 各写一条，**When** `load`，**Then** 两条分别路由到核心区与归档区；`recall` 只命中归档区那条。
4. **Given** 20 节之后装配的 `PromptBuilder`，**When** 构造时注入 `MemoryService`，**Then** 组装出的 Prompt 的第二段是 `buildContext` 返回的长期记忆；会话历史仍由第三段独立负责（不重复）。

### User Story 2 - Markdown 后端：零依赖的默认档（Priority: P1）

默认后端操作 `.oryxos/memory/MEMORY.md` 一个文件，用 `## 核心记忆` / `## 归档记忆` 两个区块分区；每条记忆带日期。`load` 每次重新读文件（契约一），核心区原样返回、归档区超过 4000 字符才从头部裁掉（契约二），`recall` 只在归档区做逐行包含匹配（契约四）。文件不存在时按约定格式自建，目录缺失时自动创建。

**Why this priority**: 课件「二」第二与「三」第二步：这是核心阶段的默认档，零依赖、人可读、git 可跟踪；Demo 二「日报体现用户偏好」直接依赖它。

**Independent Test**: `MarkdownMemoryStoreTest`（区块解析/自建、截断边界、recall 只搜归档、写后立读）+ `MemoryStoreContractTest` 的 Markdown 参数。

**Acceptance Scenarios**:

1. **Given** 一个不存在 `MEMORY.md` 的工作区，**When** 首次 `append`，**Then** 文件与两个区块按约定创建，条目落在请求的区块、带日期前缀。
2. **Given** 归档区超过 4000 字符，**When** `load`，**Then** 返回核心区全文 + 归档区末尾 4000 字符；核心区一个字不少。
3. **Given** 关键词只出现在核心区，**When** `recallByKeyword`，**Then** 不返回该条（核心区本来就会全量注入，不参与检索）。

### User Story 3 - SQLite 后端：记忆量变大后的结构化升级（Priority: P1）

记忆按条入库到 `memory_entries` 表（手工建表脚本，与 sessions/审计表同口径），`scope` 列区分核心/归档；核心区用 `WHERE scope='CORE'` 全量取，归档区查询加 `LIMIT 100`，检索用 SQL `LIKE`。仍复用项目已有 SQLite，零外部依赖。

**Why this priority**: 课件「二」第二/第三与「三」第三步：三档递进中的过渡档，证明「同一套契约、完全不同的落地方式」。

**Independent Test**: `SqliteMemoryStoreTest`（手工脚本能建能读、归档 LIMIT 生效、LIKE 检索）+ `MemoryStoreContractTest` 的 SQLite 参数。

**Acceptance Scenarios**:

1. **Given** 手工建表脚本已执行，**When** `append` 一条核心记忆与一条归档记忆，**Then** `memory_entries` 中两行 `scope` 正确、`load` 能读回。
2. **Given** 超过 100 条归档记录，**When** `load`，**Then** 只返回最近 100 条归档；核心区仍全量。
3. **Given** 关键词，**When** `recallByKeyword`，**Then** 只在归档区用 `LIKE` 匹配，核心区不参与。

### User Story 4 - Mem0 后端：真需要智能记忆时的外部集成档（Priority: P2）

接一个**自托管** Mem0 server（数据不出域），Java 侧用 `RestClient` 做协议转换：`append` 翻译成 Mem0 的 add（`scope` 落进 metadata），`load` 翻译成按 user 取回并按 scope 分组，`recall` 翻译成 Mem0 的 search（语义检索，契约四的加强版）。地址/凭证走环境变量占位，不落明文。该档不改变上层任何代码，切它只改 `memory.backend` 一行。

**Why this priority**: 课件「二」第二与「三」第四：证明「接口不变、实现随便换」能换到外部框架；但它依赖额外服务，默认不启用，故排 P2。

**Independent Test**: `Mem0MemoryStoreTest`（本地假 Mem0 服务：add 请求体带 scope、load 按 scope 取回、search 转发关键词，不碰真 server）+ `MemoryStoreContractTest` 的 Mem0 参数（同一假服务满足契约）。

**Acceptance Scenarios**:

1. **Given** `memory.backend=mem0` 与 `MEM0_BASE_URL`，**When** `save_memory` 写入，**Then** 发出 `POST /v1/memories/`，body 含内容、`user_id`、`metadata.scope`；不写明文凭证。
2. **Given** 假 Mem0 服务中有核心与归档条目，**When** `load`，**Then** 核心区全量、归档区只取最近 N 条，返回文本含两部分。
3. **Given** 关键词，**When** `recall`，**Then** 调用 Mem0 的 search 端点并返回结果内容；服务不可用/返回错误时异常上抛，由调用方审计，不静默吞掉。

### User Story 5 - MemoryTools 与装配：Agent 真正记得住事（Priority: P1）

把长期记忆暴露成两个内置 Tool：`save_memory`（内容 + `scope`，缺省归档）与 `recall_memory`（关键词，未命中返回「没有找到相关记忆」而不抛异常）。`MemoryTools` 只认 `MemoryService`，对底下是哪档后端完全无感；装配处把它注册进统一 `ToolRegistry`，并把 `MemoryService` 注入 `PromptBuilder`——`save_memory` 写入后，下一轮组装 Prompt 立刻带上，跨会话「始终在场」在真实链路里成立。

**Why this priority**: 课件「一」与「三」第六步：没有这两个 Tool 和 PromptBuilder 接线，Memory 模块只是库、Agent 用不上；Demo 二的偏好记忆依赖它。

**Independent Test**: `MemoryToolsTest`（scope 缺省归档、未命中返回约定文案、委托门面）+ `OryxToolContractTest` 参数化遍历自动纳入 `save_memory`/`recall_memory` + `PromptBuilderTest` 回归（注入门面后第二段仍是长期记忆）。

**Acceptance Scenarios**:

1. **Given** 模型调用 `save_memory(content, scope="core")`，**When** 工具执行，**Then** 门面 `remember` 收到 `CORE`，返回成功文案；`scope` 缺省/空白时按 `ARCHIVAL`。
2. **Given** 模型调用 `recall_memory("不存在")`，**When** 工具执行，**Then** 返回「没有找到相关记忆」，不抛异常。
3. **Given** Spring 上下文装配完成，**When** 查看工具注册表，**Then** `save_memory`/`recall_memory` 与既有内置工具同表可见，契约三件套非空。
4. **Given** 一次 `save_memory` 后下一轮 Prompt 组装，**When** `MemoryService.buildContext` 被调用，**Then** 新写入的内容出现在 system prompt 的长期记忆段。

---

## FR Requirements

### Must

- **FR1 MemoryService 门面**: 对上层提供三方法——`buildContext(Session)`（拼进 Prompt 的长期记忆内容）、`remember(String content, MemoryScope scope)`、`recall(String keyword)`；`buildContext` 只返回长期记忆，不重复会话历史；签名不出现任何后端特有词汇（课件「三」第一步、技术方案 §5.1）。
- **FR2 MemoryScope 二值**: `CORE` / `ARCHIVAL`；写核心还是写归档由 Agent 显式指定，缺省 `ARCHIVAL`（契约三）。
- **FR3 LongTermMemoryStore 后端接口**: `append(content, scope)` / `load()` / `recallByKeyword(keyword)` 三方法，接口中立、不出现 Markdown/SQLite/Mem0 等实现词汇（课件「三」第一步）。
- **FR4 四条契约**: ①不缓存（每次重新读）；②核心区永不截断，截断只作用于归档区；③ scope 路由由显式参数决定；④ recall 只搜归档区、只做关键词匹配（课件「二」第三、技术方案 §5.1）。
- **FR5 Markdown 后端**: 操作 `.oryxos/memory/MEMORY.md`；`## 核心记忆` / `## 归档记忆` 两区块；条目带日期；归档区超 4000 字符从头部裁；文件缺失自建；核心区完整返回（课件「三」第二步）。
- **FR6 SQLite 后端**: `memory_entries` 表（`id` 自增、`scope`、`content`、`created_at`）由手工建表脚本维护；核心区全量、归档区 `LIMIT 100`、检索 `LIKE`；不依赖 `ddl-auto`（课件「三」第三步、技术方案 §9.2）。
- **FR7 Mem0 后端**: 自托管 Mem0 REST 集成（`POST /v1/memories/`、`GET /v1/memories/`、`POST /v1/memories/search/`），`scope` 落 metadata；地址/凭证走环境变量；核心区全量、归档区取最近 N 条；异常上抛不吞（课件「三」第四步）。
- **FR8 后端选择**: `memory.backend`（`markdown` | `sqlite` | `mem0`），默认 `markdown`；装配处按配置造对应 `LongTermMemoryStore`，上层实现与 PromptBuilder/Tools 零改动（课件「三」第五步）。
- **FR9 MemoryTools**: `@Tool(name="save_memory")`（`content` + `scope`，缺省归档，非法值抛异常）与 `@Tool(name="recall_memory")`（关键词，未命中返回「没有找到相关记忆」）；只依赖 `MemoryService`（课件「三」第六步）。
- **FR10 装配与集成点**: `EngineConfiguration.promptBuilder` 注入 `MemoryService`（取代 17/18 节 `null` 过渡）；`ToolRegistryConfiguration` 显式注册 `MemoryTools`；`save_memory` 后下一轮 `buildContext` 立即可见。
- **FR11 文件与配置约定**: `.oryxos/memory/MEMORY.md` 两区块约定进入 18 节 `init` 模板；`application.yaml` 增 `memory.backend`；Mem0 环境变量占位不落明文（H4③）。

### 明确不做（边界）

- **不做自动抽取/自动提炼**：核心阶段写入时机完全由 Agent 主动调 `save_memory` 决定（第 21 节第十二节、技术方案 §5.5）。
- **不引向量库/自建向量层**：需要语义检索时切到 Mem0 档，进程内不引入向量依赖（技术方案 §5.5）。
- **不做情景记忆**：扩展阶段。
- **不做 Memory Wiki 式 claim/evidence 与矛盾检测**：扩展阶段。
- **不做记忆压缩**：超长只做简单截断（md 裁字符串、sqlite 用 LIMIT）。
- **不做知识图谱后端**：扩展阶段；真需要时集成可自托管方案（第 21 节第九节）。
- **不写 `USER.md`**：`USER.md` 全程只读（技术方案 §5.4）。
- **不改 `SessionManager`/`Session`/`PromptBuilder` 行为**：只改装配注入与既有门面接口的扩展；会话历史仍由 PromptBuilder 独立负责。
- **不在 CI 连真 Mem0 server**：假服务验证协议，真连通属人工项。
- **不新增 Maven 模块**：仍是 9 模块。

## Success Criteria

可自动化部分由课件「验收 harness」测试套件承载，`mvn test` / `mvn clean verify` 全绿即通过；关键回归点：

- `MemoryStoreContractTest`：**参数化遍历三档 `LongTermMemoryStore` 实现**（Markdown / SQLite / Mem0），同一套断言全过——写后立读（契约一）、截断只裁归档且核心区一字不少（契约二）、scope 路由正确（契约三）、recall 只搜归档（契约四）。任何一档破契约立刻红。
- `MarkdownMemoryStoreTest`：区块解析/自建、4000 字符截断边界、recall 只搜归档。
- `SqliteMemoryStoreTest`：手工建表脚本能建能读、归档 `LIMIT 100` 生效、`LIKE` 检索。
- `Mem0MemoryStoreTest`：本地假 Mem0 服务——`append` 请求体带 scope、`load` 按 scope 分组、`recall` 转发 search；不碰真 server。
- `MemoryToolsTest`：scope 缺省写归档、关键词未命中返回约定文案而不抛异常、委托门面。
- `MemoryServiceImplTest`：`buildContext` 返回长期记忆（核心区全量 + 归档区截断后），核心记忆完整在内；不重复会话历史。
- `OryxToolContractTest`：两个新 Tool 自动纳入契约三件套检查（非空）。
- 前序节全部测试回归绿（16~20 节跨节契约证据；`MemoryService` 接口扩展的编译/行为兼容由回归证明）。

人工项见课件「五、做完怎么验」：

- **三档切换验证**：`memory.backend` 分别设 `markdown` / `sqlite`，各跑一次对话，`save_memory` 写入、下一轮 `buildContext` 带上——同一段对话、同一个体感，只是底下换了后端。
- **Mem0 档真连一次（可选）**：部署自托管 Mem0，`memory.backend: mem0`，验证 `save_memory` 真的进了 Mem0、`recall` 能语义召回（依赖真 server，测不了，人工过）。
- **真模型完整链路**：对话里说一句值得记的话，Agent 主动调 `save_memory`；开新会话，system prompt 里带着核心记忆——「始终在场」的真实体感。
- **`MEMORY.md` / `USER.md` 角色分清**：code review 确认没有写 `USER.md` 的代码路径；`MEMORY.md` 能被 Agent 写入。
- 已有旧工作区的 `MEMORY.md` header 迁移：旧模板为 `# 核心记忆区...`，新代码只认 `## 核心记忆`；需用户手工调整或重新 `init`（不做自动迁移，避免覆盖用户内容）。

## 依赖与假设

- **前序节交付物（H0 已核对存在）**：17 节 `MemoryService` 最小接口（本节扩展，javadoc 点名）/`PromptBuilder`（含 `MemoryService` 可空注入点）/`Session`；18 节 `SessionManager`（会话历史，本节不重复负责）/`InitCommand.MEMORY_MD` 模板（本节改 header）/`EngineConfiguration`（本节换掉 `MemoryService=null` 过渡）；20 节 `OryxTool`/`ToolResult`/`ToolRegistry`/`DefaultToolRegistry.registerAnnotated`/`ToolRegistryConfiguration`（本节追加注册）。19 节 `NotifyConfiguration` 的 `RestClient` Bean 可供 Mem0 装配复用（或由 Mem0 构造注入）。
- **技术方案依据**：§5.1（门面 + 后端接口 + 四条契约）、§5.2（MEMORY.md 两区块）、§5.3（注入 system prompt、不缓存）、§5.4（USER.md 只读）、§5.5（不做项）、§9.2（手工建表）、§10（模块职责）。
- **外部依赖**：Spring Boot 3.x / Spring Data JPA / SQLite（均已有）；Spring `RestClient`（19 节已在链）。Mem0 为自托管外部服务，版本/端点差异属人工项；无新增第三方依赖家族。
- **假设**：`memory_entries` 表追加进 `001-init.sql`（与 sessions/审计表同脚本、同 `spring.sql.init` 加载路径）；SQLite `created_at` 沿用项目口径存 ISO-8601 TEXT（不交给 DB 默认值）；`MemoryEntryEntity`/`Repository` 放 storage 是技术方案 §10 的落位，若用户要求严格「全部→oryxos-memory」则在 Step 5 停点调整。
- **测试假设**：Mem0 契约参数使用本地假 Mem0 HTTP 服务（MockWebServer / JDK 本地回环），不走外网；SQLite 测试用内存库 + 真实手工脚本；Markdown 用 `@TempDir`。
