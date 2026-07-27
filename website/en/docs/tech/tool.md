---
title: Tool System
description: The OryxTool abstraction, nine built-in tools, three tiers of Plugin Tools, Sandbox, and NotifyTools
---

# Tool System

## 6. Capability 4: The Tool System

Tools are external capabilities an agent can call. OryxOS tools come in two categories: **built-in tools** provided by OryxOS, and **Plugin Tools** extended by business teams. Plugin Tools have three access tiers, ordered from lowest to highest barrier.

> **Module adjustment note:** In the core stage, everything tool-related merges into one `oryxos-tool` module (built-in tools, MCP client, `ToolRegistry`, and Sandbox all live inside) — not split into builtin/skill/mcp modules. They share the same `OryxTool` abstraction and `ToolRegistry`, with high coupling, so a fine split is unnecessary at the core stage.
>
> Also, an agent directory (`AGENT.md` and its `skills/` sub-instructions) is strictly speaking not a tool but a *context source* (the body injects into the system prompt; sub-instructions are read on demand via `read_file`, see 11.1). Therefore `AgentLoader`/`ContextLoader` do not belong to the tool system — they sit at the context-loading layer (see 8.3). This keeps concepts clean.

### 6.1 The OryxTool Abstraction

OryxOS's internal unified tool interface. Built-in tools, `@Tool`-annotated Plugin Tools, and MCP tools are all wrapped as **`OryxTool`** instances registered into the `ToolRegistry`; the ReAct loop never perceives a tool's origin.

The `OryxTool` interface defines four core methods:

- `getName`
- `getDescription`
- `getInputSchema` (JSON Schema)
- `execute` (accepts JSON input, returns `ToolResult`)

`ToolResult` carries a success flag, result content, error info, and a retryable flag.

### 6.2 Built-in Tools (Nine)

The core stage provides nine built-in tools in five groups:

- **`FileTools`**: `read_file`, `write_file`, `list_dir` — call `Sandbox.enforce(...)` for path-whitelist checks before executing
- **`ShellTools`**: the `shell` tool executes bash commands with timeout and command whitelist
- **`HttpTools`**: `http_get`, `http_post` — with domain whitelist
- **`MemoryTools`**: `save_memory`, `recall_memory` (owned by the Memory module, but registered as built-in tools)
- **`NotifyTools`**: `notify` (push a message to the notify channels configured in the Profile, see 6.8)

These nine cover the shortest path for "agents that can read/write files, run commands, call external APIs, remember things, and push notifications."

### 6.3 Plugin Tool Tier 1: Zero-code — AGENT.md Directory + Reused MCP

OryxOS's **recommended** tier. Business teams write no code — they write an agent directory describing what to do, and the LLM understands the task and composes MCP tool calls itself.

Defining an agent = writing a directory `.oryxos/agents/<name>/` (borrowing Anthropic Agent Skills' directory shape, see Chapter 11): `AGENT.md` = frontmatter (this agent's own profile) + task-instruction body, plus optional `skills/*.md` (sub-instructions), `scripts/*.py`, `REFERENCE.md`, and other resources. One directory is one self-contained agent that only calls the foundation's system capabilities.

Loading follows **progressive disclosure within a single agent**: the `AGENT.md` body enters the system prompt; the sub-instructions / scripts / references it points to are fetched on demand via the foundation's `read_file`/`shell`. After reading them, the LLM understands the task, decides which MCP tools to call, and composes the work itself — OryxOS does not parse task steps and is not a workflow engine; all logic is left to the LLM.

> Note: parsing `AGENT.md` is the job of `AgentLoader` / `ContextLoader` (8.3), not the tool module — it is an input source for prompts, not an executable tool (see Constitution Principle 4 and 11.1).

### 6.4 Plugin Tool Tier 2: Write Your Own MCP Server

Business teams write MCP servers in any language, exposing tools over the MCP protocol; OryxOS connects as an MCP client. MCP servers are configured in `.oryxos/mcp_servers.yaml`, declaring `name`, `transport`, `command`, `env`.

**The `McpClientService` submodule.** Maintains MCP server connections and tool registration. At startup, OryxOS connects to all configured MCP servers, calls `tools/list` for their tool lists, wraps each MCP tool as an `OryxTool` registered into the `ToolRegistry`, and handles server loss, timeouts, and error recovery.

**The `McpToolAdapter` submodule.** Adapts MCP tools to the `OryxTool` interface. On invocation, calls are forwarded to the corresponding MCP server over the MCP protocol (JSON-RPC over stdio or SSE), and results are wrapped as `ToolResult`.

### 6.5 Plugin Tool Tier 3: Write a Java Spring Bean

Annotate Java methods with Spring AI's `@Tool`; OryxOS auto-scans and registers them at startup. This is the most engineering but the deepest integration — suitable when you need to call in-house Java services directly, reuse existing Spring Beans, or integrate with Spring Security for permission control. Written exactly like OryxOS built-in tools, they invoke Java methods in-process — no MCP protocol, no separate process, no serialization — with the best performance.

### 6.6 ToolRegistry

Manages all tools uniformly. At startup, the Spring container scans all `@Tool`-annotated methods (built-in tools and tier-3 Plugin Tools), adds tools registered by the MCP client (tier 2), and wraps everything as `OryxTool` instances. When a Profile starts an agent, its `tools` field filters the available subset from the registry.

### 6.7 Sandbox Checks

The Sandbox follows the "interface first" principle: define an abstract interface carrying no implementation details, hang one implementation behind it in the core stage, and only add implementation classes for heavier isolation later — without changing the interface or its callers.

**The `Sandbox` interface.** One method, expressing the intent "execute an action in a controlled environment":

