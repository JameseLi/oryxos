---
title: ReAct 循环
description: 自实现的 Agent 核心循环：算法、模块组成与关键设计点
---

# ReAct 循环

## 4. 核心能力二：ReAct 循环

ReAct 循环是 OryxOS 最核心的一段代码。输入一条用户消息，输出 Agent 的最终响应，中间可能调用若干次 LLM 和若干次 Tool。

### 4.1 ReAct loop 算法

ReAct 是 **Reason** 加 **Act** 的简称。算法步骤：

1. 接到用户消息追加到 Session 对话历史
2. 组装 Prompt（system prompt 加 Bootstrap 加 Skill 加 Memory 加对话历史加可用 Tool 列表）
3. 调用 LLM Provider 获取响应
4. 如果响应**没有** Tool 调用，返回最终响应
5. 如果**有** Tool 调用，OryxOS 执行 Tool 并把结果作为 tool 消息追加到对话历史
6. 回到组装 Prompt 步骤继续循环
7. 达到最大迭代次数（默认 10 次）强制结束


### 4.2 模块组成

**`ReActLoop` 模块。** Agent 的核心循环引擎。输入 Session 和用户消息，输出最终响应。内部维护当前迭代次数，调用 `ProviderService` 调 LLM，调用 `ToolExecutor` 执行 Tool，把每轮的响应和工具结果累积到 Session 对话历史。核心循环逻辑精简，约数十行 Java，不依赖 Spring AI 的 Agent 抽象，让实现者完整掌握 Agent 的工作机制。

**`PromptBuilder` 模块。** 组装每轮 LLM 调用的 Prompt。按四部分顺序拼接：

1. system prompt（`AGENT.md` 正文（这个 Agent 的指令）加 Bootstrap 文件，由 `ContextLoader` 提供；末尾附当前日期时间——LLM 自己不知道今天几号，定时场景的"今天"全靠这一行）
2. Memory 注入（会话历史加长期记忆，由 `MemoryService` 提供）
3. 对话历史（按 `maxHistoryTurns` 截断后的 Session messages）
4. 当前 Profile 可用的 Tool 列表（按 Function Calling 格式）

**`ToolExecutor` 模块。** 执行 LLM 返回的 Tool 调用请求。从 `ToolRegistry` 找到对应 Tool，做 Sandbox 检查，执行 Tool，把结果包装成 `ToolResult` 返回给 ReAct 循环，并写入 `tool_invocations` 表。失败时按可重试策略返回错误信息。

**`AgentService` 模块。** 三种触发源共用的统一入口，也是一次处理的编排者：`process(Session, String)` 内部依次做——把当前 Profile 放进 `ProfileContext`（ThreadLocal，虚拟线程下每个请求天然独立）、调 `ReActLoop.run` 跑完循环、持久化 Session、`finally` 里清掉 `ProfileContext`。`ProfileContext` 解决的是"工具执行时怎么知道当前是哪个 Agent"：`OryxTool.execute` 的签名不带 Profile，`NotifyTools` 取 `notify_channels`、按 Profile 过滤工具子集这类需求，都从 `ProfileContext` 读，不改工具接口。

### 4.3 关键设计点

**`MAX_ITERATIONS` 限制。** 核心阶段默认 10 次，防止 Agent 陷入 Tool 调用死循环，可在 Profile 里覆盖。

**消息累积。** 每次迭代都把 LLM 响应和 Tool 结果追加到 Session 的 messages 列表。Session 的对话历史包含完整的 LLM 调用链和 Tool 调用链，对外可查可审计。

**上下文长度管理。** 核心阶段策略简单：保留 system prompt 和最近 N 轮对话，超出部分丢弃，N 由 Profile 配置默认 20 轮。扩展阶段引入总结压缩。

**核心阶段不做：** Tool 调用并行（一次响应里多个 Tool 调用按顺序执行）、Agent 间任务委托、流式响应。这些放扩展阶段。

---
