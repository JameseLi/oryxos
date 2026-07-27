---
title: 支撑模块与持久化
description: 工作区初始化、AgentLoader、ContextLoader、AgentScheduler、ConfigLoader，SQLite 持久化与 9 个 Maven 模块
---

# 支撑模块与持久化

## 8. 支撑模块

五大核心能力之外，OryxOS 还有几个支撑模块让整个系统跑起来。这些不是运行时内核的核心能力，但缺一不可。

### 8.1 工作区初始化

**`InitCommand` 模块。** `oryxos init` 命令的执行逻辑，创建 `.oryxos/` 工作目录及完整结构：

```
.oryxos/
├── agents/            # 每个子目录 = 一个 Agent（AGENT.md + 可选 skills/ scripts/ REFERENCE.md）
├── memory/
│   └── MEMORY.md      # 长期记忆
├── mcp_servers.yaml   # MCP 配置
├── sessions/          # Session 数据
├── logs/              # 日志
├── AGENTS.md
├── SOUL.md
├── USER.md            # Bootstrap
└── oryxos.db          # SQLite
```

创建目录、写默认模板、生成默认 Profile。

### 8.2 Profile 配置

**`AgentLoader` 模块。** 扫 `.oryxos/agents/` 各子目录，`deriveProfile` 把每个 `AGENT.md` 的 frontmatter 派生成一个 `Profile`，注册到 `ProfileRegistry`。启动时做合法性校验：Provider 是否存在、Tool 是否注册、Channel 是否支持、Bootstrap 文件是否存在。校验失败的 Agent 不阻断启动但记录错误日志。

**`ProfileRegistry` 模块。** Agent 派生 `Profile` 的内存索引，按 name 提供快速查找。Channel 接收消息时通过它拿到具体 Profile。派生自 `AGENT.md` frontmatter 的字段：`name`、`description`、`identity`（`agent_name`、`prompt`）、`provider`（`name`、`model`、`temperature`）、`tools`、`mcp_servers`、`channels`、`notify_channels`（`type`、渠道特定配置如 `url`，供 `NotifyTools` 用，见 6.8）、`schedules`、`bootstrap`、`settings`（`max_iterations`、`max_history_turns`）。核心阶段支持多个 Agent 并存，同一实例上同时可用，这是"OS"在核心阶段的最小体现。

### 8.3 上下文加载（Bootstrap + AGENT.md 正文）

> **调整说明：** Bootstrap 文件与 `AGENT.md` 正文都由 `ContextLoader` 全量注入 system prompt；Agent 目录里的子指令 / 脚本 / 参考不预载，由正文指引经底座 `read_file`/`shell` 按需取（一个 Agent 内部的渐进式披露，详见 11.1）。

**`ContextLoader` 模块。** 按 Profile 的 `bootstrap` 字段从 `.oryxos/` 读取 `AGENTS.md`、`SOUL.md`、`USER.md`（Bootstrap，全量注入）；同时把这个 Agent 自己 `AGENT.md` 的**正文**（任务指令）一并交给 `PromptBuilder`。每次组装 prompt 时重新加载不缓存，用户修改后立即生效。目录里的子指令 / 脚本 / 参考不在这里注入，由模型经 `read_file`/`shell` 在需要时现取——把 `AGENT.md` 归到上下文这一层而不是 Tool 模块，是因为它是 prompt 的输入、不是可执行的 Tool。

### 8.4 Channel 接入

Channel 是 Agent 对外的消息接入入口，主要解决"消息进来、响应出去"。HTTP 接入归 Web Service，不在 Channel 范畴内。

**`CliChannel` 模块。** `oryxos chat` 命令的实现，读 stdin 写 stdout 实现交互式对话，维护当前 Session，每次输入调 `AgentService.process`，支持 `/quit` 退出。扩展阶段补企业微信、飞书、钉钉、Slack 等 IM Channel，每个通过 Channel Adapter 插件机制扩展，所有 IM Channel 底层都调 Web Service 的 Agent 接口，不重复实现 Agent 逻辑。

### 8.5 定时任务（第三种触发源）

定时任务不是新增的核心能力，而是给 `AgentService` 加第三条触发路径。CLI 和 Web Service 都是"人推"——需要有人发起一次调用；`AgentScheduler` 是"钟推"——按 cron 表达式到点自动生成一条消息，调用链路跟人推完全一样，`ReActLoop` 不感知消息从哪个入口来。


