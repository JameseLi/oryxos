---
title: Core Features & Flows
description: The core-stage shortest path — workspace, Profile, Provider, ReAct, Memory, Tool, Channel, Web Service, Session, CLI — plus the five key flows
---

# Core Features & Flows

> Core features are the shortest path to be completed within the core stage, corresponding to the Agent OS runtime kernel. The goal: configure an agent via Profile, chat with it through the CLI, have it call LLMs and tools to complete tasks, and expose everything via REST API.

## Workspace Initialization

OryxOS's working directory is `.oryxos/`, created by the `oryxos init` command:

```bash
oryxos init   # creates the .oryxos/ workspace in the current directory
```

Resulting layout:

```
.oryxos/
├── agents/            # Agent directories
├── sessions/          # session history
├── logs/              # structured logs
├── memory/
│   └── MEMORY.md      # long-term memory file
├── AGENTS.md          # Bootstrap: project-level agent behavior notes
├── SOUL.md            # Bootstrap: default agent persona
├── USER.md            # Bootstrap: user preferences
└── mcp_servers.yaml   # MCP server configuration
```

- The three Bootstrap files are automatically loaded into the system prompt when an agent starts — it learns the project background, its identity, and user preferences
- `oryxos init` also generates a default configuration so users can start immediately

## Profile Configuration

A Profile is an agent's runtime host configuration in YAML — it decides which skill an agent binds, which Provider/model it uses, which tools it may use, which channel it binds, and whether it runs on a schedule. The Profile itself is a kernel-layer capability of the Agent OS (the foundation); "what this agent actually does" is defined by the skill it references. Together they form a complete business agent.

**Profile YAML structure:**

```yaml
name: string                    # Profile name
description: string             # Description

identity:
  agent_name: string            # Agent name
  prompt: string                # Persona / system prompt (or reference SOUL.md)

provider:
  name: string                  # Provider name (deepseek/qwen/kimi…)
  model: string                 # Model name
  temperature: float            # Temperature (optional)

tools:
  - string                      # Allowed tool names

skills:
  - string                      # Referenced SKILL.md files

mcp_servers:
  - string                      # Referenced MCP servers

channels:
  - name: string                # Channel name
    config: {}                  # Channel config

bootstrap:
  - string                      # Bootstrap files

settings:
  max_iterations: 10            # Max ReAct iterations
  max_history_turns: 20         # Max conversation history turns
```

**Profile management commands:**

```bash
oryxos profile create <name>    # create a new Profile
oryxos profile list             # list all Profiles
oryxos profile show <name>      # show Profile details
oryxos profile delete <name>    # delete a Profile
```

The core stage supports creating and managing multiple Profiles — multiple agents coexist on one OryxOS instance, the minimal expression of an "OS" in the core stage.

## Provider Abstraction (Capability 1)

The Provider is the unified abstraction for LLM calls. All LLM calls go through the Provider interface; agents never know which vendor they call.

The core stage builds directly on Spring AI Alibaba's `ChatClient`. Spring AI Alibaba already provides connectors for mainstream LLMs (DeepSeek, Qwen, ERNIE, Kimi, Zhipu, Hunyuan, Doubao, Anthropic, OpenAI…); OryxOS wraps them as Providers without reinventing the wheel.

Each Provider instance is configured with: provider name (deepseek, qwen, kimi…), model name, API key, and optional base URL.

**Not in the core stage**: fallback and hedge racing. On provider failure, an error is returned to the agent directly. Cost transparency is basic: every LLM call logs token usage, provider, and model.

## ReAct Loop (Capability 2: The Agent Brain)

The ReAct loop is the agent's core mechanism and OryxOS's most critical piece of code.

**Core algorithm (Reason + Act):**

```
Receive user message
  └─ Append to session history
     └─ Assemble prompt (system prompt + Bootstrap + history + available tools)
        └─ Call LLM provider for a response
           ├─ [No tool call] → return the final response
           └─ [Tool call(s)] → execute tools, append results to history → continue loop
```

Forced termination at the max iteration count (default 10).

**Implementation points:**

- The core loop is ~tens of lines of Java — self-implemented, not relying on Spring AI's agent abstraction
- Max iterations overridable per Profile
- Every LLM call and tool call is logged structurally

**Not in the core stage**: parallel tool calls, dynamic context compression, inter-agent task delegation.

## Three-Layer Memory (Capability 3)

**The core stage implements session + long-term layers (episodic in extension):**

### Session Memory

- Full history of the current conversation, identified jointly by channel + user + profile
- Session data persisted to local SQLite; recoverable after restart
- When context exceeds the LLM window, simply truncate earlier turns

### Long-Term Memory (Minimal)

