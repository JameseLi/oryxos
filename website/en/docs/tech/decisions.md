---
title: Key Decisions
description: Seven key trade-offs in a JDK 21 + Spring Boot 3.x monolith — including using only half of Spring AI
---

# Key Decisions

## 1. Solution Overview

OryxOS is a **Spring Boot 3.x** monolith running on **JDK 21**, doing LLM calls via **Spring AI Alibaba**, with a self-implemented **ReAct loop** as the agent core. The whole of OryxOS is one executable JAR — single-binary deployment; **GraalVM Native Image** arrives in the extension stage to further compress startup time and memory.

> **Tech stack in one line**: JDK 21 + Spring Boot 3.x + Spring AI Alibaba + self-built ReAct loop + SQLite + Picocli CLI.

### 1.1 Key Technical Decisions

The requirements doc defines five core capabilities; here are the 7 key decisions unified in one place — a quick-reference table first, then each decision in detail.

| # | Decision | Choice | Rationale |
| --- | --- | --- | --- |
| 1 | ReAct loop implementation | Self-implemented; no Spring AI agent abstraction | Full control, room to customize loop behavior later |
| 2 | Spring AI usage boundary | Only Provider abstraction + protocol conversion + `@Tool` schema generation; automatic tool execution disabled | Avoids tools being called twice; the ReAct loop stays fully under OryxOS's control |
| 3 | Execution model | Synchronous blocking + Java 21 virtual threads | Straightforward and simple; no reactive programming; high concurrency on a single node |
| 4 | Tool registration | `@Tool` annotation + **OryxTool** abstraction layer | Unifies built-in and MCP tool interfaces; the ReAct loop never knows a tool's origin |
| 5 | HTTP service layer | Spring MVC + Java 21 virtual threads | Synchronous and intuitive; thousands of concurrent connections on one node; `SseEmitter` for streaming later |
| 6 | Sandbox strategy | Interface first: `Sandbox` abstraction + `WhitelistSandbox` (application-layer path/pattern whitelist); evolves container → microVM later | `SecurityManager` is deprecated since JDK 17 and unavailable in JDK 21, conflicting with the JDK 21+ requirement; the interface is independent of the whitelist implementation, so heavier isolation later doesn't touch callers |
| 7 | Persistence | SQLite + Spring Data JPA + `MEMORY.md` file | Single binary; audit tables written from day one, avoiding later rework of parsing logs back into tables |

**Decision 1: implement the ReAct loop ourselves.** Spring AI handles the底层 work — LLM calls, function-calling protocol conversion, provider abstraction — while the ReAct loop is hand-written, keeping the agent core fully controllable and leaving room to customize loop behavior.

**Decision 2: draw a clear boundary around Spring AI usage.** This is where bugs hide most easily, so it stands alone as a decision. Spring AI ships with a complete automatic tool-calling execution mechanism (it can execute tools automatically and feed results back to the model).

OryxOS does **not** use this automatic execution. It uses only two things from Spring AI:
- The Provider abstraction and protocol conversion to each LLM vendor
- JSON Schema generation from `@Tool` annotations

The actual scheduling and execution of tools is fully controlled by OryxOS's own **`ReActLoop`** plus **`ToolExecutor`**. In other words, Spring AI acts only as a protocol adapter and schema generator inside OryxOS — never as the loop engine. During development, Spring AI's automatic tool execution **must be disabled**, or tools will be called twice.

**Decision 3: synchronous execution model.** The core stage uses a synchronous blocking model, consistent with Spring MVC. A message's journey — arrival, ReAct loop execution, tool calls, provider calls, final response — is synchronous end to end. This pairs well with Java 21 virtual threads: high concurrency on a single node without reactive programming. Streaming output (SSE) and async tool calls come in the extension stage.

**Decision 4: tool registration via `@Tool` annotation plus the `OryxTool` abstraction.** Spring AI's annotation scans Java methods to generate JSON Schemas; on top of it OryxOS adds an **`OryxTool`** abstraction that unifies built-in tools and MCP tools into one interface form, so the ReAct loop never perceives a tool's origin. The exact annotation name and usage depend on the Spring AI version adopted — verify against the current version before development.

**Decision 5: HTTP service layer on Spring MVC with Java 21 virtual threads.** Synchronous, intuitive code plus virtual-thread concurrency easily sustains thousands of connections on one node. When SSE streaming arrives in the extension stage, Spring MVC's `SseEmitter` supports it.

**Decision 6: define the Sandbox interface first; fill only one implementation tier in the core stage.** Isolation strength and cost form a seesaw, from light to heavy: application-layer whitelist checks, container isolation (namespace + cgroups + seccomp), microVM (Firecracker / Kata / gVisor), full VMs or physical isolation. To keep the core-stage choice from binding the future architecture, first abstract a `Sandbox` interface expressing the intent "execute an action in a controlled environment," carrying no concept specific to any tier (no words like "container image" or "VM config"). The core stage implements only `WhitelistSandbox`: file operations restricted to the working directory, shell command whitelists, HTTP domain whitelists — validated at the application layer without Java `SecurityManager` (deprecated since JDK 17, unavailable in JDK 21, conflicting with this project's JDK 21+ requirement). The extension stage upgrades signal-driven: when "untrusted code or multi-tenancy" appears, move to container isolation; when "fully untrusted code or large-scale multi-tenancy" appears, move to microVM. The interface never changes across upgrades — only new implementation classes.

**Decision 7: persistence on SQLite + Spring Data JPA, with long-term memory in a `MEMORY.md` file plus keyword retrieval.** Agent directories live in `.oryxos/agents/`; sessions, tool invocations, and LLM calls land in SQLite. The audit tables `tool_invocations` and `llm_calls` are **written from the core stage** (no query APIs required), laying the data foundation for the differentiating "auditable" capability on day one and avoiding later log-parsing rework. Full vector retrieval arrives in the extension stage (see Chapter 8).

### 1.2 The Complete Tech Stack

1. **JDK 21** + **Spring Boot 3.x** (virtual threads for high concurrency)
2. **Spring AI** + **Spring AI Alibaba** (LLM provider abstraction, reusing ready-made mainstream LLM connectors)
3. Self-implemented **ReAct loop** (the agent core loop)
4. **Spring MVC** (HTTP API service layer)
5. **Picocli** (CLI tool)
6. **SnakeYAML** (Profile YAML parsing)
7. **SQLite** + **Spring Data JPA** (session, audit, and metadata persistence)
8. **MCP Java SDK** (MCP client integration — a community project; partial self-implementation may be needed)
9. **Logback** + **SLF4J** (structured logging)
10. **Micrometer** + **Prometheus** (metrics, extension stage)
