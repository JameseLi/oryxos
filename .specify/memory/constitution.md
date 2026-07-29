<!--
Sync Impact Report
- Version change: (unfilled template) → 1.0.0
- Bump rationale: initial ratification. First formal adoption of the constitution; all seven
  principles newly established, codified from AGENTS.md §4「非协商原则」(committed 2026-07-27).
- Principles established (7):
  I. 固定技术栈与九模块结构
  II. 核心阶段只做运行时内核
  III. 自实现 ReAct 循环
  IV. Spring AI 只用一半
  V. Plugin Tool 三档接入·零代码优先
  VI. 审计数据 Day One 落库
  VII. 每个 User Story 必须可演示
- Added sections: 安全与工程约束；开发流程与质量门禁；Governance
- Removed sections: none
- Templates & dependent artifacts checked (no edits required):
  - ✅ .specify/templates/plan-template.md — Constitution Check gate 由 /speckit.plan 运行时
    依据本章程动态生成，模板保持通用占位即可
  - ✅ .specify/templates/spec-template.md — 通用模板，无与本章程原则绑定的强制章节
  - ✅ .specify/templates/tasks-template.md — 通用模板，无硬编码的原则任务类型
  - ✅ .pi/prompts/speckit.*.md — 未发现过时的 agent 专属引用（"claude" 仅为集成名列表示例）
  - ✅ README.md / AGENTS.md — 与本章程一致，无需改动
- Deferred TODOs: none
-->

# OryxOS Constitution

## Core Principles

### I. 固定技术栈与九模块结构

- 必须使用 JDK 21+（virtual thread 用于高并发，禁止低版本语法/API）与 Spring Boot 3.x
  单体应用，单二进制 fat JAR 部署。
- Maven 模块固定为 9 个：oryxos-core、oryxos-provider、oryxos-memory、oryxos-tool、
  oryxos-channel-cli、oryxos-web、oryxos-storage、oryxos-cli、oryxos-boot——不多不少。
  模块间通过接口解耦；扩展新 Channel/Tool 只加新模块，不改 core。
- 核心阶段禁止引入 GraalVM、WebFlux、响应式编程。
- 理由：运行时底座的价值在于稳定可预期；固定的模块边界是人与 AI 并行开发的契约。

### II. 核心阶段只做运行时内核

- 交付范围限定为五大核心能力（对接 LLM、ReAct 循环、Memory、Tool 体系、Web Service）
  及支撑模块（CLI、Scheduler、Storage）。
- 多租户、SSO、RBAC、完整审计查询、Tool Policy、向量检索、SSE 流式、WebSocket、
  认证限流一律不做，只允许预留接口扩展点。
- 理由：核心阶段是地基，治理是终局；范围蔓延会直接拖垮核心阶段节奏。

### III. 自实现 ReAct 循环

- Agent 核心循环（接消息 → 组装 Prompt → 调 LLM → 有 Tool 调用则执行回填继续循环，
  无则返回）必须由 OryxOS 自己实现，默认 MAX_ITERATIONS=10。
- 禁止依赖 Spring AI 的 Agent 抽象。
- 理由：循环行为（终止条件、迭代上限、回填格式、审计埋点）必须完全可控，
  这是运行时内核的心脏。

### IV. Spring AI 只用一半（NON-NEGOTIABLE）

- 只用：Provider 抽象、各家 LLM 协议转换、`@Tool` 注解的 JSON Schema 生成。
- 禁用：Spring AI 的自动 tool 执行。Tool 调度与执行完全由 `ReActLoop` + `ToolExecutor`
  控制，否则 tool 会被调用两次。
- 多 Provider 时必须维护 provider name → `ChatModel` 显式映射；禁止靠"扫描容器里
  所有 `ChatModel`"区分（Bean 类型相同，类型扫描必有歧义）。
- 理由：这是最高发的集成错误，直接破坏调度与审计语义，必须写死在原则里。

### V. Plugin Tool 三档接入·零代码优先

- 三档并存：零代码（Agent 目录 + 复用社区 MCP server，⭐ 主推）、轻代码（业务方自写
  任意语言 MCP server）、重代码（`@Tool` 注解 Java Spring Bean）。
- Tool 体系合并为单个 oryxos-tool 模块，共享 `OryxTool` 抽象与 `ToolRegistry`；
  禁止拆成 builtin/skill/mcp 多个模块。
- Agent 目录（`AGENT.md`）是上下文来源，归 `ContextLoader`；正文注入 system prompt，
  严禁当成可执行 Tool。
- 内置 9 个 Tool（`read_file`/`write_file`/`list_dir`、`shell`、`http_get`/`http_post`、
  `save_memory`/`recall_memory`、`notify`）全部经 `OryxTool` 抽象注册进 `ToolRegistry`。
- 理由：三档接入是生态策略——"写一个目录就能跑"是 OryxOS 与框架的本质区别。

