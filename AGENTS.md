# AGENTS.md

> 本文件是给 AI 编码助手（Claude Code、Cursor、Copilot、pi 等）的项目级工作指南。
> 在这个仓库里做任何改动之前，先读完本文件；涉及架构取舍时，以 `docs/` 下四份文档为最终依据。

---

## 1. 项目是什么

**OryxOS**：基于 Java 实现的、面向企业场景的 **Agent OS**（Agent 操作系统）。

装在企业自己的 K8s/服务器上，作为统一底座跑多个业务 Agent（运维助手、客服助手、HR 助手、知识管理助手等），共享一套渠道接入、模型路由、工具调用、记忆系统、沙箱执行能力。数据完全留在企业基础设施，不锁任何云生态。

核心认知：

- **Agent OS ≠ Agent Runtime**。Runtime 让一个 Agent 跑起来；Agent OS 让一群 Agent 在企业里被管起来（生命周期、统一接入、统一记忆、治理审计）。
- **做运行时，不做编排**。OryxOS 不做可视化 workflow 编排（那是 Dify 的事，两者互补），专注守住"运行时"这一层。
- **核心阶段交付运行时内核**，企业级治理层（多租户、SSO、完整审计、Tool Policy）放扩展阶段。核心阶段是地基，治理是终局。

## 2. 文档地图（单一事实来源）

| 文档                          | 回答     | 内容                                                         |
| ----------------------------- | -------- | ------------------------------------------------------------ |
| `docs/IndustryResearch.md`    | Why      | 业界格局、Java 生态缺位、OryxOS 定位与边界、三阶段路线       |
| `docs/DemandAnalysis.md`      | What     | 五大核心能力、术语、核心/扩展/社区三档功能、非功能需求、验收 |
| `docs/TechnicalSolution.md`   | How      | 技术栈、7 个关键决策、模块职责、数据持久化、三个验收 Demo    |
| `docs/AiProgrammingGuide.md`  | 怎么造   | Spec-Kit 实施拆解、5 个 user story、AI 协作模式              |

规则：

- **代码行为与文档冲突时，以文档为准**；要改文档级别的决策，先停下来与人讨论，AI 不得擅自修改架构决策。
- 不要凭记忆实现，动手前重读相关章节（尤其技术方案第 1.1 节关键决策、第 10 章模块表、第 11 章 Agent 目录定义）。

## 3. 技术栈（不可协商）

- **JDK 21+**（不使用更低版本语法/API；virtual thread 用于高并发）
- **Spring Boot 3.x** 单体应用，**Maven 多模块（9 个）**，单二进制 fat JAR 部署
- **Spring AI + Spring AI Alibaba**：LLM Provider 抽象（复用现成 connector，不自己造）
- **自实现 ReAct loop**：不依赖 Spring AI 的 Agent 抽象
- **SQLite + Spring Data JPA**：Session、审计数据持久化（`.oryxos/oryxos.db`）
- **`MEMORY.md` 文件**：长期记忆（核心阶段关键词检索，不引向量库依赖）
- **Picocli**：命令行（12 个子命令）；**SnakeYAML**：YAML 解析；**Spring MVC**：HTTP 层
- 结构化日志 Logback + SLF4J；核心阶段**不引入** GraalVM、WebFlux、响应式编程

## 4. 非协商原则（Constitution）

违反以下任何一条的代码都是错的，必须返工：

1. **JDK 21 + Spring Boot 3.x 单体**，Maven 9 模块结构（见第 6 节），不多不少。
2. **核心阶段只做运行时内核**：五大核心能力 + 支撑模块。多租户、SSO、RBAC、完整审计查询、Tool Policy、向量检索、SSE 流式、WebSocket、认证限流——**一律不做**，留接口扩展点即可。
3. **自实现 ReAct loop**（约数十行核心代码），完全掌控循环行为。
4. **Spring AI 只用一半**（最容易写错的一条）：
   - ✅ 用：Provider 抽象、各家 LLM 协议转换、`@Tool` 注解的 JSON Schema 生成
   - ❌ 禁用：Spring AI 的**自动 tool 执行**。Tool 调度与执行完全由 OryxOS 自己的 `ReActLoop` + `ToolExecutor` 控制，否则 tool 会被调两次。
5. **Plugin Tool 三档接入**，主推零代码档（Agent 目录 + 复用社区 MCP server）：
   - 零代码：`.oryxos/agents/<name>/` 目录 + 现成 MCP server（⭐ 主推）
   - 轻代码：业务方自写 MCP server（任何语言）
   - 重代码：`@Tool` 注解 Java Spring Bean
