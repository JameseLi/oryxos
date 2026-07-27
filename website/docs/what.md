---
title: OryxOS 是什么
description: Java 原生的企业级 Agent OS——私有部署、完全可审计的 Agent 统一底座
---

# OryxOS 是什么

**OryxOS** 是基于 Java 实现的、面向企业场景的 **Agent OS**（Agent 操作系统）。它装在企业自己的 K8s 或服务器上，作为统一底座，在底座上跑各种业务 Agent（运维助手、客服助手、HR 助手、销售助手、知识管理助手等），共享一套渠道接入、模型路由、工具调用、记忆系统、沙箱执行能力。

> 数据完全留在企业自己的基础设施，不锁任何云生态。

## Agent OS ≠ Agent Runtime

理解 OryxOS 的第一把钥匙，是分清两个容易混淆的概念：

- **Agent Runtime**：让**单个** Agent 跑起来的执行内核——LLM 调用、工具执行、上下文管理、循环控制
- **Agent OS**：内核包含一个 runtime，但在 runtime 之上还要管**一群** Agent 的生命周期、统一的对外对内接入、统一记忆、治理审计

借操作系统类比：runtime 像单个进程的执行环境，Agent OS 像管理一群进程、调度资源、提供共享服务和治理的那层。一句话：**runtime 让一个 Agent 跑起来，Agent OS 让一群 Agent 在企业里被管起来**。

## 五大核心能力

| 能力 | 一句话说明 |
| --- | --- |
| 🔌 对接 LLM | Provider 抽象对接主流大模型，Agent 不感知具体是哪家，运行时切换无 lock-in |
| 🧠 ReAct 循环 | Agent 大脑：LLM 思考 → 调工具 → 看结果 → 再决策，多步任务自主完成 |
| 💾 Memory 三层记忆 | 会话记忆 + 长期记忆（MEMORY.md），跨对话记住用户偏好与项目背景 |
| 🛠️ Tool 体系 | 9 个内置 Tool + Plugin Tool 三档接入（零代码 / 轻代码 / 重代码） |
| 🌐 Web Service | 完整 REST API 对外暴露所有能力，业务系统集成的唯一通道 |

详见 [五大核心能力](/docs/demand/capabilities)。

## 交付分两段

1. **核心阶段**：把 Agent OS 的**运行时内核**用 Java 做扎实，能力上对齐业界开源 Agent OS 的基础层——这是当前仓库正在做的事
2. **扩展阶段**：真正的差异化治理层（多租户、SSO、完整审计、Tool 治理）在核心内核之上陆续补齐

**核心阶段是地基，企业级治理是终局。**

## 四个设计目标

| 目标 | 说明 |
| --- | --- |
| **统一** | 企业内多个业务 Agent 共享同一套底座，上一个新 Agent 写一个目录就能跑 |
| **私有** | 数据完全留在企业自己的基础设施，OryxOS 本身不收集任何企业数据 |
| **易接入** | 标准 Spring Boot 工程，跟企业现有 ERP / CRM / CMDB / SSO / 监控直接对接 |
| **可观测** | 结构化 JSON 日志、健康检查、标准指标，适配企业现有监控告警体系 |

## 文档地图

| 板块 | 回答 | 从这里开始 |
| --- | --- | --- |
| 业界调研 | Why——为什么做 OryxOS | [什么是 Agent OS](/docs/research/agent-os) |
| 需求分析 | What——做什么、做成什么样 | [五大核心能力](/docs/demand/capabilities) |
| 技术方案 | How——怎么设计 | [关键技术决策](/docs/tech/decisions) |
| AI 编程指南 | 怎么造——AI 协作实施 | [实施总览](/docs/ai-guide/overview) |

下一步：[快速开始](/docs/quick-start)，三分钟跑起第一个 Agent。
