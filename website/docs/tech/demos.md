---
title: 验收 Demo 与实施节奏
description: 每日天气、每日科技日报、每日 GitHub 日报三个验收 Demo，4 周节奏与性能考虑
---

# 验收 Demo 与实施节奏

## 12. 关键流程

早期按"一个 Demo 验证一个能力"拆过五个流程，但真实场景里能力从来不是分开跑的。改成三个**每日自动运行**的端到端流程，横向串起全部五大核心能力加定时任务这个第三触发源——而且它们正好是第 11 章"一个目录 = 一个 Agent"的具体产出，把一个 Agent 目录的三种丰富度各演到一个：天气 = 光杆 `AGENT.md`、科技日报 = `AGENT.md` + `skills/` 子指令、GitHub 日报 = `AGENT.md` + `scripts/` 脚本。底座和"定义 Agent"两部分在这里合到一起跑通。

### 12.1 Demo 一：每日天气（光杆 AGENT.md）

**场景：** 每天早上 8 点，Agent 自动查天气、生成穿搭建议，推送到企业 IM 群，不需要人工发起。

1. `AgentScheduler` 按 `AGENT.md` frontmatter 里 `schedules` 声明的 cron 表达式到点触发，生成一条消息，调 `AgentService.process`——跟 `CliChannel`/`ApiController` 调用的是同一个方法，`ReActLoop` 不感知这次触发是"钟推"
2. `ReActLoop` 第一轮，`PromptBuilder` 通过 `ContextLoader` 组装 system prompt（`AGENT.md` 正文即这个 Agent 的指令 + Bootstrap）
3. `ProviderService` 调 DeepSeek，返回包含 `http_get` 的 Tool 调用
4. `ToolExecutor` 执行，`HttpTools` 调用 `Sandbox.enforce(...)` 检查 URL 通过，拿到天气 JSON，并写 `tool_invocations`
5. 结果追加到 Session 进入第二轮，DeepSeek 看到天气生成穿搭建议，决定调 `notify(content="...")` 推送——地址是 frontmatter 的 `notify_channels` 配置好的
6. `ToolExecutor` 再次执行，`NotifyTools` 委托给 `WebhookNotifyAdapter`，发送前同样先过 `Sandbox.enforce(...)` 域名白名单校验，推送成功后写第二条 `tool_invocations`
7. 无更多 Tool 调用，循环结束，最终响应留在这次自动触发的 Session 里

**验收要点：** 全程不需要人工触发；两次涉外调用都过 Sandbox 白名单且都有审计记录；`GET /api/v1/sessions/{id}` 查得到完整对话；同一个 Agent 也能通过 `oryxos chat` 或 `POST /agents/{name}/invoke` 手动补跑一次，验证"人推"和"钟推"复用同一条链路。光杆 `AGENT.md`、不带子指令 / 脚本。

涉及能力一（Provider）+ 能力二（ReAct）+ 能力四（内置 HTTP Tool + `NotifyTools` + Sandbox）+ 定时任务（`AgentScheduler`）+ 能力五（Session 查询兜底）。

### 12.2 Demo 二：每日科技日报（AGENT.md + skills/ 子指令）

**场景：** 每天早上 9 点，Agent 自动汇总当日科技新闻并推送，日报内容会体现用户之前提过的关注方向。业务方全程不写 Java 代码。

