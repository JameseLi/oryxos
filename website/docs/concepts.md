---
title: 核心概念
description: 一个目录 = 一个 Agent、Profile、三个触发源一个入口、入站 Channel ≠ 出站 Notify
---

# 核心概念

## 最重要的几件事

### 一个目录 = 一个 Agent

定义一个 Agent = 写一个目录 `.oryxos/agents/<name>/`：

- `AGENT.md`：**frontmatter** 是这个 Agent 自己的 profile（`name` / `provider` / `tools` / `schedules` / `notify_channels`……）；**正文**是任务指令，注入 system prompt
- 可选 `skills/*.md` 子指令、`scripts/` 脚本、`REFERENCE.md` 参考

形态借鉴 Anthropic Agent Skills，但定义的是 **Agent**：不做跨 Agent 共享能力库、没有 `use_skill`、没有全局索引。每个 Agent 独立自足，只调用底座的系统基础能力。详见 [定义一个 Agent](/docs/tech/define-agent)。

### 渐进式披露

`AGENT.md` 正文进 system prompt；目录里的子指令 / 脚本 / 参考**不预载**，由 LLM 按正文指引经底座既有的 `read_file` / `shell` 按需取用——子指令只在此刻才占上下文，脚本产出进上下文、代码不进。不新造工具。

### Profile

Agent 的运行时宿主配置：绑哪个 Provider/模型、能用哪些 Tool、Channel、定时规则。由 `AgentLoader.deriveProfile` 从 `AGENT.md` frontmatter **派生**，不再手写独立 Profile YAML。多个 Agent 可以在同一个 OryxOS 实例并存——这是"OS"在核心阶段的最小体现。

### 三个触发源一个入口

CLI、Web Service（"人推"）+ `AgentScheduler`（cron"钟推"）都调同一个 `AgentService.process`，ReAct 循环不感知消息从哪个入口来。钟推 Session 的 channel/user 固定为 `scheduler`，同一个 Profile 的历次定时触发复用同一个 Session。

### 入站 Channel ≠ 出站 Notify

方向相反、分开建模：

- `ChannelAdapter`（入站）：消息怎么**进来**——核心阶段内置 CLI，IM 渠道放扩展
- `NotifyChannelAdapter`（出站）：跑完结果送到**哪**——核心阶段是 Webhook 实现 + 内置 `notify` Tool，企业微信/飞书/钉钉群机器人都有 webhook 地址

同一个企业微信群，可能既是某个 Agent 的入站 Channel、又是另一个 Agent 的出站通知目标。

### 审计 day one 落库

`tool_invocations` 和 `llm_calls` 两张审计表**核心阶段就写入**（可只写不提供查询接口）——可审计的数据地基 day one 立起来，避免后期从日志反解析返工。

## 术语表

| 术语 | 定义 |
| --- | --- |
| **Agent（智能体）** | 一个具象的业务智能体，"这个 Agent 是干什么的"由 Agent 目录定义（做什么、什么时候该做），"怎么跑起来"由派生的 Profile 绑定（用哪个模型、能用哪些工具、要不要定时） |
| **Profile（配置）** | Agent 的运行时宿主配置，是 Agent OS 内核层的能力——决定一个 Agent"怎么跑"：绑定的 LLM Provider、可用 Tool 列表、绑定 Channel、Tool Policy、定时规则 |
| **Provider（供应商）** | LLM API 服务的抽象，实现统一接口让 Agent 不感知具体调的是哪家模型 |
| **ReAct 循环** | Agent 的核心工作机制，Reason + Act。LLM 思考是否调用工具，调用后看结果，再决定下一步，直到给出最终响应 |
| **Tool（工具）** | Agent 可以调用的外部能力。内置 Tool 是 OryxOS 自带的（文件、Shell、HTTP、记忆、通知推送）；Plugin Tool 是业务方自己扩展的 |
| **Memory（记忆）** | Agent 跨对话保留的状态，分三层：会话记忆、长期记忆（MEMORY.md）、情景记忆（扩展阶段） |
| **Channel（渠道）** | Agent 对外接入的消息入口，包括 CLI、企业微信、飞书、钉钉、Slack 等 |
| **Web Service** | OryxOS 对外暴露的完整 REST API，是业务系统集成 OryxOS 的唯一通道 |
| **Session（会话）** | 用户和 Agent 一次对话的上下文容器，包含对话历史、当前上下文、临时变量 |
| **Sandbox（沙箱）** | 工具执行的隔离环境。核心阶段是应用层白名单校验，扩展阶段补容器级隔离 |
| **Tool Policy（工具策略）** | 控制 Agent 可用工具的允许或拒绝规则，在 Profile 级别配置（扩展阶段） |
| **Bootstrap（引导文件）** | 加载到系统提示词中的上下文文件：AGENTS.md（项目级 agent 行为说明）、SOUL.md（agent 人格定义）、USER.md（用户偏好） |
| **Workspace（工作区）** | OryxOS 实例的工作目录，默认是 `.oryxos/`，包含配置、Bootstrap 文件、记忆、会话、Agent 目录 |

## 五大能力之间的关系

五大能力不是平行模块：**Provider、Memory、Tool 三个能力供养 ReAct 循环这个引擎，引擎跑出的能力通过 CLI、Web Service、定时任务三个入口对外提供**。详见 [整体架构](/docs/tech/architecture)。
