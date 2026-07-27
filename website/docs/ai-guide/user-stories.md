---
title: 五个 User Story
description: US-1 到 US-5 的实施拆解、跨 story 协作模式与交付物
---

# 五个 User Story

## 4. 基于 Spec-Kit 的实施拆解

准备阶段把整体 spec 和 plan 都准备好了，下面按 5 个 user story 拆解具体实施。每个 user story 的拆解结构一致：核心目标、涉及的 Maven 模块、Spec-Kit 任务拆分思路、关键 task 颗粒度、验收 Demo。模块名以技术方案第 10 章的 9 模块为准。

---

### 4.1 US-1：对接 LLM（核心能力一）

**核心目标**：让 OryxOS 能调任意主流 LLM，Agent 不感知具体调的是哪家。LLM 调用的复杂度都被 Spring AI Alibaba 吸收，OryxOS 只在它之上做一层薄包装。

**涉及的 Maven 模块**：
- `oryxos-core`（`OryxTool` 接口、`Session`、`Profile`、`ContextLoader` 等核心抽象）
- `oryxos-provider`（核心能力一）
- `oryxos-boot`（Spring Boot 启动模块）

**Spec-Kit 任务拆分思路**：`/speckit.tasks` 针对 US-1 拆任务，按依赖关系排序，标记可并行任务。预期产出的 task 大类：

| Task 类别       | 主要内容                                                                                     |
| --------------- | -------------------------------------------------------------------------------------------- |
| 环境搭建类      | Maven 多模块骨架 9 个模块、Spring Boot 启动配置、Spring AI Alibaba 依赖                      |
| 核心抽象类      | `OryxTool` 接口、`Profile` 数据结构、`Message` 数据结构                                      |
| Provider 实现类 | `ProviderService` 实现、provider name 到 `ChatModel` 的显式映射、Function Calling 适配       |
| 配置类          | `application.yaml` 配置至少跑通 DeepSeek 或 Kimi，配合 `ConfigLoader` 从环境变量加载 API key |

> **关键注意**：`ProviderService` 不能靠"扫描容器里所有 `ChatModel`"来区分 Provider，多 Provider 并存时 Bean 类型相同会有歧义，必须维护 provider name 到 `ChatModel` 的显式映射（技术方案 3.2）。AI agent 很容易写成类型扫描，要在 task 里点明。

US-1 实施完成后不立刻有 demo，因为它没有用户可见的入口，下一步 US-2 完成后跟 US-1 一起跑 Demo 一。

---

### 4.2 US-2：ReAct 循环（核心能力二）

**核心目标**：实现 Agent 的核心工作机制。即：LLM 思考是否调用工具，调用之后看结果，再决定下一步，直到给出最终响应。ReAct 循环是 OryxOS 最关键的一段代码。

**涉及的 Maven 模块**：
- `oryxos-core`（`ReActLoop`、`PromptBuilder`、`ToolExecutor`、`ContextLoader`）
- `oryxos-tool`（一个 HTTP Tool + `SandboxChecker` 简化版，Demo 一需要）
- `oryxos-channel-cli`（CLI Channel，Demo 一需要）
- `oryxos-cli`（`oryxos init` + `oryxos chat` 命令）

> 注意这里 Tool 相关只有一个 `oryxos-tool` 模块（技术方案已把 builtin/skill/mcp 合并），不再是旧版的多个 tool 模块。

**Spec-Kit 任务拆分思路**：预期产出的 task 大类：

| Task 类别           | 主要内容                                                                   |
| ------------------- | -------------------------------------------------------------------------- |
| ReAct 循环类        | `ReActLoop` 主循环、`PromptBuilder`、`ToolExecutor`、`MAX_ITERATIONS` 控制 |
| CLI Channel 类      | `CliChannel`、`oryxos chat` 命令、`oryxos init` 工作区初始化               |
| 基础 Tool 类        | HTTP Tool、`SandboxChecker` 简化版（只校验 URL 白名单）                    |
| Profile YAML 解析类 | SnakeYAML、Profile 校验                                                    |
| Session 类          | `Session` 数据结构、`SessionManager` 内存版（持久化放 US-5）               |