1. 业务方**写一个 Agent 目录** `.oryxos/agents/daily-tech-digest/`：`AGENT.md`（frontmatter：`identity` + `tools`（含 `http_get`、`read_file`、`notify`）+ 每天 09:00 的 `schedules` + `notify_channels`；正文写"编日报、组稿规范去读 `skills/digest-format.md`"）+ `skills/digest-format.md`（较长的组稿排版规范）；需要新闻聚合 MCP 就在 `mcp_servers.yaml` 配一条
2. 用户此前说过"更关注 AI 和芯片方向"，DeepSeek 调 `save_memory` 写入 `MEMORY.md` 归档区
3. 到点，`AgentScheduler` 触发 `AgentService.process`，`PromptBuilder` 往 system prompt 注入 `AGENT.md` 正文 + `MEMORY.md`——组稿规范那段较长的子指令**不预载**
4. LLM 按正文指引，调 **`read_file("skills/digest-format.md")`** 取回组稿规范作为工具结果进上下文——子指令只在此刻才占上下文（一个 Agent 内部的渐进式披露，用底座既有 `read_file`）
5. LLM 按规范调新闻工具（`http_get` 或新闻 MCP，`McpToolAdapter` 转发）拉当日科技新闻；因为看到记忆里的偏好，组稿时自然侧重 AI 和芯片方向——OryxOS 不解析任务步骤
6. LLM 调内置 `notify(content="...")` 推送，`ToolExecutor` 写 `tool_invocations`

**验收要点：** 业务方只写一个 Agent 目录（两份 markdown），零 Java；`tool_invocations` 里有 `read_file("skills/digest-format.md")`（子指令按需加载生效）；`GET /api/v1/agents` 查得到 `daily-tech-digest`；日报体现记忆偏好；同一个 Agent 可被人工触发验证。

涉及 Agent 目录子指令（`read_file` 按需读）+ 能力四方式二（MCP）+ 内置 `NotifyTools` + 能力三（Memory）+ 定时任务。

### 12.3 Demo 三：每日 GitHub 日报（AGENT.md + scripts/ 脚本）

**场景：** 每天早上 9:30，Agent 自动抓 GitHub 今日与本月热门项目、专挑 AI 相关做总结并推送。这个 Demo 演一个 Agent 目录里**捆绑一个脚本**：Agent 跑脚本拿确定性数据。

1. 业务方**写一个带脚本的 Agent 目录** `.oryxos/agents/github-daily/`：`AGENT.md`（frontmatter：`tools:[shell, notify]` + 每天 09:30 的 `schedules`；正文"跑脚本 → 组三段日报 → notify"）+ `scripts/github_trending.py`（免 token 用 GitHub Search API 按日期区间 + stars 近似 trending）
2. 到点触发，`PromptBuilder` 注入 `AGENT.md` 正文
3. LLM 按正文调 `shell` 跑 `python scripts/github_trending.py`——`Sandbox.enforce` 校验 `shell.allowed_commands` 放行 `python`、`file.allowed_paths` 限定到该 Agent 的 `scripts/` 目录；脚本自己请求 `api.github.com` 拿数据、返回 JSON。**脚本产出的 JSON 进上下文、脚本代码不进**，写 `tool_invocations`
4. LLM 用 JSON + 记忆偏好组织三段日报（今日 / 本月 / AI 重点），调 `notify` 推送

> **脚本的信任边界（呼应 11.1 与宪法原则四）**：第 3 步里脚本经 `python` 子进程自己发网络请求，**绕过了 `http_get` 的域名白名单**（白名单只管内置 `http_get` 工具）。所以**装一个带脚本的 Agent = 信任这个 Agent 的作者**（与 Anthropic 一致）。核心阶段沙箱对脚本只做"解释器 + 脚本目录"两道白名单，容器 / 网络隔离留扩展阶段。做 Agent OS 要对这条诚实：能跑第三方 Agent 很强，但信任从"底座"挪到了"Agent 作者"。

**验收要点：** `tool_invocations` 里有 `shell` 跑脚本；脚本产出进上下文、代码不进；AI 段体现记忆偏好；改一下 `AGENT.md` 正文即时生效。

涉及 Agent 目录脚本（`shell` 跑捆绑脚本 + 沙箱信任边界）+ 能力二（ReAct）+ 能力三（Memory）+ 定时任务 + 内置 `NotifyTools`。

---

---

## 13. 实施节奏（4 周）

实施按需求文档第 11 章的 4 周节奏组织，每周 3 小时，合计 12 小时。每周对应一组核心能力，每周末有可演示成果。

### 第一周（3 小时）：核心能力一 + 能力二（对接 LLM + ReAct 循环）

