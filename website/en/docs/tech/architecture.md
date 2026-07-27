---
title: Architecture
description: The four-layer view — access, engine, capability, foundation — and how the five capabilities collaborate
---

# Architecture

![OryxOS architecture](/images/oryxos-architecture.svg)

## 2. Overall Architecture

OryxOS's architecture is organized as "five core capabilities plus supporting modules." The five core capabilities form the body of the Agent OS runtime kernel; the supporting modules are the engineering infrastructure those capabilities need to run.

As a whole, OryxOS is a Spring Boot monolith with two human-triggered entries plus one internal automatic trigger:

1. **CLI Channel** for local interaction and debugging, and **Web Service** for business systems integrating via REST API — these two are "human-pushed"; **`AgentScheduler`** (8.5) fires calls automatically on cron — "clock-pushed." Messages from all three entries converge into the same engine; `AgentService` is the unified entry and does not distinguish which entry a message came from.
2. The engine is the **ReAct loop** — the system's central hub, driving the chain "receive message → assemble prompt → call LLM → execute tool → feed result back → continue reasoning." The engine doesn't do the work itself; it schedules three capabilities:
   1. **Provider** — LLM calls, connecting outward to each vendor's API
   2. **Memory** — session and long-term memory, reading and writing local files
   3. **Tool** — tool execution, connecting outward to external MCP servers via the MCP client

Beneath these three capabilities sits the storage layer: sessions and audit data land in SQLite; user-maintained data — Profiles, Bootstrap files, memory, skills — lives on the filesystem.

Two architectural points stand out:

1. All capabilities converge into one engine, one storage, one process — matching the "single binary, install and run" positioning. External dependencies (LLM vendor APIs, external MCP servers) sit outside the application boundary; OryxOS itself binds to none of them.
2. Engine and capabilities, capabilities and the outside — all are decoupled through abstract interfaces, so adding a new channel, provider, or tool in the extension stage means extending at the edges without touching the core engine.

### 2.1 Layered View

Four layers, top to bottom:

1. **Access layer** (CLI Channel, Web Service REST API, `AgentScheduler` cron triggers) — messages in and out.
2. **Engine layer** (`ReActLoop`, `PromptBuilder`, `ToolExecutor`) — the agent's brain.
3. **Capability layer** (Provider, Memory, Tool) — LLM calls, context, and execution power for the engine.
4. **Foundation layer** (Profile/Bootstrap/Skill loading, session storage, SQLite, config & secrets loading) — the engineering bedrock.

### 2.2 How the Five Capabilities Relate

The five capabilities are not parallel feature modules — they have explicit collaboration relationships:

- **ReAct loop (capability 2)** is the engine: it runs "user message → LLM reasoning → tool execution → result feedback → continue."
- **Provider (capability 1)** supplies LLM calling to the ReAct loop — invoked every reasoning round.
- **Memory (capability 3)** supplies context to the ReAct loop — session history and long-term memory are injected at every prompt assembly.
- **Tool (capability 4)** supplies execution to the ReAct loop — once the LLM picks a tool, the ReAct loop executes it.
- **Web Service (capability 5)** is the outward exit of these internal capabilities, packaging the first four as a REST API for business systems. It doesn't participate in the agent's internal loop; it is one of the loop's trigger entries and result exits (the other two entries are CLI Channel and `AgentScheduler`, see 8.5).

> **In one sentence**: Provider, Memory, and Tool feed the ReAct loop engine; what the engine produces is served through three entries — CLI, Web Service, and scheduled tasks.