6. **SQLite + MEMORY.md**：审计表 `tool_invocations` 和 `llm_calls` **核心阶段就写入落库**（可只写不提供查询接口），不能只记日志——审计数据地基 day one 立起来。
7. **每个 user story 完成必须有可演示成果**，优先级是跑通而非完美。

## 5. AI 助手最常写错的 7 个点（高发错误清单）

| ❌ 错误写法                                | ✅ 正确做法                                                                                          |
| ------------------------------------------ | ---------------------------------------------------------------------------------------------------- |
| 启用 Spring AI 自动 tool 执行              | 必须禁用，Tool 由 `ToolExecutor` 调度（原则四）                                                       |
| 靠"扫描容器里所有 `ChatModel`"区分 Provider | 必须维护 **provider name → `ChatModel` 显式映射**（多 Provider 时 Bean 类型相同，类型扫描必有歧义）   |
| 把 Memory 简化成跟 Session 合并            | `MemoryService` 三层统一门面，内部委托 `SessionManager` + `LongTermMemory`                            |
| 把 Tool 拆成 builtin/skill/mcp 多个模块    | 合并为一个 `oryxos-tool` 模块（共享 `OryxTool` 抽象和 `ToolRegistry`）                                |
| 把 Agent 目录（`AGENT.md`）当成一种 Tool   | Agent 目录是**上下文来源**，归 `ContextLoader`（core 模块）；正文注入 system prompt，不是可执行 Tool  |
| 审计只写日志                               | `tool_invocations`、`llm_calls` 两张表核心阶段就落库                                                  |
| Profile 里明文写 API key                   | 敏感配置 `${ENV_VAR}` 占位，`ConfigLoader` 从环境变量解析，加载时做必填/格式校验                      |

其他易错点：

- **不使用 Java SecurityManager**（JDK 17 起废弃、JDK 21 不可用）。Sandbox 走 `Sandbox` 接口 + `WhitelistSandbox` 应用层白名单（路径/命令/域名）。
- `AGENT.md` 正文、Bootstrap 文件**每次组装 prompt 重新读不缓存**，用户改完即时生效。
- SQLite 上 `hibernate.ddl-auto=update` 只能首次建表用；表结构演进**手动维护建表脚本**，不依赖自动迁移。
- 定时任务用 `ThreadPoolTaskScheduler` + `CronTrigger` **动态注册**，不用编译期写死的 `@Scheduled`。

## 6. 目标工程结构（9 个 Maven 模块）

```
oryxos/
├── oryxos-core          # OryxTool 接口、Session、Profile、ReActLoop、PromptBuilder、
│                        # ToolExecutor、AgentService、AgentScheduler、AgentLoader、ContextLoader
├── oryxos-provider      # 能力一：ProviderService、Function Calling 适配、provider name 显式映射
├── oryxos-memory        # 能力三：MemoryService 门面、LongTermMemoryStore（Markdown/Sqlite/Mem0 三档）、MemoryTools
├── oryxos-tool          # 能力四（三合一）：FileTools/ShellTools/HttpTools/NotifyTools、
│                        # McpClientService、McpToolAdapter、ToolRegistry、Sandbox 接口 + WhitelistSandbox
├── oryxos-channel-cli   # CliChannel（oryxos chat）
├── oryxos-web           # 能力五：WebServer、6 个 ApiController、GlobalExceptionHandler、OpenAPI
├── oryxos-storage       # SQLite、SessionRepository、ToolInvocationRepository、LlmCallRepository
├── oryxos-cli           # Picocli 主入口、12 个子命令、ConfigLoader
└── oryxos-boot          # Spring Boot 启动模块、自动配置、依赖聚合
```

模块间通过接口解耦；扩展新 Channel/Tool 只加新模块不改 core。

**运行时工作区 `.oryxos/`**（由 `oryxos init` 生成）：

```
.oryxos/
├── agents/            # 一个子目录 = 一个 Agent（AGENT.md + 可选 skills/ scripts/ REFERENCE.md）
├── memory/MEMORY.md   # 长期记忆（核心记忆区 + 归档记忆区）
├── mcp_servers.yaml   # MCP server 配置
├── sessions/ logs/    # 会话、日志
├── AGENTS.md SOUL.md USER.md   # Bootstrap 文件（注入 system prompt）
└── oryxos.db          # SQLite
```

## 7. 核心领域概念（别搞混）