- Stored in one Markdown file: `.oryxos/memory/MEMORY.md`, kept across all conversations
- The agent actively reads/writes it through two built-in tools:
  - `save_memory(content)`: append something to remember long-term to MEMORY.md
  - `recall_memory(query)`: keyword-search MEMORY.md for relevant content
- At agent startup, the entire MEMORY.md is injected into the system prompt as long-term context
- Simple truncation when the file exceeds 4000 characters (compression in extension stage)

**Not in the core stage**: automatic fact extraction from conversations, semantic retrieval (keyword matching only for now), episodic memory, Memory Wiki, contradiction detection.

**The core user experience**: after using OryxOS for a while, the agent naturally remembers your preferences, project info, and key decisions — no re-explaining next conversation. This is the core experience distinguishing an Agent OS from a chatbot.

## Tool System (Capability 4)

Tools are external capabilities an agent can call. The agent decides when to call which tool via LLM function calling; OryxOS handles tool registration, lookup, invocation, and result return.

### Built-in Tools (Core Stage)

| Tool | Type | Description |
| --- | --- | --- |
| `read_file` | File | Read file contents, path whitelist enforced |
| `write_file` | File | Write file contents, path whitelist enforced |
| `list_dir` | File | List directories, path whitelist enforced |
| `shell` | Shell | Execute bash commands, timeout + command whitelist |
| `http_get` / `http_post` | HTTP | Make HTTP requests, domain whitelist |
| `save_memory` | Memory | Append content to MEMORY.md |
| `recall_memory` | Memory | Keyword-search MEMORY.md |

### Plugin Tools (Business Extensions)

| Tier | Barrier | Recommendation | Scenario |
| --- | --- | --- | --- |
| **Tier 1**: SKILL.md + reuse MCP servers | Zero-code | ⭐⭐⭐ Recommended | Describe intent; the LLM composes existing capabilities |
| **Tier 2**: write your own MCP server | Light-code | ⭐⭐ | Integrate in-house systems, any language |
| **Tier 3**: Java `@Tool` Bean | Full-code | ⭐ | Deep integration, best performance |

> **Selection principle**: if tier 1 works, don't use tier 2; if tier 2 works, don't use tier 3.

**Zero-code example**: to build "push yesterday's GitHub PR review progress to Slack every morning," you only need to:

1. Write `daily-pr-digest.md` describing the task and when it triggers
2. Reuse the community `github-mcp` and `slack-mcp` servers
3. Configure a Profile referencing this skill and the two MCP servers

Not a single line of code in the whole process.

### Sandbox Isolation

The core stage implements application-layer whitelist checks:

- File operations: path whitelist
- Shell: command whitelist
- HTTP: domain whitelist
- Execution timeouts and resource limits

> Note: Java SecurityManager is **not** used — deprecated since JDK 17, unavailable in JDK 21. Full Docker/K8s container-level sandboxing is an extension feature.

## Channel Access

Channels are the agent's inbound message entries, solving "messages in, responses out." HTTP access belongs to Web Service, not channels.

The core stage ships one channel: **CLI Channel**, started via `oryxos chat`, supporting multi-turn conversation, context viewing, and tool-call records.

WeCom, Lark, DingTalk, Slack and other IM channels are extension features (high implementation complexity — OAuth and enterprise credentials — beyond the core-stage scope).

## Web Service (Capability 5)

Web Service is OryxOS's complete outward face; business systems integrate via REST API. This is the key capability distinguishing OryxOS from personal-positioned projects like OpenClaw and Hermes.

### Core-Stage Endpoints (10)

| Category | Endpoint | Description |
| --- | --- | --- |
| Sessions | `POST /api/v1/sessions` | Create session |
| Sessions | `POST /api/v1/sessions/{id}/messages` | Post message |
| Sessions | `GET /api/v1/sessions/{id}` | Get history |
| Sessions | `DELETE /api/v1/sessions/{id}` | Archive session |
| Agents | `POST /api/v1/agents/{name}/invoke` | Stateless invocation |
| Profiles | `GET /api/v1/profiles` | List profiles |
| Memory | `GET /api/v1/memory` | Query long-term memory |
| Tools | `GET /api/v1/tools` | List available tools |
| System | `GET /api/v1/health` | Health check |
| System | `GET /api/v1/info` | Runtime info |

### Extension-Stage Endpoints (15)

Profile show/reload/create/update/delete; Memory append/clear/search; tool describe and invocation history; LLM call history and token statistics; webhook triggers; streaming SSE responses; Prometheus metrics; OpenAPI spec.

**Not in the core stage**: authentication (assumes trusted intranet), streaming SSE, WebSocket, RBAC.

### Business Integration Patterns

