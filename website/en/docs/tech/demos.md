---
title: Acceptance Demos & Timeline
description: Three acceptance demos — daily weather, daily tech digest, daily GitHub digest — plus the 4-week cadence and performance considerations
---

# Acceptance Demos & Timeline

## 12. Key Flows

An earlier plan split five flows by "one demo per capability," but real scenarios never run capabilities in isolation. Replaced with three **daily, automatically running** end-to-end flows that horizontally string together all five core capabilities plus the scheduled third trigger — and they happen to be the concrete outputs of Chapter 11's "one directory = one agent," each demonstrating one level of agent-directory richness: weather = bare `AGENT.md`; tech digest = `AGENT.md` + `skills/` sub-instructions; GitHub digest = `AGENT.md` + `scripts/` scripts. The foundation and "defining agents" come together and run here.

### 12.1 Demo 1: Daily Weather (Bare AGENT.md)

**Scenario:** Every morning at 8am, the agent automatically fetches the weather, composes outfit advice, and pushes it to the enterprise IM group — no human initiation.

1. `AgentScheduler` triggers on the cron expression declared in the `AGENT.md` frontmatter `schedules`, generates a message, and calls `AgentService.process` — the same method `CliChannel`/`ApiController` call; the `ReActLoop` never knows this trigger is "clock-pushed"
2. First `ReActLoop` round: `PromptBuilder` assembles the system prompt via `ContextLoader` (the `AGENT.md` body — this agent's instructions — plus Bootstrap)
3. `ProviderService` calls DeepSeek, which returns a tool call for `http_get`
4. `ToolExecutor` executes; `HttpTools` passes `Sandbox.enforce(...)` URL validation, fetches the weather JSON, and writes to `tool_invocations`
5. The result is appended to the session; round two: DeepSeek sees the weather, composes outfit advice, and decides to call `notify(content="...")` — the address comes from the frontmatter's `notify_channels`
6. `ToolExecutor` executes again; `NotifyTools` delegates to `WebhookNotifyAdapter`, which also passes `Sandbox.enforce(...)` domain validation before sending; on success a second `tool_invocations` row is written
7. No more tool calls — the loop ends; the final response stays in this auto-triggered session

**Acceptance points:** no human trigger throughout; both external calls pass the sandbox whitelist and both are audited; `GET /api/v1/sessions/{id}` shows the complete conversation; the same agent can also be run manually via `oryxos chat` or `POST /agents/{name}/invoke`, proving "human-push" and "clock-push" share one pipeline. Bare `AGENT.md` — no sub-instructions or scripts.

Involves capability 1 (Provider) + capability 2 (ReAct) + capability 4 (built-in HTTP tool + `NotifyTools` + Sandbox) + scheduling (`AgentScheduler`) + capability 5 (session query as backstop).

### 12.2 Demo 2: Daily Tech Digest (AGENT.md + skills/ Sub-instructions)

**Scenario:** Every morning at 9am, the agent aggregates the day's tech news and pushes it; the digest reflects interest areas the user mentioned before. The business side writes no Java at all.

1. The business side **writes an agent directory** `.oryxos/agents/daily-tech-digest/`: `AGENT.md` (frontmatter: `identity` + `tools` (including `http_get`, `read_file`, `notify`) + a 09:00-daily `schedules` + `notify_channels`; body: "compose the daily digest; read the formatting spec at `skills/digest-format.md`") + `skills/digest-format.md` (a long formatting spec); if a news-aggregation MCP is needed, add one entry in `mcp_servers.yaml`
2. The user previously said "I'm more into AI and chips" — DeepSeek calls `save_memory` to write it into the archival section of `MEMORY.md`
3. On time, `AgentScheduler` triggers `AgentService.process`; `PromptBuilder` injects the `AGENT.md` body + `MEMORY.md` into the system prompt — the long formatting sub-instruction is **not preloaded**
4. Following the body's guidance, the LLM calls **`read_file("skills/digest-format.md")`** to fetch the formatting spec as a tool result into context — the sub-instruction only occupies context at this moment (progressive disclosure within one agent, using the foundation's existing `read_file`)
5. Per the spec, the LLM calls a news tool (`http_get` or a news MCP, forwarded by `McpToolAdapter`) for the day's tech news; seeing the remembered preference, it naturally weights AI and chips when composing — OryxOS does not parse task steps
6. The LLM calls the built-in `notify(content="...")` to push; `ToolExecutor` writes `tool_invocations`

**Acceptance points:** the business side writes only one agent directory (two markdown files), zero Java; `tool_invocations` contains `read_file("skills/digest-format.md")` (proving on-demand sub-instruction loading works); `GET /api/v1/agents` lists `daily-tech-digest`; the digest reflects memory preferences; the same agent can be triggered manually for verification.

Involves agent-directory sub-instructions (on-demand `read_file`) + capability 4 tier 2 (MCP) + built-in `NotifyTools` + capability 3 (Memory) + scheduling.

### 12.3 Demo 3: Daily GitHub Digest (AGENT.md + scripts/ Scripts)

**Scenario:** Every morning at 9:30, the agent fetches today's and this month's trending GitHub projects, picks AI-related ones, summarizes, and pushes. This demo shows an agent directory **bundling a script**: the agent runs the script for deterministic data.