**`AgentScheduler` 模块（归 `oryxos-core`）。** 基于 Spring 的 `ThreadPoolTaskScheduler` 加 `CronTrigger` 动态注册任务，不用静态的 `@Scheduled` 注解，因为触发规则要按 Profile 配置动态生成，编译期写死的注解做不到。Profile 新增 `schedules` 字段声明 cron 表达式、时区、要发给 Agent 的消息内容；OryxOS 启动时扫描所有 Profile 的 `schedules` 字段逐个注册。

**并发控制。** 每个定时任务用一把进程内的 `ReentrantLock`（按任务 id 维度）防止同一任务重叠执行——上一次还没跑完，下一次触发点到了就跳过，不排队、不并行跑两份。核心阶段是单实例部署，这把锁只解决"同一进程内不重叠"，**不是**分布式锁，多实例部署下的分布式协调放扩展阶段。

**失败处理。** 定时任务执行失败只记日志，不能让调度器本身崩溃、影响后续任务触发；失败的这次调用依然走 `AgentService.process` 内部完整的 `llm_calls`/`tool_invocations` 审计路径，跟人推触发的失败没有区别。

**会话身份。** 钟推也要落 Session：`session_id` 沿用既有公式（channel + user + profile 联合生成，见 9.2），channel 和 user 都固定为 `scheduler`。即同一个 Profile 的历次定时触发复用同一个 Session，对话历史自然累积、靠 `max_history_turns` 截断兜底——不为钟推新设任何概念。

**状态持久化与可管理（第 28 节补齐）。** 光"到点自动跑"还不够——运营方要能看见有哪些定时任务、跑过几次、上次成没成，也要能手动补跑一次、临时停掉一个任务。为此把任务状态和执行历史落 SQLite（重启不丢），并做成管理台的一等公民：

- **两张表**（手工建表脚本，见 9.2）：`scheduled_tasks` 存任务登记信息与运行状态（`task_id` 主键、`profile_name`、`cron`、`zone`、`message`、`enabled`、`next_run_at`、`last_run_at`、`last_status`、`run_count`），`task_executions` 存每次执行的历史（成功失败都记：`task_id`、`session_id`、`started_at`、`success`、`error_message`、`duration_ms`）。定义来源仍是 skill/Profile 的 `schedules`——这两张表只存"状态 + 历史"，不作为定义源，重启时从文件重新注册。
- **契约在 core、实现在 storage**（依赖倒置）：`ScheduledTaskStore` 接口（`register`/`recordExecution`/`isEnabled`/`setEnabled`/`list`/`executions`）放 `oryxos-core`，`AgentScheduler` 依赖它；JPA 实现 `JpaScheduledTaskStore` 放 `oryxos-storage`。`AgentScheduler` 启动扫描时顺带 `register` 登记，每次 `execute` 成功失败都 `recordExecution` 留痕（与宪法 V 审计同源）；`runOnce` 先看 `isEnabled`（停用则跳过、不记执行），管理台"立即执行"走 `runNow` 手动触发一次（无视启用状态）。
- **四个管理端点**（`ScheduleApiController`，前缀 `/api/v1/schedules`）：`GET /schedules` 列任务与状态、`GET /schedules/{id}/executions` 查执行历史、`POST /schedules/{id}/run` 立即执行一次、`PUT /schedules/{id}` 启用/停用。管理台"定时任务"页调这四个端点，可查可管——这是第 28 节相对第 26 节"管理台只读"的一处明确扩展（仅限定时任务这一子系统的运行控制）。

**核心阶段 vs 扩展阶段的边界。** 核心阶段的 `schedules` **定义**只能写在 `AGENT.md` frontmatter 里，跟着进程启动一起注册，改 cron / 新增任务要重启（或触发重新加载）才生效；第 28 节补齐的是任务的**状态持久化与运行控制**（查看 / 执行历史 / 立即执行 / 启用停用），不含通过 API 增删改 cron 定义。"业务方通过 Web Service 上传一个 Agent 目录（`AGENT.md` 带 `schedules` frontmatter）、由此定义一个新 Agent 并让它定时自动运行"这个完整闭环，依赖的是 7.3 里扩展阶段才补齐的两个能力——Agent 目录上传接口（含一句话生成）、`AgentScheduler` 的运行时增删接口——核心阶段这条链路要靠手动丢目录走通，扩展阶段补上后才是纯 API、免重启的闭环。

### 8.6 三种运行模式

| 命令             | 模式        | 说明                                                            |
| ---------------- | ----------- | --------------------------------------------------------------- |
| `oryxos chat`    | 交互对话    | 本地 CLI 交互                                                   |
| `oryxos serve`   | Web Service | 启动 REST API 服务（定时任务随 `serve`/`gateway` 一起常驻调度） |
| `oryxos gateway` | 守护进程    | 同时挂多个 Channel                                              |