**关键 task 颗粒度**：US-2 是 Spec-Kit 拆分的重点。几个需要拆细的复杂 task：
- `ReActLoop` 主循环（核心循环逻辑精简约数十行 Java，但工程化部分如错误处理、日志、消息累积、迭代次数控制建议拆 2~3 个子 task）
- `PromptBuilder` 组装（四部分内容即 system prompt + Bootstrap + Memory + 对话历史 + Tool 列表，建议拆成几个子 task 逐步加入）

> **再次强调 constitution 原则四**：调用 Spring AI 时只用它的协议转换和 schema 生成，**禁用它的自动 tool 执行**，tool 的实际调度由 `ToolExecutor` 控制。AI agent 实现 `ReActLoop` 时很容易顺手启用 Spring AI 的自动执行，导致 tool 被调两次，task 里要明确禁用。

US-1 + US-2 完成后跑 `/speckit.analyze` 检查 spec 跟代码一致性。

**验收 Demo 一**：查天气穿衣

`oryxos chat` 启动 CLI，用户输入"查一下北京天气并告诉我穿什么"，Agent 通过 ReAct 循环调用 HTTP Tool 拉天气 JSON，根据数据回复穿衣建议，完整对话日志正确累积到 Session，至少跑通一个 Provider（DeepSeek 或 Kimi）。

---

### 4.3 US-3：Memory 三层记忆（核心能力三）

**核心目标**：让 Agent 跨对话保留状态。核心阶段做极简版的两层（会话和长期），用一份 `MEMORY.md` 文件加两个内置 Tool 实现，让 Agent 主动写入和读取。

**涉及的 Maven 模块**：
- `oryxos-memory`（核心能力三，含 `MemoryService` 三层门面、`LongTermMemory`、`MemoryTools`）

**Spec-Kit 任务拆分思路**：US-3 相对独立，依赖 US-2 但不影响 US-4。预期产出的 task 大类：

| Task 类别              | 主要内容                                                                                                   |
| ---------------------- | ---------------------------------------------------------------------------------------------------------- |
| `MemoryService` 门面类 | 三层统一门面，内部把会话记忆委托给 `SessionManager`、长期记忆委托给 `LongTermMemory`                       |
| `LongTermMemory` 类    | `append`、`load`、`recallByKeyword`、`truncateIfNeeded` 四个方法，接口预留 `recall(mode)` 向量检索升级空间 |
| `MemoryTools` 类       | `save_memory` + `recall_memory` 两个内置 Tool，用 `@Tool` 注解                                             |
| `PromptBuilder` 集成类 | 在 `PromptBuilder` 里通过 `MemoryService` 注入记忆，确保不破坏 US-2 跑通的 ReAct 循环                      |
| `MEMORY.md` 文件管理类 | 文件位置、格式约定、超长截断策略                                                                           |

US-3 实施完成后跑 `/speckit.analyze`。

**验收 Demo 二**：跨对话记偏好

第一次对话告诉 Agent"我项目用 Spring Boot，部署在 K8s 上"，Agent 主动调 `save_memory` 追加到 `MEMORY.md`；重启 OryxOS 或新开会话；第二次对话问"帮我看看我的项目能用什么数据库"，Agent 在响应里引用之前记的偏好给出建议。

---

### 4.4 US-4：Plugin Tool 体系（核心能力四）

**核心目标**：让业务方扩展 OryxOS 的能力。Plugin Tool 三档接入：
1. 零代码 SKILL.md + MCP（主推）
2. 轻代码自写 MCP server
3. 重代码 Java `@Tool` 注解

核心阶段做完三档基础设施 + 内置 Tool 补齐。

**涉及的 Maven 模块**：
- `oryxos-tool`（补齐文件 Tool + Shell Tool、MCP Client、`SandboxChecker` 完整版、`ToolRegistry`，三合一模块）
- `oryxos-core`（SKILL.md 的加载归 `ContextLoader`，不在 tool 模块）

