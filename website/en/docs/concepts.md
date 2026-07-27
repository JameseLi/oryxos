---
title: Core Concepts
description: One directory = one agent, Profile, three triggers one entry, inbound Channel ≠ outbound Notify
---

# Core Concepts

## What Matters Most

### One Directory = One Agent

Defining an agent means writing a directory `.oryxos/agents/<name>/`:

- `AGENT.md`: the **frontmatter** is this agent's own profile (`name` / `provider` / `tools` / `schedules` / `notify_channels`…); the **body** is the task instruction, injected into the system prompt
- Optional `skills/*.md` sub-instructions, `scripts/` scripts, `REFERENCE.md`

The shape borrows from Anthropic Agent Skills, but what it defines is an **Agent**: no cross-agent shared capability library, no `use_skill`, no global index. Every agent is self-contained and only calls the foundation's system capabilities. See [Defining an Agent](/en/docs/tech/define-agent).

### Progressive Disclosure

The `AGENT.md` body goes into the system prompt; sub-instructions, scripts, and references are **not preloaded** — the LLM fetches them on demand through the built-in `read_file` / `shell` tools, guided by the body. Sub-instructions only occupy context at that moment; script output enters context, script code does not. No new tools are invented for this.

### Profile

The runtime host configuration of an agent: which Provider/model it binds, which tools it may use, channels, and schedules. It is **derived** by `AgentLoader.deriveProfile` from the `AGENT.md` frontmatter — no separate hand-written Profile YAML. Multiple agents can coexist on one OryxOS instance — the minimal expression of an "OS" in the core stage.

### Three Triggers, One Entry

CLI and Web Service ("human-pushed") plus `AgentScheduler` (cron, "clock-pushed") all call the same `AgentService.process`; the ReAct loop never knows which entry a message came from. Cron-triggered sessions use fixed channel/user `scheduler`, and all scheduled runs of the same Profile reuse one session.

### Inbound Channel ≠ Outbound Notify

Opposite directions, modeled separately:

- `ChannelAdapter` (inbound): how messages come **in** — the core stage ships CLI; IM channels come in the extension stage
- `NotifyChannelAdapter` (outbound): where results go **to** — the core stage ships a Webhook implementation plus the built-in `notify` tool; WeCom/Lark/DingTalk group bots all provide webhook URLs

The same IM group can be one agent's inbound channel and another agent's outbound notify target.

### Audit Tables from Day One

The `tool_invocations` and `llm_calls` audit tables are **written from the core stage** (write-only, query APIs come later) — the data foundation for auditability is laid on day one, avoiding later rework of parsing logs back into tables.

## Glossary

| Term | Definition |
| --- | --- |
| **Agent** | A concrete business agent. "What it does" is defined by its agent directory (what and when); "how it runs" is bound by the derived Profile (which model, which tools, which schedule) |
| **Profile** | The runtime host configuration of an agent — a kernel-layer capability deciding "how it runs": bound LLM provider, allowed tools, channels, tool policy, schedules |
| **Provider** | An abstraction over LLM API services with a unified interface, so agents never know which vendor they call |
| **ReAct Loop** | The agent's core mechanism, Reason + Act: the LLM decides whether to call a tool, observes the result, and decides the next step until a final response |
| **Tool** | An external capability an agent can call. Built-in tools ship with OryxOS (file, shell, HTTP, memory, notify); Plugin Tools are extended by users |
| **Memory** | State an agent keeps across conversations, in three layers: session memory, long-term memory (MEMORY.md), episodic memory (extension stage) |
| **Channel** | An inbound message entry for agents: CLI, WeCom, Lark, DingTalk, Slack, etc. |
| **Web Service** | The complete REST API OryxOS exposes — the single integration surface for business systems |
| **Session** | The context container of one conversation, holding message history, current context, and temporary variables |
| **Sandbox** | The isolated environment for tool execution. The core stage uses application-layer whitelists; container isolation comes later |
| **Tool Policy** | Allow/deny rules controlling which tools an agent may use, configured per Profile (extension stage) |
| **Bootstrap** | Context files loaded into the system prompt: AGENTS.md (project-level notes), SOUL.md (agent persona), USER.md (user preferences) |
| **Workspace** | The working directory of an OryxOS instance, default `.oryxos/`, holding config, Bootstrap files, memory, sessions, and agent directories |

## How the Five Capabilities Relate

The five capabilities are not parallel modules: **Provider, Memory, and Tool feed the ReAct loop engine; the engine's output is served through three entries — CLI, Web Service, and the scheduler**. See [Architecture](/en/docs/tech/architecture).