```text
Sandbox.enforce(SandboxAction action)

SandboxAction  = { type: ActionType, target: String }
ActionType     = FILE_READ | FILE_WRITE | SHELL_COMMAND | HTTP_REQUEST
```

> `ActionType` has four values (file read / file write / shell command / HTTP request) — reads and writes are separated to allow read/write-granular permissions later; `WhitelistSandbox.enforce` routes both `FILE_READ` and `FILE_WRITE` to `checkFilePath`.

No tier-specific words like "whitelist," "container image," or "VM config" appear in the interface signature — reverse-fitting the heaviest microVM implementation against this signature should fit cleanly. That is the neutrality check.

**`WhitelistSandbox` (the only core-stage implementation).** Configured in `application.yaml` (`file.allowed_paths`, `shell.allowed_commands`, `http.allowed_domains`); internally routes by `ActionType` to three private check methods:

- `checkFilePath` (normalize the path, then compare against the whitelist — must handle `../` traversal)
- `checkShellCommand` (extract the command's first token and compare against the whitelist)
- `checkHttpUrl` (parse the host, then wildcard-match)

Any failed check throws `SandboxViolationException` and tool execution aborts; the exception reuses `ToolExecutor`'s existing failure-audit path into `tool_invocations` (`success=false`, `error_message`) — no separate audit logic needed for the sandbox.

`FileTools`, `ShellTools`, and `HttpTools` call `sandbox.enforce(...)` at the start of their `execute` methods; real IO proceeds only after the check passes.

**Signal-driven upgrades in the extension stage — interface unchanged, only new implementations:**

| Stage | Implementation | Upgrade signal |
| --- | --- | --- |
| Core stage | `WhitelistSandbox` (application-layer path/pattern whitelist) | — |
| Extension 1 | Container isolation (namespace + cgroups + seccomp) | Running relatively untrusted code, or multi-tenancy |
| Extension 2 | microVM (Firecracker / Kata / gVisor) | Running fully untrusted code, or large-scale multi-tenancy |

> **Point 1:** application-layer whitelists are a "deterrent-grade" defense — they stop model mistakes, not determined bypass. Do not run fully untrusted code or offer multi-tenancy on them in the core stage.
>
> **Point 2:** Sandbox + whitelists are the core stage's *only* tool-governance mechanism; Profile-level Tool Policy (which agent may use which tools) is an extension feature. The Profile `tools` field already limits an agent's tool subset in the core stage — a proto-form of tool governance; complete allow/deny policies come later.

### 6.8 Notification Push (`NotifyTools`)

In one sentence: **inbound has the Channel Adapter answering "how do messages come in"; outbound lacked a symmetric thing answering "how do messages go out" — `NotifyTools` fills exactly that.**

Before the two acceptance demos appeared, OryxOS never truly needed to "push a message out proactively" — with CLI and Web Service, someone sends a message in and the agent replies synchronously; no push mechanism required. But scheduled demos like "daily weather" and "daily tech digest" are different: `AgentScheduler` fires on time with nobody waiting for a response, so the agent must **proactively** deliver results where people can see them (enterprise IM groups) — that requires a "push out" capability.

**What happens without this module:** every business team defining an agent would hand-write "call `http_post` against this webhook URL" in their skill, or find and configure a WeCom/Lark MCP server themselves — each skill writing its own copy: repetitive, inconsistent, and contrary to the "interface first" principle emphasized for Sandbox and Memory.

**The `NotifyChannelAdapter` interface.** Same thinking as the Sandbox in 6.7: first define an abstract interface carrying no channel-specific details, expressing the intent "deliver a piece of content to a notify target":

```text
NotifyChannelAdapter.send(NotifyTarget target, String content)

NotifyTarget = { channelType: String, config: Map<String, String> }
```

**`WebhookNotifyAdapter` (the only core-stage implementation).** A generic HTTP webhook covers all scenarios — WeCom, Lark, and DingTalk group bots all provide webhook URLs, so the core stage skips their dedicated APIs (signature algorithms, AccessToken refresh — those auth details come later): it wraps `content` into the JSON shape the webhook expects and sends one POST. Before sending, it passes `Sandbox.enforce(new SandboxAction(HTTP_REQUEST, url))` domain-whitelist validation, sharing the same `http.allowed_domains` config as `http_post` — no new sandbox logic.

**`NotifyTools` (built-in tool, in `oryxos-tool`):**

```text
@Tool notify(content: String, channel: String = default channel)
```

The `channel` parameter maps to the Profile's new `notify_channels` field (declaring which targets this agent may push to; each entry has a `type` and channel-specific config like `url`). Most of the time the LLM only passes `content` — it never needs to know the concrete webhook address, because **addresses are runtime configuration, not conversation information** — the same design consideration as keeping the sandbox domain whitelist in Profile/`application.yaml` instead of interface signatures.

**Relationship with existing mechanisms:**

- **Audit:** `notify` follows `ToolExecutor`'s existing success/failure audit path into `tool_invocations`, like every other tool — no new audit logic.
- **Sandbox:** reuses `Sandbox.enforce(HTTP_REQUEST, ...)` — no new sandbox concepts.
- **Symmetric to inbound channels, but not the same thing:** `ChannelAdapter` (8.4) answers "what triggers the agent to start running"; `NotifyChannelAdapter` answers "where results go when the agent finishes" — opposite semantic directions, modeled separately, not merged into one abstraction. The same enterprise IM group can be one agent's inbound channel and another agent's outbound notify target.
- **Boundary with Plugin Tool tier 2 (MCP):** if a business team needs richer formats like WeCom's official rich-text card messages, it can still write a dedicated MCP server via tier 2 — both paths coexist. `notify` merely unifies the repetitive "most common plaintext/webhook push" work; it does not eat tier 2's scenarios.
