---
title: Supporting Modules & Persistence
description: Workspace initialization, AgentLoader, ContextLoader, AgentScheduler, ConfigLoader — plus SQLite persistence and the 9 Maven modules
---

# Supporting Modules & Persistence

## 8. Supporting Modules

Beyond the five core capabilities, OryxOS has several supporting modules that make the whole system run. They are not core capabilities of the runtime kernel, but none can be missing.

### 8.1 Workspace Initialization

**The `InitCommand` module.** The execution logic of `oryxos init`, creating the `.oryxos/` working directory with its full structure:

```
.oryxos/
├── agents/            # each subdirectory = one Agent (AGENT.md + optional skills/ scripts/ REFERENCE.md)
├── memory/
│   └── MEMORY.md      # long-term memory
├── mcp_servers.yaml   # MCP configuration
├── sessions/          # session data
├── logs/              # logs
├── AGENTS.md
├── SOUL.md
├── USER.md            # Bootstrap
└── oryxos.db          # SQLite
```

It creates directories, writes default templates, and generates a default Profile.

### 8.2 Profile Configuration

**The `AgentLoader` module.** Scans the subdirectories of `.oryxos/agents/`; `deriveProfile` derives a `Profile` from each `AGENT.md`'s frontmatter and registers it into the `ProfileRegistry`. At startup it validates: does the provider exist, are the tools registered, is the channel supported, do the Bootstrap files exist. Agents that fail validation do not block startup but are logged as errors.

**The `ProfileRegistry` module.** An in-memory index of derived Profiles with fast lookup by name. Channels use it to resolve the concrete Profile when receiving messages. Fields derived from `AGENT.md` frontmatter: `name`, `description`, `identity` (`agent_name`, `prompt`), `provider` (`name`, `model`, `temperature`), `tools`, `mcp_servers`, `channels`, `notify_channels` (`type` plus channel-specific config like `url`, used by `NotifyTools`, see 6.8), `schedules`, `bootstrap`, `settings` (`max_iterations`, `max_history_turns`). The core stage supports multiple coexisting agents, simultaneously available on one instance — the minimal expression of an "OS" in the core stage.

### 8.3 Context Loading (Bootstrap + AGENT.md Body)

> **Adjustment note:** Bootstrap files and the `AGENT.md` body are both fully injected into the system prompt by `ContextLoader`; an agent directory's sub-instructions / scripts / references are not preloaded — they are fetched on demand via the foundation's `read_file`/`shell`, guided by the body (progressive disclosure within a single agent, see 11.1).

**The `ContextLoader` module.** Reads `AGENTS.md`, `SOUL.md`, `USER.md` from `.oryxos/` per the Profile's `bootstrap` field (Bootstrap, fully injected); it also hands this agent's own `AGENT.md` **body** (the task instructions) to `PromptBuilder`. It re-reads on every prompt assembly with no caching — user edits take effect immediately. Sub-instructions / scripts / references in the directory are not injected here; the model fetches them via `read_file`/`shell` when needed. `AGENT.md` belongs to the context layer rather than the tool module because it is an input to prompts, not an executable tool.

### 8.4 Channel Access

Channels are the agent's inbound message entries, solving "messages in, responses out." HTTP access belongs to Web Service, not channels.

**The `CliChannel` module.** The implementation of `oryxos chat`: reads stdin and writes stdout for interactive conversation, maintains the current session, calls `AgentService.process` on each input, and supports `/quit` to exit. WeCom, Lark, DingTalk, Slack and other IM channels come in the extension stage, each extended via the Channel Adapter plugin mechanism; all IM channels call Web Service's agent endpoints underneath, never re-implementing agent logic.

### 8.5 Scheduled Tasks (The Third Trigger Source)

Scheduled tasks are not a new core capability — they add a third trigger path to `AgentService`. CLI and Web Service are "human-pushed" — someone initiates a call; `AgentScheduler` is "clock-pushed" — it generates a message on a cron schedule. The call chain is exactly the same as human-pushed; the `ReActLoop` never knows which entry a message came from.