| Pattern | How | Best for |
| --- | --- | --- |
| Synchronous call | `POST /agents/{name}/invoke` and wait | Stateless short tasks |
| Session continuity | Create a session, then post multiple messages | Continuous conversation |
| Webhook trigger | Alerting systems, CI/CD call agents via webhook | Event-driven |
| Cross-language integration | Any language that can make HTTP requests | General integration |

## Session Management

A session is the context container of one conversation between a user and an agent: start/end time, user identity, agent identity, message history, current context, temporary variables. The session ID is generated jointly by channel + user + agent.

In the core stage, session data persists to local SQLite (under `.oryxos/sessions/`). After restarting OryxOS, in-flight sessions resume. When session context exceeds the LLM window, earlier turns are simply truncated, keeping recent ones.

## Three Run Modes

| Mode | Command | Description |
| --- | --- | --- |
| Interactive chat | `oryxos chat` | Interactive multi-turn conversation; `--message "xxx"` sends one message and exits |
| HTTP API | `oryxos serve` | Serves the REST API on a port (default 8080) |
| Daemon | `oryxos gateway` | Resident daemon serving multiple channels |

All three modes share the same Profile configuration and session storage.

## CLI Tool (12 Commands)

| Category | Command | Description |
| --- | --- | --- |
| Startup & status | `oryxos init` | Initialize workspace |
| Startup & status | `oryxos status` | Show config and runtime status |
| Startup & status | `oryxos chat [--profile <name>]` | Interactive chat |
| Startup & status | `oryxos serve` | Start HTTP API service |
| Startup & status | `oryxos gateway` | Start multi-channel daemon |
| Profile | `oryxos profile list` | List all profiles |
| Profile | `oryxos profile create <name>` | Create profile |
| Profile | `oryxos profile show <name>` | Show profile details |
| Profile | `oryxos profile delete <name>` | Delete profile |
| Queries | `oryxos provider list` | List configured providers |
| Queries | `oryxos tool list` | List registered tools |
| Queries | `oryxos session list` | List session history |

## Configuration & Secrets Loading

Basic version in the core stage:

- Sensitive configuration is injected via **environment variables** or a separate local config file — never hardcoded in Profile YAML
- Profiles use `${ENV_VAR}` placeholders, resolved from the environment at load time
- Basic validation at load time (required fields, format) with clear errors for missing or illegal values

Full encrypted storage, key rotation, and enterprise KMS/Vault integration are extension features.

## Project Homepage

As an open-source project, OryxOS needs an independent homepage as its public face — explaining what OryxOS is, what it does, and how to use it, guiding developers to get started quickly. The homepage is built during the core stage and released alongside the core code as part of OryxOS 1.0. Recommended stack: VitePress, Astro, Docusaurus, or similar static site generators.

---

# Key Flows

## Flow 1: Workspace Initialization

```
User runs oryxos init
  → OryxOS creates .oryxos/ and its subdirectories
  → Creates three Bootstrap files (AGENTS.md, SOUL.md, USER.md)
  → Generates a default Profile
User edits Bootstrap files with project background, agent persona, user preferences
User configures the LLM provider API key and model
```

## Flow 2: Profile Creation and Agent Startup

```
User runs oryxos profile create <name>
  → OryxOS creates a new YAML under .oryxos/
User edits agent persona, provider, tool list, channel
User runs oryxos chat --profile <name>
  → OryxOS loads the Profile
  → Initializes the provider connection
  → Registers tools into the agent's tool pool
  → Loads Bootstrap files into the system prompt
  → Agent is ready for conversation
```

## Flow 3: Message Processing (Highest-Frequency Path)

```
A message arrives from a channel (CLI input or HTTP API call)
  → Channel adapter converts to the internal unified format
  → Agent queries session context
  → Assemble the LLM prompt (Bootstrap + history + available tools)
  → Call the LLM provider for a response
  → [Tool call present] → OryxOS executes the tool → result fed back to the LLM to continue
  → Final response sent back to the user via the channel adapter
  → All actions logged structurally
```

## Flow 4: Tool Invocation

```
The LLM specifies tool name and parameters via function calling
  → OryxOS finds the tool in the agent's tool pool
  → Parameter validation and whitelist checks
  → Built-in tool: executes inside the OryxOS process (under whitelist constraints)
  → MCP tool: forwarded to the corresponding MCP server via the MCP protocol
  → Execution result (success/failure, error info, retryable flag) returned to the agent
  → Agent continues reasoning with the tool result as the next LLM input
```

## Flow 5: Session Context Management

```
A user talks to an agent for the first time
  → OryxOS looks up an active session by channel+user+agent joint ID
  → [No active session] → create a new session with empty history
  → [Active session found] → restore session context
Subsequent messages append to the session history
  → [Context over limit] → truncate earlier turns, keep recent ones (summarization in extension)
Session idle timeout → ends; history archived and queryable
```