1. The business side **writes a script-carrying agent directory** `.oryxos/agents/github-daily/`: `AGENT.md` (frontmatter: `tools:[shell, notify]` + a 09:30-daily `schedules`; body: "run the script → compose a three-part digest → notify") + `scripts/github_trending.py` (token-free GitHub Search API approximating trending by date range + stars)
2. On trigger, `PromptBuilder` injects the `AGENT.md` body
3. Following the body, the LLM calls `shell` to run `python scripts/github_trending.py` — `Sandbox.enforce` validates `shell.allowed_commands` to allow `python`, and `file.allowed_paths` restricts to this agent's `scripts/` directory; the script itself requests `api.github.com` and returns JSON. **The script's JSON output enters context; the script code does not** — and the call is written to `tool_invocations`
4. The LLM uses the JSON plus memory preferences to compose the three-part digest (today / this month / AI focus), then calls `notify` to push

> **The trust boundary of scripts (echoing 11.1 and Constitution Principle 4):** in step 3, the script makes network requests through the `python` subprocess itself, **bypassing `http_get`'s domain whitelist** (the whitelist only governs the built-in `http_get` tool). So **installing a script-carrying agent = trusting that agent's author** (same as Anthropic). In the core stage, the sandbox applies only two whitelists to scripts — "interpreter + script directory"; container/network isolation comes in the extension stage. An Agent OS must be honest about this: being able to run third-party agents is powerful, but trust shifts from "the foundation" to "the agent's author."

**Acceptance points:** `tool_invocations` contains the script-running `shell` call; script output enters context, code does not; the AI section reflects memory preferences; editing the `AGENT.md` body takes effect immediately.

Involves agent-directory scripts (`shell` running bundled scripts + the sandbox trust boundary) + capability 2 (ReAct) + capability 3 (Memory) + scheduling + built-in `NotifyTools`.

---

## 13. Timeline (4 Weeks)

Implementation follows the requirements doc's 4-week cadence — 3 hours per week, 12 hours total. Each week maps to a set of core capabilities, with a demonstrable outcome at week's end.

### Week 1 (3h): Capability 1 + 2 (LLM Integration + ReAct Loop)

- Maven multi-module skeleton (9 modules), `oryxos init`, Profile YAML parsing
- `ProviderService` wrapping Spring AI Alibaba (DeepSeek or Kimi first, including the provider-name mapping)
- `ReActLoop` + `PromptBuilder` + `ToolExecutor`, one built-in HTTP tool, `CliChannel`
- In-memory sessions (SQLite arrives in week 4)

**Demonstrable:** `oryxos chat` multi-turn conversation; the agent calls the HTTP tool to complete a simple task.

### Week 2 (3h): Capability 3 + 4 (Memory + Tools)

- `MemoryService` three-layer facade + `LongTermMemory` (`MEMORY.md` read/write), `save_memory` + `recall_memory`
- `PromptBuilder` gains memory injection
- File tools + shell tool (`Sandbox` interface + `WhitelistSandbox` application-layer whitelist), `McpClientService` (connect external MCP servers)
- `ContextLoader` loads the `AGENT.md` body (and on-demand sub-instructions in agent directories)

**Demonstrable:** the agent remembers preferences and uses them later; it reads/writes local files and calls external MCP servers to complete cross-tool tasks.

### Week 3 (3h): Capability 5 — Web Service

- `WebServer` (Spring MVC + virtual threads), six `ApiController`s with the core 10 endpoints
- `GlobalExceptionHandler`, `ConfigLoader` (configuration & secrets loading)

**Demonstrable:** external systems call OryxOS completely through the 10 REST endpoints.

### Week 4 (3h): Multi-agent Demo + Engineering Finish

- Multi-agent demo (two differently profiled agents coexisting on one instance)
- Sessions persisted to SQLite (including `tool_invocations` and `llm_calls` writes)
- `ContextLoader` Bootstrap loading, all 12 Picocli commands, structured logging
- `AgentScheduler` third trigger source (`ThreadPoolTaskScheduler` + `CronTrigger`, driven by the Profile `schedules` field)
- Project homepage (VitePress or similar)

**Demonstrable:** multiple agents coexist; the CLI is smooth; Bootstrap files affect agent behavior; sessions survive restarts; scheduled tasks fire on time; the homepage is live.

---

After the core stage, OryxOS 1.0 is a demonstrable, minimal-but-complete Agent OS runtime kernel with all five core capabilities working. It then transitions to open-source community maintenance; extension features (multi-channel, memory vector retrieval, episodic memory, the skill system, MCP server exposure, Tool Policy, full sandbox, the remaining Web Service endpoints, web dashboard, SSO and multi-tenancy, full audit, clustered HA) — and the governance layer that makes OryxOS a true enterprise-grade Agent OS — are advanced by the community over time.

---

## 14. Performance & Scalability Considerations

Performance targets are defined in the requirements doc; here is how they are reached.

**Java 21 virtual threads sustain high concurrency.** Each agent is an in-memory Profile object plus a session list — negligible footprint. Virtual threads give each concurrent request its own virtual thread; a few dozen OS threads suffice for thousands of concurrent requests, and LLM-call IO blocking automatically yields the OS thread.

**1000 concurrent sessions stay memory-manageable.** 1000 sessions averaging 50KB total ~50MB — fine. SQLite writes are triggered mainly by session message appends and audit-table writes; the core stage writes every time, and if stress tests reveal a bottleneck, optimize to batched writes.

**Memory file IO.** Each prompt assembly reads `MEMORY.md` once — a few KB to tens of KB at 1–2ms per read; acceptable at 1000 concurrency. Add a cache with file watch in the extension stage.

**Startup time.** Spring Boot starts in 2–4 seconds on JDK 21 — fine for a resident service, too slow for a CLI tool. In the core stage, CLI commands split into two kinds: those not needing Spring operate on files directly via standard APIs; only those needing it boot Spring. In the extension stage, GraalVM Native Image brings startup under 100ms.