**The `AgentScheduler` module (in `oryxos-core`).** Built on Spring's `ThreadPoolTaskScheduler` + `CronTrigger` with dynamically registered tasks — not the static `@Scheduled` annotation, because trigger rules are generated dynamically from Profile configuration, which compile-time annotations cannot do. The Profile's new `schedules` field declares cron expressions, time zones, and the message to send the agent; at startup OryxOS scans every Profile's `schedules` field and registers each one.

**Concurrency control.** Each scheduled task uses an in-process `ReentrantLock` (keyed by task id) to prevent overlapping runs of the same task — if the previous run hasn't finished when the next trigger fires, the trigger is skipped: no queueing, no parallel duplicates. The core stage is single-instance deployment, so this lock only solves "no overlap within one process"; it is **not** a distributed lock. Distributed coordination for multi-instance deployment comes in the extension stage.

**Failure handling.** A failing scheduled task only logs; it must never crash the scheduler itself or affect future triggers. The failed invocation still goes through the complete `llm_calls`/`tool_invocations` audit path inside `AgentService.process` — no different from a human-pushed failure.

**Session identity.** Clock-pushed runs also land in sessions: `session_id` follows the existing formula (channel + user + profile jointly generated, see 9.2), with channel and user fixed as `scheduler`. Successive scheduled runs of the same Profile reuse one session — history accumulates naturally, bounded by `max_history_turns` truncation. No new concepts are introduced for clock-push.

**State persistence and manageability.** "Runs on time" alone is not enough — operators need to see which scheduled tasks exist, how many times they've run, whether the last run succeeded; they also need to manually trigger a run and temporarily disable a task. So task state and execution history land in SQLite (surviving restarts), managed as first-class citizens:

- **Two tables** (hand-maintained DDL, see 9.2): `scheduled_tasks` stores registration info and run state (`task_id` PK, `profile_name`, `cron`, `zone`, `message`, `enabled`, `next_run_at`, `last_run_at`, `last_status`, `run_count`); `task_executions` stores the history of each execution (success and failure both recorded: `task_id`, `session_id`, `started_at`, `success`, `error_message`, `duration_ms`). The source of truth for definitions remains the `schedules` in skill/Profile — these tables store only "state + history"; on restart, tasks re-register from files.
- **Contract in core, implementation in storage** (dependency inversion): the `ScheduledTaskStore` interface (`register`/`recordExecution`/`isEnabled`/`setEnabled`/`list`/`executions`) lives in `oryxos-core` and `AgentScheduler` depends on it; the JPA implementation `JpaScheduledTaskStore` lives in `oryxos-storage`. `AgentScheduler` calls `register` during startup scans, and `recordExecution` on every execution — same audit lineage as the constitution. `runOnce` checks `isEnabled` first (disabled tasks are skipped and not recorded); the console's "run now" goes through `runNow`, triggering once regardless of enabled state.
- **Four management endpoints** (`ScheduleApiController`, prefix `/api/v1/schedules`): `GET /schedules` lists tasks and state, `GET /schedules/{id}/executions` shows execution history, `POST /schedules/{id}/run` triggers immediately, `PUT /schedules/{id}` enables/disables.