**Spec-Kit 任务拆分思路**：US-4 跟 US-3 可以并行（都依赖 US-2 但互不依赖）。预期产出的 task 大类：

| Task 类别        | 主要内容                                                                                                            |
| ---------------- | ------------------------------------------------------------------------------------------------------------------- |
| 内置 Tool 补齐类 | `read_file`、`write_file`、`list_dir`，Shell Tool 带白名单，`SandboxChecker` 完整实现                               |
| MCP Client 类    | `mcp_servers.yaml` 解析、`McpClientService` 启动时连接、`tools/list` 拉工具、`McpToolAdapter` 包装成 `OryxTool`     |
| `AGENT.md` 类    | `ContextLoader` 加载 `.oryxos/agents/` 下每个 Agent 的 `AGENT.md` 正文拼接到 system prompt，这部分归 core 不归 tool |
| Agent 定义类     | `AgentLoader.deriveProfile` 从 `AGENT.md` frontmatter 派生 `Profile`（含 `tools` / `mcp_servers` 等字段）           |

**关键 task 颗粒度**：US-4 的 task 数量较多，几个需要重点拆解的复杂 task：

- **MCP Client 集成**（MCP 协议是 JSON-RPC over stdio 或 SSE，Java 生态成熟度不如 Python）：
  - 建议先实现 stdio transport（最常用），SSE 放扩展
  - stdio MCP Client 建议拆几个子 task：连接管理、`tools/list`、`tool/call`、错误恢复
- **`SandboxChecker` 完整版**（从 US-2 的简化版扩展到完整版：文件路径白名单 + Shell 命令白名单 + HTTP 域名白名单，建议拆 3 个子 task）

US-4 实施完成后跑 `/speckit.analyze`。

**验收 Demo 三**：零代码 PR digest

业务方写一个 Agent 目录 `.oryxos/agents/daily-pr-digest/`（`AGENT.md` 正文描述任务），在 `mcp_servers.yaml` 配置 `github-mcp`（用社区现成的 MCP server），Agent 启动后能读 `AGENT.md` 正文、调 `github-mcp` 拉 PR、汇总成简报，整个过程业务方零代码只写了一个目录 + 配置。

---

### 4.5 US-5：Web Service（核心能力五）

**核心目标**：把 OryxOS 的所有能力通过 REST API 对外暴露，业务系统通过 HTTP 接入。这是 OryxOS 区别于个人助手项目的关键能力。

**涉及的 Maven 模块**：
- `oryxos-web`（核心能力五）
- `oryxos-storage`（SQLite 持久化层，Session 持久化从内存版升级，并落 `tool_invocations` 和 `llm_calls` 审计表）
- `oryxos-cli`（Picocli 12 个命令补全）
- `oryxos-core`（`ConfigLoader`、`ContextLoader` 的 Bootstrap 加载补全）

**Spec-Kit 任务拆分思路**：US-5 依赖前 4 个 user story 都完成，是最后实施的 user story，Spec-Kit 拆解的任务密度最高。预期产出的 task 大类：

| Task 类别             | 主要内容                                                                                                                |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| Web Service 基础类    | `WebServer` 启动 + virtual thread 配置、`GlobalExceptionHandler`、OpenAPI 文档                                          |
| 6 个 ApiController 类 | Session + Agent + Profile + Memory + Tool + System，每个 Controller 一组端点，**可并行实现**                            |
| 核心 10 个 REST 端点  | 会话管理 4 个、Agent 调用 1 个、Profile/Memory/Tool 列表 3 个、health/info 2 个                                         |
| 持久化升级类          | Session 从内存版升级到 SQLite，`SessionRepository`，跨重启恢复，以及 **`tool_invocations` 和 `llm_calls` 审计表的写入** |
| 配置与上下文类        | `ConfigLoader` 配置密钥加载，`ContextLoader` 的 Bootstrap 文件加载补全并跟 `PromptBuilder` 集成                         |
| CLI 完整版            | Picocli 12 个命令全部实现                                                                                               |
| 工程化类              | Logback + SLF4J 结构化日志 + 错误处理                                                                                   |

