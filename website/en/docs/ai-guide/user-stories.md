---
title: Five User Stories
description: Implementation breakdown of US-1 through US-5, cross-story collaboration patterns, and deliverables
---

# Five User Stories

## 4. Spec-Kit-Based Implementation Breakdown

With the overall spec and plan ready from preparation, here is the concrete breakdown by the 5 user stories. Each story follows the same structure: core goal, Maven modules involved, Spec-Kit task-splitting approach, key task granularity, acceptance demo. Module names follow the 9 modules in Technical Solution Chapter 10.

---

### 4.1 US-1: LLM Integration (Capability 1)

**Core goal**: let OryxOS call any mainstream LLM, with agents unaware of which vendor is being called. The complexity of LLM calling is absorbed by Spring AI Alibaba; OryxOS adds only a thin wrapper on top.

**Maven modules involved**:
- `oryxos-core` (core abstractions: `OryxTool` interface, `Session`, `Profile`, `ContextLoader`)
- `oryxos-provider` (capability 1)
- `oryxos-boot` (Spring Boot startup module)

**Spec-Kit task-splitting approach**: `/speckit.tasks` splits tasks for US-1, ordered by dependency with parallelizable ones marked. Expected task categories:

| Task category | Main content |
| --- | --- |
| Environment setup | Maven multi-module skeleton (9 modules), Spring Boot startup config, Spring AI Alibaba dependencies |
| Core abstractions | `OryxTool` interface, `Profile` data structure, `Message` data structure |
| Provider implementation | `ProviderService` implementation, explicit provider name → `ChatModel` mapping, function-calling adaptation |
| Configuration | `application.yaml` with at least DeepSeek or Kimi working, plus `ConfigLoader` reading API keys from environment variables |

> **Key caution**: `ProviderService` must not distinguish providers by "scanning all `ChatModel` beans in the container" — with multiple providers coexisting, bean types are identical and ambiguous. An explicit provider name → `ChatModel` mapping is mandatory (Technical Solution 3.2). AI agents easily slip into type scanning — call this out in the task.

US-1 has no demo right after completion (no user-visible entry); it runs Demo 1 together with US-2 once US-2 completes.

---

### 4.2 US-2: ReAct Loop (Capability 2)

**Core goal**: implement the agent's core mechanism — the LLM decides whether to call a tool, observes the result, and decides the next step until a final response. The ReAct loop is OryxOS's most critical code.

**Maven modules involved**:
- `oryxos-core` (`ReActLoop`, `PromptBuilder`, `ToolExecutor`, `ContextLoader`)
- `oryxos-tool` (one HTTP tool + simplified `SandboxChecker`, needed by Demo 1)
- `oryxos-channel-cli` (CLI channel, needed by Demo 1)
- `oryxos-cli` (`oryxos init` + `oryxos chat` commands)

> Note: tool-related code lives in the single `oryxos-tool` module (the technical solution merged builtin/skill/mcp) — no longer multiple tool modules as in older versions.

**Spec-Kit task-splitting approach**: expected task categories:

| Task category | Main content |
| --- | --- |
| ReAct loop | `ReActLoop` main loop, `PromptBuilder`, `ToolExecutor`, `MAX_ITERATIONS` control |
| CLI channel | `CliChannel`, `oryxos chat` command, `oryxos init` workspace initialization |
| Basic tools | HTTP tool, simplified `SandboxChecker` (URL whitelist only) |
| Profile YAML parsing | SnakeYAML, Profile validation |
| Session | `Session` data structure, in-memory `SessionManager` (persistence in US-5) |

**Key task granularity**: US-2 is the focus of Spec-Kit splitting. Several complex tasks to split finer:
- The `ReActLoop` main loop (core loop logic is a lean ~tens of lines of Java, but the engineering parts — error handling, logging, message accumulation, iteration control — should split into 2–3 subtasks)
- `PromptBuilder` assembly (four parts — system prompt + Bootstrap + Memory + history + tool list — best added incrementally over several subtasks)

> **Re-emphasizing Constitution Principle 4**: when calling Spring AI, use only its protocol conversion and schema generation — **disable its automatic tool execution**; actual tool scheduling is controlled by `ToolExecutor`. When implementing `ReActLoop`, AI agents easily enable Spring AI's auto-execution out of habit, causing tools to be called twice — the task must state the prohibition explicitly.

After US-1 + US-2, run `/speckit.analyze` to check spec-vs-code consistency.

**Acceptance Demo 1**: weather check with outfit advice