- 搭 Maven 多模块骨架（9 个模块）、`oryxos init`、Profile YAML 解析
- `ProviderService` 包装 Spring AI Alibaba（先跑通 DeepSeek 或 Kimi，含 provider name 映射）
- `ReActLoop` + `PromptBuilder` + `ToolExecutor`、一个内置 HTTP Tool、`CliChannel`
- Session 内存版（第四周加 SQLite）

**可演示：** `oryxos chat` 多轮对话，Agent 调 HTTP Tool 完成简单任务。

### 第二周（3 小时）：核心能力三 + 能力四（Memory + Tool）

- `MemoryService` 三层门面 + `LongTermMemory`（`MEMORY.md` 读写）、`save_memory` + `recall_memory`
- `PromptBuilder` 加 Memory 注入
- 文件 Tool + Shell Tool（`Sandbox` 接口 + `WhitelistSandbox` 应用层白名单）、`McpClientService`（连接外部 MCP server）
- `ContextLoader` 加载 `AGENT.md` 正文（及 Agent 目录里的子指令按需读）

**可演示：** Agent 记住偏好并后续用到，调本地文件读写、调外部 MCP server 完成跨工具任务。

### 第三周（3 小时）：核心能力五 Web Service

- `WebServer`（Spring MVC + virtual thread）、六个 `ApiController` 的核心 10 个端点
- `GlobalExceptionHandler`、`ConfigLoader`（配置与密钥加载）

**可演示：** 外部系统通过 10 个 REST 端点完整调用 OryxOS。

### 第四周（3 小时）：多 Agent 演示 + 工程化收尾

- 多 Agent 演示（两个不同 Profile 的 Agent 在同一实例并存）
- Session 持久化到 SQLite（含 `tool_invocations`、`llm_calls` 写入）
- `ContextLoader` 的 Bootstrap 加载、Picocli 12 个命令完整、结构化日志
- `AgentScheduler` 第三触发源（`ThreadPoolTaskScheduler` + `CronTrigger`，Profile `schedules` 字段驱动）
- 项目主页（VitePress 或类似）

**可演示：** 多 Agent 并存可用，CLI 体验流畅，Bootstrap 影响 Agent 行为，Session 跨重启恢复，定时任务到点自动触发，主页可访问。

---

核心阶段结束后 OryxOS 1.0 是一个可演示的最小完整 Agent OS 运行时内核，五大核心能力全部跑通。之后转入开源社区维护，扩展功能（多 Channel、Memory 向量检索、情景记忆、Skill 体系、MCP Server 暴露、Tool Policy、完整 Sandbox、Web Service 剩余端点、Web 仪表板、SSO 和多租户、完整审计、集群高可用）以及让 OryxOS 成为真正企业级 Agent OS 的治理层由社区陆续推进。

---

---

## 14. 性能和可扩展性考虑

性能目标在需求文档第 8 章已定义，这里说明怎么达到。

**Java 21 virtual thread 撑高并发。** 每个 Agent 是内存里的 Profile 对象加 Session 列表占用极少，virtual thread 让每个并发请求跑在独立虚拟线程，OS 线程数维持几十个就够支撑几千并发，LLM 调用 IO 阻塞时 virtual thread 自动让出 OS 线程。

**1000 个并发 Session 内存可控。** 1000 个 Session 平均 50KB 共 50MB 没问题。SQLite 写入主要由 Session 追加消息和审计表写入触发，核心阶段每次都写，压测发现瓶颈再优化成批量落盘。

**Memory 文件 IO。** 每次组装 prompt 读一次 `MEMORY.md`，文件几 KB 到几十 KB 每次读 1 到 2ms，1000 并发可接受。扩展阶段加 cache 加文件 watch。

**启动时间。** Spring Boot 在 JDK 21 下启动 2 到 4 秒，对常驻服务没问题，对 CLI 工具太慢。核心阶段 CLI 命令分两类，不需要 Spring 的直接用标准 API 操作文件，需要的才启动 Spring。扩展阶段用 GraalVM Native Image 把启动降到 100ms 以下。

---
