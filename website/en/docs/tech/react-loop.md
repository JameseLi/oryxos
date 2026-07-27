---
title: ReAct Loop
description: The self-implemented agent core loop — algorithm, module composition, and key design points
---

# ReAct Loop

## 4. Capability 2: The ReAct Loop

The ReAct loop is OryxOS's most critical piece of code. Input: one user message. Output: the agent's final response. In between, it may call the LLM several times and tools several times.

### 4.1 The ReAct Algorithm

ReAct is short for **Reason** + **Act**. Algorithm steps:

1. Append the incoming user message to the session history
2. Assemble the prompt (system prompt + Bootstrap + Skill + Memory + conversation history + available tool list)
3. Call the LLM provider for a response
4. If the response has **no** tool call, return it as the final response
5. If it **has** tool calls, OryxOS executes the tools and appends the results as tool messages to the history
6. Go back to the prompt-assembly step and continue looping
7. Force termination at the max iteration count (default 10)

### 4.2 Module Composition

**The `ReActLoop` module.** The agent's core loop engine. Input: a session and a user message; output: the final response. Internally it tracks the current iteration count, calls `ProviderService` for LLM calls, calls `ToolExecutor` for tool execution, and accumulates each round's response and tool results into the session history. The core loop logic is lean — about tens of lines of Java, independent of Spring AI's agent abstraction — so implementers fully master how the agent works.

**The `PromptBuilder` module.** Assembles the prompt for each LLM call, concatenating four parts in order:

1. System prompt (the `AGENT.md` body — this agent's instructions — plus Bootstrap files, provided by `ContextLoader`; the current date and time is appended at the end — the LLM doesn't know today's date on its own, and "today" in scheduled scenarios depends entirely on this line)
2. Memory injection (session history + long-term memory, provided by `MemoryService`)
3. Conversation history (session messages truncated per `maxHistoryTurns`)
4. The tool list available to the current Profile (in function-calling format)

**The `ToolExecutor` module.** Executes the tool-call requests returned by the LLM. Finds the tool in the `ToolRegistry`, runs the sandbox check, executes the tool, wraps the result as a `ToolResult` for the ReAct loop, and writes to the `tool_invocations` table. On failure, returns error info per the retry policy.

**The `AgentService` module.** The unified entry shared by all three trigger sources, and the orchestrator of one processing run: inside `process(Session, String)` it sequentially — puts the current Profile into `ProfileContext` (a ThreadLocal; with virtual threads every request is naturally isolated), calls `ReActLoop.run` to finish the loop, persists the session, and clears `ProfileContext` in `finally`. `ProfileContext` answers "during tool execution, how do we know which agent is current": `OryxTool.execute`'s signature carries no Profile, so needs like `NotifyTools` reading `notify_channels` or filtering a Profile's tool subset all read from `ProfileContext` — without changing the tool interface.

### 4.3 Key Design Points

**The `MAX_ITERATIONS` limit.** Default 10 in the core stage, preventing the agent from falling into an infinite tool-call loop; overridable per Profile.

**Message accumulation.** Every iteration appends the LLM response and tool results to the session's messages list. The session history contains the complete LLM call chain and tool call chain — queryable and auditable from outside.

**Context length management.** The core-stage strategy is simple: keep the system prompt and the most recent N turns, discard the rest; N is configured per Profile, default 20 turns. Summarization compression comes in the extension stage.

**Not in the core stage:** parallel tool calls (multiple tool calls in one response execute sequentially), inter-agent task delegation, streaming responses. These come in the extension stage.
