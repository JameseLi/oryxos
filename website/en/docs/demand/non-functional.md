---
title: Non-Functional & Acceptance
description: Performance, reliability, security and compliance requirements — plus data model, milestones, risks, and acceptance criteria
---

# Non-Functional & Acceptance

## Non-Functional Requirements

### Performance

| Metric | Target |
| --- | --- |
| Agents per node | ≥ 10 |
| Concurrent sessions per node | ≥ 100 |
| Session creation P99 latency | ≤ 200ms |
| OryxOS internal forwarding overhead | ≤ 50ms |

(LLM call latency itself depends on the provider and is outside OryxOS's control.)

### Reliability

- Registered Profile configurations and written session data are guaranteed not to be lost
- On LLM provider failure, the core stage reports the error directly; full failover is an extension feature
- On tool-call failure, retry per the retry policy — exponential backoff, up to three attempts by default

### Operability

- Configuration changes are made by editing Profile YAML files; in the core stage they take effect on service restart
- Supports physical machines, VMs, Docker, and Kubernetes deployment

### Compatibility

- **JDK**: 21+ (required by Spring Boot 3.x)
- **OS**: mainstream Linux distributions (Ubuntu 22.04+, CentOS 8+, Debian 11+, Alibaba Cloud Linux 3, Rocky Linux)
- **LLM protocol**: the OpenAI-compatible protocol is the de facto standard — any provider implementing it can plug into OryxOS directly

### Security

- API calls support HTTPS
- Sensitive configuration (LLM API keys, database passwords, tool credentials) supports encrypted storage — never plaintext in config files
- Tool calls are isolated via application-layer whitelist checks
- Full authentication, Docker sandbox isolation, and SSO integration are extension features

### Compliance

- **Data residency**: OryxOS never sends any data outbound on its own; all data stays on enterprise infrastructure
- Full audit log coverage, SIEM export, SOC 2, GDPR, HIPAA, and China MLPS Level 3 integration are extension features

---

## Data Model

### Profile (YAML file)

See [Core Features — Profile Configuration](/en/docs/demand/core-features#profile-configuration).

### Session (persisted to SQLite)

| Field | Type | Description |
| --- | --- | --- |
| `session_id` | VARCHAR | Primary key, generated from channel+user+profile |
| `profile_name` | VARCHAR | Associated profile name |
| `channel` | VARCHAR | Access channel |
| `user_id` | VARCHAR | User identity |
| `messages_json` | TEXT | JSON-serialized conversation history |
| `status` | VARCHAR | `active` / `archived` |
| `created_at` | TIMESTAMP | Creation time |
| `last_active_at` | TIMESTAMP | Last active time |
| `archived_at` | TIMESTAMP | Archive time (nullable) |

### Memory (file-based, not a database table)

Long-term memory is one Markdown file — `.oryxos/memory/MEMORY.md` — appended to over time, with no structured schema. After vector databases are introduced in the extension stage, memory gains structured embedding storage.

### Tool Invocation (records every tool call)

| Field | Type | Description |
| --- | --- | --- |
| `id` | BIGINT | Primary key |
| `session_id` | VARCHAR | Associated session |
| `tool_name` | VARCHAR | Tool name |
| `input_json` | TEXT | Call parameters (JSON) |
| `result_json` | TEXT | Execution result (JSON) |
| `success` | BOOLEAN | Whether successful |
| `error_message` | TEXT | Error message (nullable) |
| `duration_ms` | BIGINT | Execution duration (ms) |
| `created_at` | TIMESTAMP | Call time |

### LLM Call (records every LLM call)

| Field | Type | Description |
| --- | --- | --- |
| `id` | BIGINT | Primary key |
| `session_id` | VARCHAR | Associated session |
| `provider` | VARCHAR | Provider name |
| `model` | VARCHAR | Model name |
| `prompt_tokens` | INT | Input token count |
| `completion_tokens` | INT | Output token count |
| `total_tokens` | INT | Total tokens |
| `duration_ms` | BIGINT | Call duration (ms) |
| `created_at` | TIMESTAMP | Call time |

---

## Milestones

OryxOS core features are organized on a **4-week cadence**, 3 hours per week, 12 hours total.

| Week | Time | Capability track | Demonstrable outcome |
| --- | --- | --- | --- |
| **Week 1** | 3h | LLM integration + ReAct loop (capabilities 1+2) | `oryxos chat` multi-turn conversation; agent calls HTTP tool via ReAct for weather lookup |
| **Week 2** | 3h | Memory + tool system (capabilities 3+4) | Agent remembers user preferences and uses them later; calls local files and external MCP servers |
| **Week 3** | 3h | Web Service (capability 5) | External systems call OryxOS completely through 10 REST endpoints |
| **Week 4** | 3h | Multi-agent demo + engineering finish | Multiple agents coexist; full CLI; sessions survive restarts; project homepage live |

### Weekly Details

**Week 1** (3h): LLM integration + ReAct loop
- `oryxos init` workspace initialization, Profile YAML parsing
- Provider abstraction (based on Spring AI Alibaba; DeepSeek or Kimi first)
- ReAct loop (core loop ~tens of lines of Java: LLM calls, tool-call parsing, message accumulation)
- One basic built-in tool (HTTP), CLI channel
- Session management (in-memory; SQLite persistence in week 4)

**Week 2** (3h): Memory + tool system
- Minimal long-term memory (MEMORY.md file, `save_memory` and `recall_memory` built-in tools, whole file injected into system prompt at startup)
- File tools (read_file, write_file, list_dir), shell tool (with whitelist checks)
- MCP client integration (connect external MCP servers)

**Week 3** (3h): Web Service + API endpoints
- Web Service core 10 REST endpoints (4 session, 1 agent invocation, 3 profile/memory/tool listings, health/info)
- Start Spring MVC service via `oryxos serve`
- Configuration & secrets loading (environment variable injection + basic validation)

**Week 4** (3h): Multi-agent demo + engineering finish
- Multi-agent demo (two differently configured agents coexisting on one instance)
- Full 12-command CLI, sessions persisted to SQLite (survive restarts)
- Bootstrap file mechanism (AGENTS.md, SOUL.md, USER.md loaded into system prompt)
- Structured logs, project homepage (VitePress or similar)

---

## Risks & Open Questions

### Identified Risks

| Risk | Description | Mitigation |
| --- | --- | --- |
| **Core scope risk** | 4 weeks × 12 hours is extremely tight; some features may be more complex than expected | Keep core scope tight; if a week slips, move tail-end features to extensions immediately — every week must have a demonstrable outcome |
| **Spring AI compatibility risk** | Function calling, streaming, token counting, error-code details vary across providers | Stabilize the OpenAI protocol first in the core stage; full regression across other providers in extension |
| **Tool execution security risk** | Application-layer whitelists are not a complete sandbox; OryxOS process could be affected | Strictly limit built-in tool capabilities; do not run highly sensitive scenarios in production during the core stage |
| **Java startup speed and memory** | Java apps start slowly and use more memory, hurting UX | Validate feature completeness first; introduce GraalVM Native Image in extension |
| **Community continuity uncertainty** | Extension features depend on community contributors; some may stall long-term | Maintainers keep baseline investment in core extension features; community features rely on the community |
| **Positioning misread risk** | Community may ask "how does the core stage differ from OpenClaw/Hermes" | Docs state clearly: the core stage is the foundation, differentiation is the endgame — do not oversell as a complete enterprise Agent OS |
| **Ecosystem relationship risk** | Relationship between OryxOS and OpenClaw/Hermes | Interoperate via SKILL.md — complementary, not competitive; OpenClaw is personal, Hermes is small-team, OryxOS is enterprise |

### Open Questions

| Question | Description | Decision time |
| --- | --- | --- |
| Provider abstraction interface design | Use Spring AI `ChatClient` directly, or add an OryxOS layer on top | Technical solution stage |
| SQLite vs H2 for underlying storage | SQLite is embedded C; H2 is pure Java | Technical solution stage |
| Bootstrap file load order and priority | How AGENTS.md, SOUL.md, USER.md compose into the system prompt | Technical solution stage |
| GraalVM Native Image timing | Core stage or extension stage | After core stage ends |

---

## Acceptance Criteria

### Functional Acceptance

All core features complete; each module covered by at least one end-to-end test case:

- [ ] `oryxos init` workspace initialization
- [ ] Profile configuration and management (multiple profiles coexisting)
- [ ] Provider abstraction (at least DeepSeek and Kimi working)
- [ ] ReAct loop (multi-turn tool calls, correct message accumulation, correct termination at max iterations)
- [ ] Long-term memory (save_memory writes, recall_memory keyword retrieval, injection into system prompt at startup)
- [ ] Built-in tools (file, HTTP, shell, save_memory, recall_memory, notify)
- [ ] Plugin tool access (tier-1 zero-code SKILL.md + MCP working; tier-3 `@Tool` annotation example working)
- [ ] MCP client integration, CLI channel
- [ ] Scheduled task `AgentScheduler` (third trigger source — cron-triggered, reusing the same `AgentService` pipeline as CLI/Web Service)
- [ ] All 10 core Web Service REST endpoints working
- [ ] Session persistence (SQLite, survives restarts)
- [ ] All 12 CLI commands
- [ ] Configuration & secrets loading

### Performance Acceptance

Verified through stress testing:

- 10 agents per node running stably for 4 hours
- 100 concurrent sessions per node
- Session creation P99 latency < 200ms
- Internal forwarding overhead < 50ms

### Operability Acceptance

- Complete deployment docs (a newcomer finishes single-node deployment within 30 minutes)
- CLI has clear help and error messages
- Project homepage live, explaining what OryxOS is and how to start quickly

### Scenario Acceptance (Two Demos)

An earlier plan split five demos by "one demo per capability," but real scenarios never run capabilities in isolation — a compelling agent stacks multiple capabilities and runs itself on schedule. Replaced with two **daily, automatically running** end-to-end demos; each strings together multiple core capabilities, and together they cover all five capabilities plus the scheduled-trigger source. Both demos passing is a **hard condition** for the core release:

| Demo | Capabilities verified | Scenario | Acceptance |
| --- | --- | --- | --- |
| **Demo 1: Daily weather** | Capabilities 1+2 (LLM + ReAct), capability 4 (built-in HTTP tool), scheduler (`AgentScheduler`) | Every morning on schedule: fetch weather, compose outfit advice, push to enterprise IM group | No human trigger — the full ReAct loop runs on time; weather fetch and push are each one HTTP call, both passing the sandbox domain whitelist and both written to `tool_invocations`; `GET /api/v1/sessions/{id}` shows the complete auto-triggered conversation |
| **Demo 2: Daily tech digest** | Capability 4 (Plugin Tool tier-1 SKILL.md zero-code + tier-2 MCP), capability 3 (Memory), scheduler (`AgentScheduler`) | Every day on schedule: aggregate tech news and push; the digest reflects user-stated interests (e.g., "more into AI and chips") | Business side writes no Java — only SKILL.md + `mcp_servers.yaml` + Profile `schedules`; the LLM decides which news MCP tool to call, composes the digest, and calls the push MCP tool itself — OryxOS does not parse task steps; the digest reflects preferences stored in MEMORY.md |

Both demos are "clock-pushed" (`AgentScheduler` triggers on time), but must also support a "human-pushed" manual run for verification (`oryxos chat` or `POST /agents/{name}/invoke`) — proving the same agent walks the same `AgentService` pipeline regardless of entry.