**Core stage vs extension stage boundary.** In the core stage, schedule **definitions** can only be written in `AGENT.md` frontmatter, registered at process startup — changing a cron or adding a task requires a restart (or reload). What ships additionally is **state persistence and run control** (view / history / run-now / enable-disable), not API-driven cron definition changes. The complete closed loop — "a business team uploads an agent directory (with `schedules` frontmatter) via Web Service, thereby defining a new agent that runs automatically on schedule" — depends on two extension-stage capabilities from 7.3 (the agent-directory upload endpoint with one-sentence generation, and `AgentScheduler`'s runtime add/remove endpoints). In the core stage this chain works via manual directory drops; the extension stage makes it pure-API and restart-free.

### 8.6 Three Run Modes

| Command | Mode | Description |
| --- | --- | --- |
| `oryxos chat` | Interactive chat | Local CLI interaction |
| `oryxos serve` | Web Service | Starts the REST API service (scheduled tasks stay resident with `serve`/`gateway`) |
| `oryxos gateway` | Daemon | Serves multiple channels simultaneously |

All three modes share the same Profile configuration and session storage; only the access layer differs.

### 8.7 The CLI Tool

**The `OryxOsCli` module.** The Picocli CLI entry — the `main` function of all of OryxOS — registering 12 subcommands:

```
init
status
chat
serve
gateway
profile list / create / show / delete
provider list
tool list
session list
```

Each subcommand is one `@Command` class. Commands that don't need the Spring context (`init`, `profile list`) operate on files directly for fast startup; commands needing LLM calls (`chat`, `serve`, `gateway`) boot the Spring context.

### 8.8 Configuration & Secrets Loading

**The `ConfigLoader` module** uniformly loads sensitive configuration — LLM API keys, provider credentials, MCP server credentials. The core stage is basic: sensitive config is injected via environment variables or a separate local config file, never hardcoded in Profile YAML (Profiles use `${ENV_VAR}` placeholders, resolved at load time); loading performs basic validation (required fields, format) with clear errors for missing or invalid values. Full encrypted storage, key rotation, and enterprise KMS/Vault integration are extension features. This module stands alone because, for an enterprise-grade foundation, config and secrets loading/validation is a day-one responsibility that must not scatter across modules ownerless.

---

## 9. Data Persistence

### 9.1 Persistence Choices

The core stage picks **SQLite** + **Spring Data JPA** for relational persistence, and a **`MEMORY.md`** file with keyword retrieval for long-term memory.

**Why no vector database in the core stage:** LanceDB does vectors + full-text well and is a natural upgrade direction for memory, but its Java local-embedded support is still in development — the current Java SDK only supports remote Cloud or Enterprise, which doesn't fit OryxOS's single-binary positioning. Other vector stores (Qdrant, Chroma, Milvus) all need external processes; pgvector needs an external PostgreSQL. JVector, a pure-Java embedded vector library, is another path but its maturity is unproven.

The core-stage judgment: run the shortest path with SQLite + `MEMORY.md` first, letting implementers master the Agent OS's core mechanisms; retrieval-experience optimizations like vector search go to the extension stage.

**Extension-stage upgrade paths:**

- **Option A:** switch to LanceDB Java local-embedded once GA — keeps single-binary
- **Option B:** connect PostgreSQL pgvector — one more PG service to deploy; most mature community
- **Option C:** dual-write SQLite with a JVector pure-Java embedded vector index — keeps single-binary

The choice is decided in the extension stage. The core-stage `LongTermMemory` interface already reserves upgrade headroom (`recallByKeyword` can upgrade to a `recall` with `mode`), so switching底层 doesn't affect upper tools.

### 9.2 SQLite Relational Data

Integrated via Spring Data JPA; `application.yaml` points the datasource at `.oryxos/oryxos.db`.

> **Engineering risk note:** SQLite's own `ALTER TABLE` capability is limited, and `hibernate.ddl-auto=update` supports schema evolution weakly on SQLite. Using `update` for first-time table creation in the core stage is fine, but do not rely on `update` for later schema evolution — maintain DDL scripts manually or introduce Flyway/Liquibase.

Five core tables:

1. **`sessions`**: session metadata plus JSON-serialized conversation history
2. **`tool_invocations`**: one record per tool call
3. **`llm_calls`**: one record per LLM call
4. **`scheduled_tasks`**: scheduled-task registration info and run state (see 8.5)
5. **`task_executions`**: history of every scheduled-task execution, success and failure both recorded (see 8.5)

> **Adjustment over the original plan:** `tool_invocations` and `llm_calls` are written from the core stage (query APIs not required), because "auditable" is one of OryxOS's differentiating selling points — the audit data foundation should stand on day one; building audit later from raw logs would require re-parsing rework. Query APIs and audit reports go to the extension stage, but writing exists from the core stage.

**`sessions` entity fields:**

| Field | Description |
| --- | --- |
| `session_id` | Primary key, generated from channel + user + profile |
| `profile_name` | Associated Profile |
| `channel` | Access channel |
| `user_id` | User identity |
| `messages_json` | JSON-serialized conversation history |
| `status` | `active` / `archived` |
| `created_at` | Creation time |
| `last_active_at` | Last active time |
| `archived_at` | Archive time |

**`scheduled_tasks` entity fields:**

| Field | Description |
| --- | --- |
| `task_id` | Primary key — the schedule's id (declared in the Profile/Skill `schedules`) |
| `profile_name` | Owning Profile |
| `cron` | Cron expression |
| `zone` | Time zone |
| `message` | Message sent to the agent on trigger |
| `enabled` | Whether enabled (console switch, default enabled) |
| `next_run_at` | Next trigger time |
| `last_run_at` | Last trigger time |
| `last_status` | Last result: `success` / `failed` |
| `run_count` | Total trigger count |
| `updated_at` | State update time |

**`task_executions` entity fields:**

| Field | Description |
| --- | --- |
| `id` | Primary key, auto-increment |
| `task_id` | References `scheduled_tasks` |
| `session_id` | The clock-pushed session used for this trigger |
| `started_at` | Start time |
| `success` | Whether successful |
| `error_message` | Failure info (nullable) |
| `duration_ms` | Execution duration |

### 9.3 Filesystem Data

Several kinds of data in `.oryxos/` live on the filesystem, not in SQLite: agent directories (`AGENT.md` + scripts / sub-instructions), Bootstrap files, memory (`MEMORY.md`), MCP configuration, and logs. The filesystem's advantages: users can edit directly, track with git, and back up easily. Agent directories and Bootstrap files — data users actively maintain — are friendlier on the filesystem than in a database.

---

## 10. Project Structure

OryxOS is a Maven multi-module project with 9 modules:

| Module | Responsibility |
| --- | --- |
| `oryxos-core` | Core abstractions and interfaces: `OryxTool` interface, `Session`, `Profile`, `ContextLoader`, `AgentLoader` (scans `.oryxos/agents/`, `deriveProfile`), `ReActLoop`, `PromptBuilder`, `ToolExecutor`, `AgentService`, `AgentScheduler` (scheduled triggers), `AgentLifecycleService` (extension stage — orchestrates "define an agent: directory to disk + derive Profile + register + Scheduler") |
| `oryxos-provider` | Capability 1: `ProviderService`, function-calling adaptation, provider configuration (explicit provider name → `ChatModel` mapping) |
| `oryxos-memory` | Capability 3: `MemoryService` unified facade, `LongTermMemory`, `MemoryTools` (`save_memory` / `recall_memory`) |
| `oryxos-tool` | Capability 4: built-in tools (`FileTools`, `ShellTools`, `HttpTools`, `NotifyTools`), `McpClientService`, `McpToolAdapter`, `ToolRegistry`, `Sandbox` interface + `WhitelistSandbox` implementation, `NotifyChannelAdapter` interface + `WebhookNotifyAdapter` implementation (three-in-one module) |
| `oryxos-channel-cli` | CLI channel: `CliChannel`, the `oryxos chat` command implementation |
| `oryxos-web` | Capability 5: `WebServer`, 6 `ApiController`s, `GlobalExceptionHandler`, OpenAPI docs |
| `oryxos-storage` | Persistence layer: SQLite, `SessionRepository`, `ToolInvocationRepository`, `LlmCallRepository` |
| `oryxos-cli` | CLI entry: Picocli main entry, 12 subcommands, `ConfigLoader` |
| `oryxos-boot` | Spring Boot startup module: main class, auto-configuration, dependency aggregation |

Modules are decoupled through interfaces. Adding a new channel or tool implementation in the extension stage means adding a new module without touching core; all channel modules call `oryxos-web`'s agent endpoints underneath.

Packaging:

```bash
mvn clean package
```

This produces a fat JAR started with `java -jar`; the extension stage compiles to a native binary via **GraalVM Native Image**.
