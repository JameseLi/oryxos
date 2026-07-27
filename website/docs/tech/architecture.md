---
title: 整体架构
description: 接入层、引擎层、能力层、基础层四层视图与五大能力的协作关系
---

# 整体架构

![OryxOS 整体架构](/images/oryxos-architecture.svg)


## 2. 整体架构

OryxOS 的整体架构按"五大核心能力加支撑模块"组织。五大核心能力是 Agent OS 运行时内核的主体，支撑模块是让这些能力跑起来需要的工程基础设施。

整体上，OryxOS 是一个 Spring Boot 单体应用，对外有两个人工触发入口，加一个内部自动触发入口：

1. **CLI Channel** 用于本地交互和调试，**Web Service** 用于业务系统通过 REST API 集成，这两个是"人推"；**`AgentScheduler`**（8.5）按 cron 到点自动发起调用，是"钟推"。三个入口的消息最终都汇入同一个引擎，`AgentService` 作为统一入口不区分消息从哪个入口来。
2. 引擎是 **ReAct 循环**，它是整个系统的中枢，负责把"接收消息、组装 Prompt、调用 LLM、执行 Tool、回填结果、继续推理"这条链路驱动起来。引擎自己不直接干活，而是调度三块能力：
   1. **Provider** 负责 LLM 调用并向外对接各家大模型 API
   2. **Memory** 负责会话和长期记忆并读写本地文件
   3. **Tool** 负责工具执行并通过 MCP Client 向外对接外部 MCP server

这三块能力之下是存储层，Session 和审计数据落 SQLite，Profile、Bootstrap、Memory、Skill 这些用户可维护的数据落文件系统。

这个架构有两个要点：

1. 所有能力收敛到一个引擎、一套存储、一个进程内，符合"单二进制、装好就跑"的定位，外部依赖（LLM 厂商 API、外部 MCP server）都在应用边界之外，OryxOS 自身不绑定任何一家。
2. 引擎和能力之间、能力和外部之间都通过抽象接口解耦，这让扩展阶段加新 Channel、新 Provider、新 Tool 时只需在边缘扩展，不动核心引擎。


### 2.1 分层视图

从上到下分四层：

1. **接入层**（CLI Channel、Web Service 的 REST API、`AgentScheduler` 定时触发），负责消息进出。
2. **引擎层**（`ReActLoop`、`PromptBuilder`、`ToolExecutor`），是 Agent 的大脑。
3. **能力层**（Provider、Memory、Tool），给引擎提供 LLM 调用、上下文、执行能力。
4. **基础层**（Profile/Bootstrap/Skill 加载、Session 存储、SQLite、配置与密钥加载），是工程地基。

### 2.2 五大能力之间的关系

五大能力不是平行的功能模块，它们之间有明确的协作关系：

- **ReAct 循环（能力二）** 是引擎，负责把"用户消息到 LLM 思考到 Tool 执行到结果回填到继续"这件事跑起来。
- **Provider（能力一）** 给 ReAct 循环提供 LLM 调用能力，每轮思考都要调一次。
- **Memory（能力三）** 给 ReAct 循环提供上下文，每轮组装 prompt 时把会话历史和长期记忆注入进去。
- **Tool（能力四）** 给 ReAct 循环提供执行能力，LLM 决定调哪个 Tool 后由 ReAct 循环负责执行。
- **Web Service（能力五）** 是这套内部能力的对外出口，把前四个能力包装成 REST API 供业务系统集成，它不参与 Agent 内部循环，而是循环的触发入口和结果出口之一（另外两个入口是 CLI Channel 和 `AgentScheduler` 定时触发，见 8.5）。

> **简化成一句话**：Provider、Memory、Tool 三个能力供养 ReAct 循环这个引擎，引擎跑出的能力通过 CLI、Web Service、定时任务三个入口对外提供。

---