三种模式共享同一份 Profile 配置和 Session 存储，差异只是接入层。

### 8.7 命令行工具

**`OryxOsCli` 模块。** Picocli 命令行入口，整个 OryxOS 的 `main` 函数，注册 12 个子命令：

```
init
status
chat
serve
gateway
profile list / create / show / delete
provider list
tool list
session list
```

每个子命令一个 `@Command` 类。不需要 Spring 上下文的命令（`init`、`profile list`）直接走文件操作启动快；需要 LLM 调用的命令（`chat`、`serve`、`gateway`）启动 Spring 上下文。

### 8.8 配置与密钥加载

**`ConfigLoader` 模块** 负责统一加载 LLM API key、Provider 凭证、MCP server 凭证等敏感配置。核心阶段做基础版：敏感配置通过环境变量注入或独立的本地配置文件加载，不明文写死在 Profile YAML 里（Profile 里用 `${ENV_VAR}` 占位，加载时从环境变量解析）；配置加载时做必填项和格式的基础校验，缺失或非法时给清晰报错。完整的加密存储、密钥轮转、对接企业 KMS/Vault 放扩展阶段。单列这个模块，是因为对企业级底座，配置和密钥的加载校验是 day one 该有的，不能散落各模块无人负责。

---

---

## 9. 数据持久化

### 9.1 持久化选型说明

核心阶段选 **SQLite** 加 **Spring Data JPA** 做关系型持久化，**`MEMORY.md`** 文件加关键词检索做长期记忆。

**为什么核心阶段不用向量数据库：** LanceDB 在向量加全文检索上做得好，是 Memory 自然的升级方向，但它的 Java 本地嵌入式支持还在开发中，当前 Java SDK 只支持远程的 Cloud 或 Enterprise，不符合 OryxOS 单二进制部署的定位。其他向量库（Qdrant、Chroma、Milvus）都需要外部进程，pgvector 要外部 PostgreSQL。JVector 这种纯 Java 嵌入式向量库是另一条路但成熟度待验证。

核心阶段的判断是先用 SQLite 加 `MEMORY.md` 跑通最短链路，让实现者先掌握 Agent OS 的核心机制，向量检索这种检索体验优化放扩展阶段。

**扩展阶段升级路径：**

- **方案 A：** 等 LanceDB Java 本地嵌入式 GA 后切换，保持单二进制
- **方案 B：** 接 PostgreSQL pgvector，企业部署多起一个 PG 服务，社区最成熟
- **方案 C：** 用 JVector 纯 Java 嵌入式向量索引跟 SQLite 双写，保持单二进制

具体选哪个扩展阶段决议。核心阶段 `LongTermMemory` 接口已预留升级空间（`recallByKeyword` 可升级为带 `mode` 的 `recall`），切换底层不影响上层 Tool。

### 9.2 SQLite 关系型数据

通过 Spring Data JPA 集成，`application.yaml` 配置数据源指向 `.oryxos/oryxos.db`。

> **工程风险提示：** SQLite 本身 `ALTER TABLE` 能力有限，`hibernate.ddl-auto=update` 在 SQLite 上对表结构演进的支持很弱。核心阶段首次建表用 `update` 可以，但表结构后续演进时不要依赖 `update` 自动迁移，需要手动维护建表脚本或引入 Flyway/Liquibase。

核心表五张：

1. **`sessions`**：Session 元数据加 JSON 序列化的对话历史
2. **`tool_invocations`**：每次 Tool 调用记录
3. **`llm_calls`**：每次 LLM 调用记录
4. **`scheduled_tasks`**：定时任务登记信息与运行状态（第 28 节，见 8.5）
5. **`task_executions`**：定时任务每次执行的历史，成功失败都记（第 28 节，见 8.5）

> **相对原方案的调整：** `tool_invocations` 和 `llm_calls` 在核心阶段就做写入（不一定做查询接口），因为"可审计"是 OryxOS 的差异化卖点之一，审计数据的地基应该 day one 就立起来，纯靠日志后期要做审计还得反解析返工。查询接口和审计报表放扩展阶段，但写入核心阶段就有。`scheduled_tasks`/`task_executions` 是第 28 节把"定时任务"做成可查可管的一等公民时补的两张表，让任务状态与执行历史重启不丢（见 8.5）。

**`sessions` 实体字段：**

| 字段             | 说明                                      |
| ---------------- | ----------------------------------------- |
| `session_id`     | 主键，channel 加 user 加 profile 联合生成 |
| `profile_name`   | 关联 Profile                              |
| `channel`        | 接入 Channel                              |
| `user_id`        | 用户标识                                  |
| `messages_json`  | JSON 序列化的对话历史                     |
| `status`         | `active` / `archived`                     |
| `created_at`     | 创建时间                                  |
| `last_active_at` | 最后活跃时间                              |
| `archived_at`    | 归档时间                                  |