> **注意审计表的写入**（constitution 原则六）：`tool_invocations` 和 `llm_calls` 核心阶段就落库，不是只放日志，这样可审计的数据地基 day one 就立起来。这一点 AI agent 容易漏掉（觉得日志够了），task 里要明确。

**关键 task 颗粒度**：US-5 工程量最大。
- 6 个 `ApiController` 可以并行实现（互不依赖），每个 Controller 1~2 个端点
- Session SQLite 升级主要是 `SessionRepository` + `messages_json` 序列化，要小心 Session 状态的迁移
- Bootstrap 加载（`ContextLoader`）跟 `PromptBuilder` 集成时确保不破坏之前跑通的 ReAct 循环

US-5 完成后跑最后一次 `/speckit.analyze`，整个主体开发完成。

**验收 Demo 四**：Web Service 同步调用

外部系统 `POST /api/v1/sessions` 创建 Session，`POST /api/v1/sessions/{id}/messages` 发消息，`GET` 查历史，`DELETE` 归档，完整链路跑通。

**验收 Demo 五**：Web Service 多端点联动

外部系统调 `GET /info` 查健康 + Provider 列表、`GET /profiles` 列可用 Agent、`GET /tools` 查可用 Tool、`POST /agents/{name}/invoke` 无状态调用 Agent、`GET /memory` 查长期记忆，5 个不同端点协同完成一次业务流程。

---

### 4.6 实施过程中的协作模式

5 个 user story 的实施过程中有几个跨 user story 的协作要点：

**`/speckit.analyze` 每个 user story 结束后必跑**

检查 constitution + spec + plan + tasks + 代码是否一致，发现漂移立刻修正，这是 Spec-Kit 防漂移的核心命令，**不能省**。

**AI agent 跑偏 constitution 时主动纠正**

看到 Claude Code 生成的代码不符合 constitution，主动让 AI agent 重读 constitution 改正。OryxOS 最容易被写错的几个点：

| 问题                                           | 正确做法                                         |
| ---------------------------------------------- | ------------------------------------------------ |
| 用了非 JDK 21 特性                             | 强制要求 JDK 21                                  |
| 改了 ReAct 实现方式（依赖 Spring AI 自动执行） | 自实现，tool 被调两次时立刻查这里                |
| 启用了 Spring AI 自动 tool 执行                | 必须禁用，见 constitution 原则四                 |
| 把 Tool 又拆成多模块                           | 应该合并为一个 `oryxos-tool` 模块                |
| Provider 用类型扫描                            | 必须用显式 provider name 映射                    |
| `AgentLoader`/`AGENT.md` 当成 Tool             | Agent 目录应该归 `ContextLoader`，在 core 模块里 |
| 审计表没落库                                   | `tool_invocations` 和 `llm_calls` day one 写入   |

**跨 task 上下文丢失时回到 spec**

Spec-Kit 把代码拆成多个 task 后，AI agent 实施每个 task 时可能不知道前面任务做了什么，定期让它读 `spec.md` + `plan.md` + 最近的代码。

**git commit 标记每个 user story 完成**

方便随时回退到稳定状态。

---

---

## 5. 项目交付物

主体开发完成后 OryxOS 1.0 是一个可演示的最小完整 Agent OS 运行时内核，五大核心能力全部跑通。除了核心代码本身，还有几个交付物：

### 项目主页

OryxOS 作为开源项目需要一个独立的主页作为对外门面，技术栈用 VitePress 或类似静态站点工具，内容讲清楚 OryxOS 是什么、五大核心能力是什么、怎么快速开始。

### Spec-Kit Artifacts 保留

`.specify/` 目录下的 constitution、spec、plan 在主体开发结束后仍然保留在仓库里，作为社区接力的长期参考。

### 社区文档

API 参考文档、部署运维手册、贡献者指南这些剩余文档作为社区共建项目，由社区贡献者通过 PR 完成。

---
