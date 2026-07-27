---
title: Memory 三层记忆
description: MemoryService 统一门面、三档 LongTermMemoryStore 后端与 MEMORY.md 文件设计
---

# Memory 三层记忆

## 5. 核心能力三：Memory 三层记忆

Memory 是 Agent OS 区别于普通 chatbot 的核心能力。三层记忆是完整设计，核心阶段做会话和长期两层，情景记忆放扩展。

> **架构调整说明：** Memory 做成三层记忆的统一门面，对 ReAct 循环只暴露一个 `MemoryService` 接口，内部再分会话记忆和长期记忆。这样对外叙述的"三层记忆"和内部实现一致，ReAct 循环不需要分别去问 Session 和 `MEMORY.md` 两个地方。

### 5.1 模块组成

**`MemoryService` 模块（统一门面）。** 对 ReAct 循环暴露统一的记忆读写接口。内部把会话记忆委托给 `SessionManager`（底层是 SQLite 的 Session 存储），把长期记忆委托给 `LongTermMemory`（底层是 `MEMORY.md` 文件）。ReAct 循环组装 prompt 时只调 `MemoryService` 一个接口拿到完整上下文。这是相对原设计的关键调整，避免 Memory 概念横跨两个模块却没有统一入口。


**`LongTermMemoryStore` 后端接口（可插拔）。** 长期记忆抽成一个后端接口，把"长期记忆的读写契约"和"具体存哪、怎么存"解耦——这是第 21 节评审那道"接口墙"在实现层的落地。对外三个方法：

- `append(content, scope)`（追加内容到指定分区，`scope` 取 `MemoryScope.CORE` 或 `ARCHIVAL`，默认 `ARCHIVAL`）
- `load`（返回核心记忆区全量 + 归档记忆区截断后的内容，核心区永远完整不截断）
- `recallByKeyword`（按关键词检索，只在归档记忆区做匹配，核心区不参与检索因为它本来就会被全量注入）

所有实现共同遵守四条行为契约：①不缓存（每次重新读文件/查库/调 API）；②核心记忆区永不被截断，截断只作用在归档区；③写核心还是写归档由 Agent 经 `scope` 显式指定，系统不猜；④`recall` 是关键词检索不做复杂化。核心阶段**不做自动抽取**，分区完全由 Agent 通过 `save_memory` 的调用时机和 `scope` 参数手动决定，这是信号驱动升级原则在 Memory 模块的体现——自动从对话历史提炼记忆放到扩展阶段。

**三档后端实现（核心阶段一次交付，靠配置 `memory.backend` 选一个）。** 递进对应第 21 节讲的三级演进：

- **`MarkdownMemoryStore`（默认）。** 底层操作 `.oryxos/memory/MEMORY.md` 一个 Markdown 文件，按 `## 核心记忆` / `## 归档记忆` 两个 header 分区（详见 5.2）；截断是字符串裁归档段，检索是 `String.contains` 行匹配。零依赖、人可读、git 可跟踪，记忆量不大时的首选。
- **`SqliteMemoryStore`。** 记忆按条入库到 `memory_entries` 表（手工建表脚本，与 sessions/审计表同口径），截断变成归档查询的 `LIMIT N`、检索变成 SQL `LIKE`、核心区用 `WHERE scope='CORE'` 全量取。仍**零外部依赖**（复用已有 SQLite），记忆量上千、要结构化查询时的升级档。
- **`Mem0MemoryStore`。** 接一个**自托管** Mem0 记忆层（数据不出域），Java 侧走 REST 集成，`append/load/recall` 翻译成 Mem0 的 add/get/search——提炼、冲突消解、语义检索都交给 Mem0。凭证与地址走环境变量占位。这是"真需要智能记忆"时的外部集成档，对应第 21 节第十四节"记忆若非核心差异化能力、集成可自托管方案是理性选择"。

换后端只改 `memory.backend` 一行配置，`MemoryService` 以上（`PromptBuilder`/`MemoryTools`/`ReActLoop`）一个字不动——这就是接口墙的价值兑现。`recallByKeyword` 也预留了语义升级空间（Mem0 档已是语义检索），切换底层不影响上层。

**`MemoryTools` 子模块。** 把长期记忆暴露给 Agent 调用，包含 `save_memory` 和 `recall_memory` 两个内置 Tool，标注 `@Tool` 注解自动注册到 `ToolRegistry`，跟其他内置 Tool 一视同仁。

**会话记忆。** 由 `SessionManager` 实现（见第 8 章），通过 SQLite 持久化，按 Channel 加用户加 Profile 联合标识管理。`MemoryService` 把它作为三层之一统一对外。

### 5.2 MEMORY.md 文件设计（默认后端 `MarkdownMemoryStore`）

默认后端的文件位置 `.oryxos/memory/MEMORY.md`，内部用两个一级分区组织，每条记忆带日期 header：


格式不做更严格的规定，Agent 写什么 LLM 自己理解就行，简单但有效；两个分区只是组织方式上的区分。换到 `SqliteMemoryStore` 时同一套"核心/归档"语义落到 `memory_entries` 表的 `scope` 列，换到 `Mem0MemoryStore` 时落到 Mem0 的 metadata——分区约定不变，存储形态随后端而变。

### 5.3 Memory 注入到 system prompt

ReAct 循环每次组装 prompt 时，`MemoryService` 把会话历史和长期记忆（核心记忆区加归档记忆区，经 `LongTermMemoryStore.load()` 取得）提供给 `PromptBuilder`。长期记忆每次重新读不做缓存（契约一），这样 Agent 调用 `save_memory` 后下一轮立刻能看到——Markdown 档每次读一个小文件、SQLite 档每次查库、Mem0 档每次调 API，性能都可接受。扩展阶段可在门面背后加 in-memory cache 加失效机制。

### 5.4 MEMORY.md 跟 USER.md 的区别

| 文件        | 来源                          | 读写方          | 用途                               |
| ----------- | ----------------------------- | --------------- | ---------------------------------- |
| `USER.md`   | 用户手写                      | OryxOS 只读不写 | 用户的"初始设定"（Bootstrap 文件） |
| `MEMORY.md` | Agent 通过 `save_memory` 写入 | OryxOS 读写     | Agent 的"成长记录"（长期记忆）     |

两者都进 system prompt，但来源和生命周期不同。

### 5.5 核心阶段不做的部分

- 自动抽取（由 LLM 自己决定何时调 `save_memory`，Markdown/SQLite 两档不自动从对话提取；Mem0 档的自动抽取是其自带能力，用不用取决于是否切到该后端）
- 内置向量库（核心阶段 md/sqlite 两档不引入向量依赖；需要语义检索时切到 `Mem0MemoryStore` 由外部服务承担，不在 OryxOS 进程内自建向量层）
- 情景记忆（放扩展）
- Memory Wiki（结构化 claim/evidence、矛盾检测）
- 记忆压缩（超长简单截断，md 裁字符串、sqlite 用 LIMIT）
- 知识图谱后端（第 21 节第九节：门槛比向量更高，真需要时集成 Graphiti 类可自托管方案，不自造）

---