**`scheduled_tasks` 实体字段（第 28 节）：**

| 字段           | 说明                                                        |
| -------------- | ----------------------------------------------------------- |
| `task_id`      | 主键，schedule 的 id（Profile/Skill 的 `schedules` 里声明） |
| `profile_name` | 归属 Profile                                                |
| `cron`         | cron 表达式                                                 |
| `zone`         | 时区                                                        |
| `message`      | 到点发给 Agent 的消息                                       |
| `enabled`      | 是否启用（管理台开关，默认启用）                            |
| `next_run_at`  | 下次触发时刻                                                |
| `last_run_at`  | 上次触发时刻                                                |
| `last_status`  | 上次结果 `success` / `failed`                               |
| `run_count`    | 累计触发次数                                                |
| `updated_at`   | 状态更新时间                                                |

**`task_executions` 实体字段（第 28 节）：**

| 字段            | 说明                       |
| --------------- | -------------------------- |
| `id`            | 主键，自增                 |
| `task_id`       | 关联 `scheduled_tasks`     |
| `session_id`    | 本次触发所用的钟推 Session |
| `started_at`    | 开始时间                   |
| `success`       | 是否成功                   |
| `error_message` | 失败信息（可空）           |
| `duration_ms`   | 执行耗时                   |

### 9.3 文件系统数据

`.oryxos/` 里几类数据放文件系统不放 SQLite：Agent 目录（`AGENT.md` + 脚本 / 子指令）、Bootstrap 文件、Memory（`MEMORY.md`）、MCP 配置、日志。文件系统的优势是用户可以直接编辑、git 跟踪、备份。Agent 目录和 Bootstrap 这种用户主动维护的数据放文件系统比放数据库友好。

---

---

## 10. 项目工程结构

OryxOS 是 Maven 多模块项目，由 9 个模块组成：

| 模块名               | 职责                                                                                                                                                                                                                                                                                                                                      |
| -------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `oryxos-core`        | 核心抽象和接口：`OryxTool` 接口、`Session`、`Profile`、`ContextLoader`、`AgentLoader`（扫 `.oryxos/agents/`、`deriveProfile`）、`ReActLoop`、`PromptBuilder`、`ToolExecutor`、`AgentService`、`AgentScheduler`（定时触发）、`AgentLifecycleService`（扩展阶段，编排"定义一个 Agent（Agent 目录落盘 + 派生 Profile + 注册 + Scheduler）"） |
| `oryxos-provider`    | 核心能力一：`ProviderService`、Function Calling 适配、Provider 配置（provider name 到 `ChatModel` 显式映射）                                                                                                                                                                                                                              |
| `oryxos-memory`      | 核心能力三：`MemoryService` 统一门面、`LongTermMemory`、`MemoryTools`（`save_memory` / `recall_memory`）                                                                                                                                                                                                                                  |
| `oryxos-tool`        | 核心能力四：内置 Tool（`FileTools`、`ShellTools`、`HttpTools`、`NotifyTools`）、`McpClientService`、`McpToolAdapter`、`ToolRegistry`、`Sandbox` 接口 + `WhitelistSandbox` 实现、`NotifyChannelAdapter` 接口 + `WebhookNotifyAdapter` 实现（三合一模块）                                                                                   |
| `oryxos-channel-cli` | CLI Channel：`CliChannel`、`oryxos chat` 命令实现                                                                                                                                                                                                                                                                                         |
| `oryxos-web`         | 核心能力五：`WebServer`、6 个 `ApiController`、`GlobalExceptionHandler`、OpenAPI 文档                                                                                                                                                                                                                                                     |
| `oryxos-storage`     | 持久化层：SQLite、`SessionRepository`、`ToolInvocationRepository`、`LlmCallRepository`                                                                                                                                                                                                                                                    |
| `oryxos-cli`         | 命令行入口：Picocli 主入口、12 个子命令、`ConfigLoader`                                                                                                                                                                                                                                                                                   |
| `oryxos-boot`        | Spring Boot 启动模块：主类、自动配置、依赖聚合                                                                                                                                                                                                                                                                                            |

模块之间通过接口解耦。扩展阶段加新 Channel 或新 Tool 实现只加新模块不改 core，所有 Channel 模块底层都调 `oryxos-web` 的 Agent 接口。

打包：

```bash
mvn clean package
```

生成 fat JAR，`java -jar` 启动，扩展阶段通过 **GraalVM Native Image** 编译成原生二进制。

---