Start the CLI with `oryxos chat`; the user enters "check Beijing weather and tell me what to wear." The agent calls the HTTP tool via the ReAct loop to fetch weather JSON, replies with outfit advice based on the data, and the full conversation log accumulates correctly into the session — with at least one provider working (DeepSeek or Kimi).

---

### 4.3 US-3: Three-layer Memory (Capability 3)

**Core goal**: let agents retain state across conversations. The core stage implements a minimal two layers (session + long-term) with one `MEMORY.md` file and two built-in tools, letting the agent write and read actively.

**Maven modules involved**:
- `oryxos-memory` (capability 3, including the `MemoryService` three-layer facade, `LongTermMemory`, `MemoryTools`)

**Spec-Kit task-splitting approach**: US-3 is relatively independent — depends on US-2 but doesn't affect US-4. Expected task categories:

| Task category | Main content |
| --- | --- |
| `MemoryService` facade | Three-layer unified facade; internally delegates session memory to `SessionManager` and long-term memory to `LongTermMemory` |
| `LongTermMemory` | Four methods — `append`, `load`, `recallByKeyword`, `truncateIfNeeded`; the interface reserves `recall(mode)` headroom for vector retrieval |
| `MemoryTools` | Two built-in tools — `save_memory` + `recall_memory` — annotated with `@Tool` |
| `PromptBuilder` integration | Inject memory via `MemoryService` in `PromptBuilder`, without breaking the ReAct loop proven in US-2 |
| `MEMORY.md` file management | File location, format conventions, over-length truncation strategy |

After US-3, run `/speckit.analyze`.

**Acceptance Demo 2**: remembering preferences across conversations

In the first conversation, tell the agent "my project uses Spring Boot, deployed on K8s"; the agent proactively calls `save_memory` to append it to `MEMORY.md`. Restart OryxOS or start a new session. In the second conversation, ask "what database could my project use?" — the agent references the remembered preference in its answer.

---

### 4.4 US-4: Plugin Tool System (Capability 4)

**Core goal**: let business teams extend OryxOS's capabilities. Three tiers of Plugin Tools:
1. Zero-code SKILL.md + MCP (recommended)
2. Light-code: write your own MCP server
3. Full-code: Java `@Tool` annotation

The core stage completes the three-tier infrastructure plus the full built-in toolset.

**Maven modules involved**:
- `oryxos-tool` (complete file tools + shell tool, MCP client, full `SandboxChecker`, `ToolRegistry` — the three-in-one module)
- `oryxos-core` (SKILL.md loading belongs to `ContextLoader`, not the tool module)

**Spec-Kit task-splitting approach**: US-4 can run parallel with US-3 (both depend on US-2, not on each other). Expected task categories:

| Task category | Main content |
| --- | --- |
| Built-in tools completion | `read_file`, `write_file`, `list_dir`; shell tool with whitelist; full `SandboxChecker` implementation |
| MCP client | `mcp_servers.yaml` parsing, `McpClientService` connecting at startup, `tools/list` fetching tools, `McpToolAdapter` wrapping into `OryxTool` |
| `AGENT.md` | `ContextLoader` loads each agent's `AGENT.md` body under `.oryxos/agents/` into the system prompt — belongs to core, not tool |
| Agent definition | `AgentLoader.deriveProfile` deriving `Profile` from `AGENT.md` frontmatter (including `tools` / `mcp_servers` fields) |

**Key task granularity**: US-4 has many tasks; the complex ones needing careful splitting:

- **MCP client integration** (MCP protocol is JSON-RPC over stdio or SSE; the Java ecosystem is less mature than Python's):
  - Implement stdio transport first (most common); SSE goes to extension
  - Split the stdio MCP client into subtasks: connection management, `tools/list`, `tool/call`, error recovery
- **Full `SandboxChecker`** (expanding US-2's simplified version into the full one: file-path whitelist + shell-command whitelist + HTTP-domain whitelist — split into 3 subtasks)

After US-4, run `/speckit.analyze`.

**Acceptance Demo 3**: zero-code PR digest

The business side writes an agent directory `.oryxos/agents/daily-pr-digest/` (the `AGENT.md` body describing the task) and configures `github-mcp` (a ready-made community MCP server) in `mcp_servers.yaml`. Once started, the agent reads the `AGENT.md` body, calls `github-mcp` to fetch PRs, and summarizes a briefing — the business side wrote zero code, just one directory plus config.

---

### 4.5 US-5: Web Service (Capability 5)

**Core goal**: expose all of OryxOS's capabilities via REST API, with business systems integrating over HTTP. This is the key capability distinguishing OryxOS from personal-assistant projects.

**Maven modules involved**:
- `oryxos-web` (capability 5)
- `oryxos-storage` (SQLite persistence layer — sessions upgraded from in-memory, plus the `tool_invocations` and `llm_calls` audit tables)
- `oryxos-cli` (all 12 Picocli commands)
- `oryxos-core` (`ConfigLoader`, `ContextLoader` Bootstrap loading completed)

**Spec-Kit task-splitting approach**: US-5 depends on all four prior stories and is implemented last; it has the highest task density. Expected task categories:

| Task category | Main content |
| --- | --- |
| Web Service foundation | `WebServer` startup + virtual-thread config, `GlobalExceptionHandler`, OpenAPI docs |
| 6 ApiControllers | Session + Agent + Profile + Memory + Tool + System — one endpoint group per controller, **parallelizable** |
| Core 10 REST endpoints | 4 session management, 1 agent invocation, 3 profile/memory/tool listings, 2 health/info |
| Persistence upgrade | Sessions from in-memory to SQLite, `SessionRepository`, restart recovery — and **writing the `tool_invocations` and `llm_calls` audit tables** |
| Config & context | `ConfigLoader` secrets loading, `ContextLoader` Bootstrap loading completed and integrated with `PromptBuilder` |
| Full CLI | All 12 Picocli commands |
| Engineering | Logback + SLF4J structured logging + error handling |

> **Watch the audit-table writes** (Constitution Principle 6): `tool_invocations` and `llm_calls` land in the database from the core stage — not just logs — so the auditable data foundation stands on day one. AI agents easily skip this (thinking logs suffice); state it explicitly in tasks.

**Key task granularity**: US-5 has the most engineering.
- The 6 `ApiController`s can be implemented in parallel (mutually independent), 1–2 endpoints each
- The session SQLite upgrade is mainly `SessionRepository` + `messages_json` serialization — handle session-state migration carefully
- When integrating Bootstrap loading (`ContextLoader`) with `PromptBuilder`, don't break the previously working ReAct loop

After US-5, run the final `/speckit.analyze` — main development complete.

**Acceptance Demo 4**: Web Service synchronous calls

An external system creates a session via `POST /api/v1/sessions`, posts a message via `POST /api/v1/sessions/{id}/messages`, queries history via `GET`, archives via `DELETE` — the complete chain works.

**Acceptance Demo 5**: Web Service multi-endpoint coordination

An external system calls `GET /info` for health + provider list, `GET /profiles` to list available agents, `GET /tools` for available tools, `POST /agents/{name}/invoke` for a stateless call, and `GET /memory` for long-term memory — five different endpoints cooperating in one business flow.

---

### 4.6 Collaboration Patterns During Implementation

Several cross-story collaboration points during implementation of the 5 user stories:

**Run `/speckit.analyze` at the end of every user story**

Checks whether constitution + spec + plan + tasks + code are consistent; fix drift immediately when found. This is Spec-Kit's core anti-drift command — **do not skip it**.

**Actively correct AI agents that drift from the constitution**

When Claude Code generates code violating the constitution, have the AI agent re-read the constitution and fix it. The easiest mistakes on OryxOS:

| Problem | Correct approach |
| --- | --- |
| Using non-JDK-21 features | JDK 21 enforced |
| Changing the ReAct implementation (relying on Spring AI auto-execution) | Self-implemented; if tools get called twice, check here first |
| Spring AI automatic tool execution enabled | Must be disabled — see Constitution Principle 4 |
| Tools split back into multiple modules | Should be the single merged `oryxos-tool` module |
| Provider via type scanning | Must use the explicit provider-name mapping |
| `AgentLoader`/`AGENT.md` treated as a Tool | Agent directories belong to `ContextLoader` in the core module |
| Audit tables not persisted | `tool_invocations` and `llm_calls` written from day one |

**When cross-task context is lost, return to the spec**

After Spec-Kit splits code into many tasks, the AI agent implementing each task may not know what earlier tasks did. Periodically have it read `spec.md` + `plan.md` + recent code.

**Git-commit at each user-story completion**

Makes it easy to roll back to a stable state anytime.

---

## 5. Project Deliverables

After main development, OryxOS 1.0 is a demonstrable, minimal-but-complete Agent OS runtime kernel with all five core capabilities working. Beyond the core code itself, several deliverables:

### Project Homepage

As an open-source project, OryxOS needs an independent homepage as its public face — built with VitePress or a similar static site tool — explaining what OryxOS is, what the five core capabilities are, and how to get started quickly.

### Spec-Kit Artifacts Preserved

The constitution, spec, and plan under `.specify/` stay in the repo after main development, serving as long-term references for community continuation.

### Community Documentation

Remaining docs — API reference, deployment & ops handbook, contributor guide — are community-built projects, contributed via PRs.