### VI. 审计数据 Day One 落库（NON-NEGOTIABLE）

- 持久化固定为 SQLite + Spring Data JPA（`.oryxos/oryxos.db`）+ `MEMORY.md` 长期记忆；
  核心阶段不引入向量库依赖。
- `tool_invocations`、`llm_calls` 两张审计表核心阶段就必须写入落库（含 token 用量、
  耗时、成功/失败、错误信息），可只写不提供查询接口；禁止只记日志。
- `MemoryService` 是三层统一门面，内部委托 `SessionManager` + `LongTermMemory`；
  禁止把 Memory 简化成跟 Session 合并。
- 理由：企业级治理是终局，审计数据地基必须 day one 立起来——历史数据缺失无法事后补齐。

### VII. 每个 User Story 必须可演示

- 每个 user story 完成必须产出可演示成果，优先级是跑通而非完美。
- 核心阶段验收硬条件是 3 个每日自动 Demo（每日天气、每日科技日报、每日 GitHub 日报），
  均须支持"钟推 + 人推"双路径。
- 实施顺序按依赖：US-1 对接 LLM → US-2 ReAct 循环 → US-3 Memory ∥ US-4 Tool 体系 →
  US-5 Web Service。
- 理由：可演示成果是防止"架构空转"的唯一手段；每个 story 结束 git commit 标记，
  保证可回退到稳定态。

## 安全与工程约束

- **密钥管理**：API key、webhook URL、凭证等敏感信息一律 `${ENV_VAR}` 占位、环境变量
  注入，禁止写进代码、Profile、git；`ConfigLoader` 加载时做必填/格式校验。
- **沙箱**：不使用 Java SecurityManager（JDK 17 起废弃、JDK 21 不可用）；Sandbox 走
  接口抽象 + `WhitelistSandbox` 应用层白名单（路径/命令/域名）。脚本类 Agent 的信任
  边界必须在文档/注释中诚实标注：脚本经 `shell` 子进程发网络请求会绕过 `http_get`
  域名白名单，装带脚本的 Agent = 信任其作者。
- **配置热读**：`AGENT.md` 正文、Bootstrap 文件每次组装 prompt 重新读取不缓存，
  保证用户改完即时生效。
- **Schema 演进**：SQLite 上 `hibernate.ddl-auto=update` 只用于首次建表；表结构演进
  手动维护建表脚本，不依赖自动迁移。
- **定时任务**：用 `ThreadPoolTaskScheduler` + `CronTrigger` 动态注册，禁止编译期
  写死的 `@Scheduled`。
- **接口中立**：新增能力先审查接口是否中立——签名中不出现某一档实现特有的词
  （Sandbox 不出现"白名单/容器/VM"，MemoryStore 不出现"Markdown/SQLite"）。
- **Web 约定**：统一响应信封 `ApiResponse`（code/message/data/timestamp）；异常经
  `GlobalExceptionHandler` 转标准 JSON；核心阶段无认证（假设内网）、CORS 全开、
  单条消息上限 32KB、Agent 调用 60 秒超时。
- **可观测**：所有 LLM 调用、Tool 调用记结构化日志（Logback + SLF4J）并落审计表。

## 开发流程与质量门禁

- **文档即单一事实来源**：`docs/` 四份文档（业界调研/需求分析/技术方案/AI 编程指南）
  是架构决策最终依据；代码行为与文档冲突时以文档为准。要改文档级决策必须先停下来
  与人讨论，AI 不得擅自修改架构决策。
- **Spec-Kit 主流程**：主体开发走 constitution → specify → plan → tasks → implement；
  增量小改动可直接手动提示词 + PR。
- **一致性检查**：每个 user story 结束必跑一致性检查（spec/plan/代码是否漂移），不省略。
- **回退保障**：git commit 标记每个 user story 完成点，任何时刻可回退到稳定态。
- **原则问题升级**：发现本章程或 AGENTS.md 有原则性问题时，停下来与人讨论，
  不自行修改原则。

## Governance

- 本章程优先于其他一切开发惯例；`AGENTS.md` 是面向 AI 编码助手的运行时工作指南，
  二者冲突时以本章程为准，并同步修订 `AGENTS.md`。
- 修订程序：提出修订动议 → 与人讨论确认 → 更新本文件（含顶部 Sync Impact Report）→
  传播至 plan/spec/tasks 模板与命令文件 → git commit 标记修订点。
- 版本语义：MAJOR = 原则移除或重新定义等向后不兼容变更；MINOR = 新增原则或实质性
  扩充指导；PATCH = 措辞澄清、错别字等非语义修订。
- 合规审查：每次 `/speckit.plan` 的 Constitution Check 必须逐条对照本章程出具门禁
  结论；每个 user story 验收时复查合规。违反任一原则的代码视为错误，必须返工，
  不允许"先合后改"。

**Version**: 1.0.0 | **Ratified**: 2026-07-29 | **Last Amended**: 2026-07-29
