---
title: Quick Start
description: Build OryxOS, initialize the workspace, and run your first agent
---

# Quick Start

## Requirements

- **JDK 21+**
- **Maven 3.8+**

## Build

```bash
git clone https://github.com/oryxos/oryxos.git
cd oryxos
mvn clean package
```

The output is a single fat JAR: `oryxos-boot/target/oryxos.jar`.

## Initialize the Workspace

```bash
java -jar oryxos.jar init    # creates the .oryxos/ workspace in the current directory
```

Resulting layout:

```
.oryxos/
├── agents/            # one subdirectory = one Agent (AGENT.md + optional skills/ scripts/)
├── memory/
│   └── MEMORY.md      # long-term memory (core section + archival section)
├── mcp_servers.yaml   # MCP server configuration
├── sessions/          # session data
├── logs/              # structured logs
├── AGENTS.md          # Bootstrap: project-level agent behavior notes
├── SOUL.md            # Bootstrap: default agent persona
├── USER.md            # Bootstrap: user preferences
└── oryxos.db          # SQLite
```

The three Bootstrap files are loaded into the system prompt when an agent starts, so it knows the project background, its identity, and user preferences.

## Configure the LLM Key

Sensitive configuration always comes from environment variables — never plaintext on disk:

```bash
export DEEPSEEK_API_KEY=sk-xxx
```

Profiles use `${ENV_VAR}` placeholders, resolved from the environment at load time, with clear errors for missing or invalid values.

## Three Run Modes

| Mode | Command | Description |
| --- | --- | --- |
| Interactive chat | `oryxos chat` | Interactive multi-turn conversation — the main way to develop and debug |
| HTTP API | `oryxos serve` | Exposes the REST API on a port (default 8080) |
| Daemon | `oryxos gateway` | Long-running process serving multiple channels |

All three modes share the same configuration and session storage; only the access layer differs.

```bash
java -jar oryxos.jar chat                  # interactive chat
java -jar oryxos.jar chat --profile <name> # chat with a specific agent
java -jar oryxos.jar serve                 # start the REST API (:8080)
```

## CLI Commands (12)

| Category | Command | Description |
| --- | --- | --- |
| Startup & status | `oryxos init` | Initialize the workspace |
| Startup & status | `oryxos status` | Show configuration and runtime status |
| Startup & status | `oryxos chat [--profile <name>]` | Interactive chat |
| Startup & status | `oryxos serve` | Start the HTTP API service |
| Startup & status | `oryxos gateway` | Start the multi-channel daemon |
| Profile management | `oryxos profile list / create / show / delete` | Manage agent profiles |
| Queries | `oryxos provider list` | List configured providers |
| Queries | `oryxos tool list` | List registered tools |
| Queries | `oryxos session list` | List session history |

## REST API (10 Core Endpoints)

```bash
# Session management
curl -X POST localhost:8080/api/v1/sessions
curl -X POST localhost:8080/api/v1/sessions/{id}/messages
curl      localhost:8080/api/v1/sessions/{id}
curl -X DELETE localhost:8080/api/v1/sessions/{id}

# Stateless agent invocation
curl -X POST localhost:8080/api/v1/agents/{name}/invoke

# Information
curl localhost:8080/api/v1/profiles
curl localhost:8080/api/v1/memory
curl localhost:8080/api/v1/tools

# System status
curl localhost:8080/api/v1/health
curl localhost:8080/api/v1/info
```

## Define Your First Agent

Drop a directory into `.oryxos/agents/` — that directory is an agent:

```bash
mkdir -p .oryxos/agents/daily-weather
```

```markdown
---
name: daily-weather
provider: { name: deepseek, model: deepseek-chat }
tools: [http_get, notify]
schedules:
  - cron: "0 0 8 * * *"
    message: "Check Beijing weather and give outfit advice"
notify_channels:
  - type: webhook
    url: "${WECOM_WEBHOOK_URL}"
---
Every morning, check Beijing weather, compose outfit advice, and push it to the ops group.
```

- The **frontmatter** is this agent's profile (model, tools, schedule, notify channels)
- The **body** is the task instruction, injected into the system prompt
- `schedules` makes it run automatically at 8am (cron-triggered); you can also trigger it manually anytime via `oryxos chat` — both paths go through the same `AgentService` pipeline

The directory can be richer: `skills/*.md` sub-instructions and `scripts/` scripts, fetched on demand by the LLM through the built-in `read_file` / `shell` tools (progressive disclosure). See [Defining an Agent](/en/docs/tech/define-agent).

## Next Steps

- [Core Concepts](/en/docs/concepts): one directory = one agent; three triggers, one entry; inbound Channel ≠ outbound Notify
- [Acceptance Demos & Timeline](/en/docs/tech/demos): daily weather, daily tech digest, daily GitHub digest — three complete examples
- [Five Core Capabilities](/en/docs/demand/capabilities): everything the foundation offers