- **一个目录 = 一个 Agent**：`.oryxos/agents/<name>/`，含 `AGENT.md`（frontmatter 是 Agent 自己的 profile；正文是任务指令）+ 可选 `skills/*.md` 子指令、`scripts/` 脚本。借 Anthropic Agent Skills 的**形态**，但定义的是 Agent；不做跨 Agent 共享能力库、没有 `use_skill`、没有全局索引。
- **Profile**：Agent 的运行时宿主配置（绑哪个 Provider/模型、能用哪些 Tool、Channel、定时规则）。由 `AgentLoader.deriveProfile` 从 `AGENT.md` frontmatter 派生，**不再手写独立 Profile YAML**。
- **渐进式披露（收在单个 Agent 内部）**：`AGENT.md` 正文进 system prompt；子指令/脚本/参考**不预载**，由 LLM 按正文指引经底座 `read_file`/`shell` 按需取用。不新造工具。
- **ReAct 循环**：接消息 → 组装 Prompt（system + Bootstrap + Memory + 历史 + Tool 列表）→ 调 LLM → 有 Tool 调用则执行回填继续循环，无则返回。默认 `MAX_ITERATIONS=10`。
- **三个触发源一个入口**：CLI、Web Service（"人推"）+ `AgentScheduler`（cron"钟推"）都调同一个 `AgentService.process`，ReAct 不感知来源。钟推 Session 的 channel/user 固定为 `scheduler`。
- **入站 Channel ≠ 出站 Notify**：`ChannelAdapter` 管"消息怎么进来"，`NotifyChannelAdapter`（核心阶段 Webhook 实现 + 内置 `notify` Tool）管"跑完结果送到哪"，方向相反、分开建模。
- **内置 Tool 9 个**：`read_file`/`write_file`/`list_dir`、`shell`、`http_get`/`http_post`、`save_memory`/`recall_memory`、`notify`，全部经 `OryxTool` 抽象注册进 `ToolRegistry`。
- **Memory 分区**：核心记忆区永不截断全量注入；归档记忆区才截断、才参与关键词检索。核心阶段不做自动抽取。

## 8. 实施顺序（5 个 user story，按依赖不按重要性）

```
US-1 对接 LLM → US-2 ReAct 循环 → US-3 Memory ∥ US-4 Tool 体系 → US-5 Web Service
```

- US-1 是地基；US-2 每轮都调 LLM；US-3/US-4 互不依赖可并行；US-5 依赖前四个全部（**排最后是因为依赖，不是不重要**——它是区别于个人助手项目的关键能力）。
- 验收硬条件是 3 个每日自动 Demo：每日天气（光杆 `AGENT.md`）、每日科技日报（`AGENT.md` + `skills/`）、每日 GitHub 日报（`AGENT.md` + `scripts/`），都要支持"钟推"+"人推"双路径。

## 9. 编码与工程约定

- **标识符一律英文 ASCII**：类名、方法名、函数名、变量名、常量名等所有代码标识符必须使用英文（遵循 Java 惯例：类 `UpperCamelCase`、方法/变量 `lowerCamelCase`、常量 `UPPER_SNAKE_CASE`），**禁止中文等非 ASCII 字符**（Java 虽允许 Unicode 标识符编译通过，但本仓库一律不用）。测试方法的中文描述放进 `@DisplayName` 注解（JUnit 5 报告照常显示中文），需求追溯后缀（如 `_FR006`）可保留在方法名尾部。
- 所有 LLM 调用、Tool 调用记结构化日志 + 落审计表（含 token 用量、耗时、成功/失败、错误信息）。
- Web 层统一响应信封 `ApiResponse`（`code`/`message`/`data`/`timestamp`），异常经 `GlobalExceptionHandler` 转标准 JSON。
- 接口先行：新增能力先想"接口是否中立"——签名里不出现某一档实现特有的词（Sandbox 接口不出现"白名单/容器/VM"，MemoryStore 不出现"Markdown/SQLite"）。
- 核心阶段无认证假设内网；CORS 全开方便调试；单条消息上限 32KB；Agent 调用 60 秒超时。
- 敏感信息（API key、webhook URL、凭证）一律环境变量注入，禁止写进代码、Profile、git。
- 脚本类 Agent 的信任边界要在文档/注释中诚实标注：脚本经 `shell` 子进程发网络请求**绕过 `http_get` 域名白名单**，装带脚本的 Agent = 信任其作者。

## 10. 协作模式

- 主体开发走 **Spec-Kit**（constitution → specify → plan → tasks → implement），增量小改动直接手动提示词 + PR。
- 每个 user story 结束必跑一致性检查（spec/plan/代码是否漂移），不省。
- 发现本文件或 constitution 有原则性问题：**停下来与人讨论**，AI 不自行修改原则。
- git commit 标记每个 user story 完成点，保证可回退到稳定态。
