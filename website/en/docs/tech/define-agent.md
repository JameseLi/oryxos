---
title: Defining an Agent
description: One directory = one agent — borrowing the Agent Skills shape, deriving Profiles, and progressive disclosure
---

# Defining an Agent

## 11. Defining an Agent: One Directory + Web Service

The previous ten chapters are the **foundation** — the engine, capabilities, and supporting facilities that let any agent run reliably. The foundation itself is not any concrete business agent. This chapter explains how a business agent actually gets "defined" on top of the foundation, and through which entry that act is exposed. The shape **borrows from Anthropic Agent Skills** (directory + progressive disclosure), but in OryxOS, such a directory defines an **Agent**.

### 11.1 Terminology: One Directory = One Agent

**Keep the two layers — foundation and agent — straight.** This is the most important point of the chapter:

- **Foundation = system base capabilities**: Provider, ReAct, built-in tools (`read_file`/`shell`/`http_get`/`notify`/`save_memory`…), Memory, Sandbox, scheduling, Web (Chapters 1–10). Shared by all agents.
- **Agent = one directory** `.oryxos/agents/<name>/`: `AGENT.md` (frontmatter = this agent's own profile: `name`/`description`/`provider`/`model`/`tools`/`notify_channels`/`schedules`; body = task instructions) + optional `skills/*.md` (sub-instructions), `scripts/` (scripts), `REFERENCE.md` (references). A self-sufficient business agent — **carrying everything itself, with no separate Profile YAML** (`.oryxos/profiles/` is eliminated).

**Borrow Anthropic Agent Skills' shape, but define an Agent.** Anthropic calls such a directory a Skill (a loadable capability of Claude, the great agent); what we borrow is the directory's *shape*, not the naming — in OryxOS, **one directory = one Agent**; "skill" is merely an optional component inside an agent directory (sub-instructions). **There is no cross-agent shared capability library, no `use_skill`, no global index.** Every agent is independent and self-sufficient, calling only the foundation's system capabilities.

**Derived Profile**: everything in the foundation (Chapters 1–10) consumes `Profile`, so `AgentLoader.deriveProfile(agentDir)` maps the `AGENT.md` frontmatter into a `Profile` — letting an agent directory **reuse the entire foundation with zero changes**.

**Progressive disclosure (contained within a single agent)**: the agent's **body** enters the system prompt when triggered (it is this agent's "persona + what to do"); the directory's **sub-instructions / references / scripts are not preloaded** — they are **fetched on demand via the foundation's existing capabilities**, guided by the body: read sub-instructions/references with `read_file`, run scripts with `shell`/`python` (script *output* enters context, script *code* does not). No new tools, no capability library, no global index.

> **The bottom line holds (Constitution Principle 4):** the `AGENT.md` body is injected into the system prompt by `ContextLoader` (same layer as Bootstrap files); **an agent directory is not an executable tool** — its sub-resources are fetched through the foundation's existing `read_file`/`shell`; no new mechanism is invented.

### 11.2 Core Stage: One Directory Defines One Agent (Filesystem)

The core stage goes through the filesystem, in one step:

1. Place a directory in `.oryxos/agents/<name>/`: at minimum an `AGENT.md`; add `skills/*.md` and `scripts/` if sub-instructions or scripts are needed.
2. At startup, `AgentLoader` scans `.oryxos/agents/` and runs `deriveProfile` → `ProfileRegistry.register` for each directory; anything with `schedules` goes to `AgentScheduler`.
3. At runtime: the agent fires on schedule; the body enters the system prompt; sub-instructions / references / scripts are fetched on demand via `read_file`/`shell`; `ContextLoader` / `read_file` re-read every time with no caching — edits to the body take effect immediately.

With runtime registration (the 11.3 hooks are established in the core stage), new agents go live without restarts.

### 11.3 Extension Stage: `/api/v1/agents` + One-sentence Generation + Live Watch + File Browser

Business systems and operators manage everything purely via API or pages, never touching the filesystem. Externally there is only **one kind of resource — Agent** (one directory). `AgentLifecycleService` (extending the existing `AgentApiController`):

- `POST /api/v1/agents/generate`: generate an **`AGENT.md` draft** from one sentence via LLM, returned as-is (not written to disk, not registered) — for page preview and manual editing (sensitive items like cron/tools deserve a human glance)
- `POST /api/v1/agents`: write the agent directory (`AGENT.md` [+ scripts / sub-instructions]) → `deriveProfile` → register
- `GET /api/v1/agents` / `GET /{name}`: query defined agents
- `PUT /api/v1/agents/{name}`: update body / provider / notify (overwrite takes effect immediately) and/or `schedules` (on change, unregister old handles before registering new ones)
- `DELETE /api/v1/agents/{name}`: unregister schedules → remove from index → archive the **entire agent directory** to `.oryxos/archive/` (never physically deleted)
- `POST /api/v1/agents/{name}/invoke`: the existing stateless invocation endpoint, unchanged

After an agent directory is written, the path it walks — `AgentLoader.deriveProfile → ProfileRegistry.register → AgentScheduler.registerProfile` — is **the same code** as the startup scan, guaranteeing "agents created via API behave exactly like agents created by dropping directories." The runtime registration methods of `ProfileRegistry` (`register`/`remove`/`exists`) and `AgentScheduler` (`registerProfile`/`unregisterProfile` + the `scheduledTasks` handle table) are established in the core stage and called directly in this stage; `generate` goes through the existing `ProviderService` (and lands in `llm_calls` audit).

**One directory, two ingestion paths + live watch.** `.oryxos/agents/` is the **single source of truth**, filled by two converging paths: API upload (validate + write agent directory) and manual directory drops (scp/git/editors). This stage adds `WorkspaceWatcher` (a daemon thread in the assembly layer using JDK `WatchService`; full scan at startup + live watching of `.oryxos/agents/` changes afterward) — **it is the unified registration entry**: any agent directory add/change/delete calls `AgentLifecycleService.register(agentDir)` (the **same method** the API upload path calls after writing the directory) or unregisters. Thus "upload means live = drop-directory means live, restart-free throughout." In addition, `WorkspaceApiController` provides **read-only** workspace file browsing (`GET /workspace/tree` lists agent directory trees, `GET /workspace/file?path=` reads file contents — **path traversal protection is mandatory**: `normalize()` then `startsWith(root)` check), letting the console's "workspace" page drill into an agent directory to view its `AGENT.md`/scripts/sub-instructions.

### 11.4 Why These Things Ship as One Package

Doing any one of them alone cannot complete the loop of "say one sentence / call one API / drop one directory → a new agent that runs itself on schedule goes live": without directory writing, no Profile can be derived; without runtime registration, new agents wait for restarts; without schedule registration, "runs itself" is empty talk; without one-sentence generation, operators still hand-write frontmatter. So they ship as one batch — not split halfway.

### 11.5 Three Examples

The three demos in the next chapter — daily weather, daily tech digest, daily GitHub digest — each demonstrate one level of **agent-directory richness**: **the weather agent is a bare `AGENT.md`** (instructions in the body, using built-in HTTP tool + `schedules`); **the tech-digest agent is `AGENT.md` + `skills/` sub-instructions** (the digest-format spec lives in a separate file the agent reads on demand via `read_file`, with MCP / Memory); **the GitHub-digest agent is `AGENT.md` + `scripts/` scripts** (including `scripts/github_trending.py`, which the agent runs via `shell` for deterministic data). In the core stage these run through 11.2's manual path; after upgrading to 11.3's unified API in the extension stage, new agents go live without restarts.
