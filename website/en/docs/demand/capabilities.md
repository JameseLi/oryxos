---
title: Five Core Capabilities
description: LLM integration, ReAct loop, three-layer memory, tool system, and Web Service — and the enterprise scenarios they compose
---

# Five Core Capabilities

OryxOS prioritizes five core capabilities, from which countless real enterprise needs can be composed. All five belong to the runtime kernel layer — "making a single agent run well." The multi-agent governance capabilities that make OryxOS a true "OS" (multi-tenancy, tool policy, audit, SSO) arrive in the extension and community stages.

## Capability 1: LLM Integration

Through a Provider abstraction layer, OryxOS connects to mainstream LLMs (DeepSeek, Qwen, Kimi, Zhipu, Hunyuan, Doubao, Anthropic, OpenAI, and more). Agents never know which vendor they are calling, and providers can be switched at runtime with no lock-in.

**What this enables:**

- Natural-language assistants for any business scenario — the agent understands intent and replies through the LLM
- The same agent using different models for different tasks — cheap models for simple tasks, strong models for complex ones
- Integration with enterprise self-hosted inference (Ollama, vLLM) — data never leaves the enterprise
- Multi-provider orchestration — planning with a cheap model, synthesis with a strong one, in a single report

## Capability 2: ReAct Loop

ReAct (Reason + Act) is the agent's core mechanism: given a task, the LLM decides whether and which tool to call, observes the result, and decides the next step — until it produces a final response.

**What this enables:**

- Agents decide when to call which tool on their own — no hardcoded flows
- Multi-step tasks complete in one conversation (read a file, analyze it, call an API, produce a report)
- Agents can roll back, retry, or switch tools when something fails
- Complex business processes need no upfront orchestration — the agent decides the execution path at runtime

## Capability 3: Three-Layer Memory

The agent remembers user preferences, projects, decisions, and conversation history. A three-layer design; the core stage implements session and long-term layers, with episodic memory in the extension stage:

| Layer | Description | Core stage |
| --- | --- | --- |
| Session memory | Full history of the current conversation, auto-compressed when too long | ✅ Implemented |
| Long-term memory | User preferences, project background, key facts — kept in MEMORY.md across conversations | ✅ Implemented (minimal) |
| Episodic memory | What was learned during each task — files modified, decisions made | ⏳ Extension stage |

**What this enables:**

- Agents remember user preferences across conversations ("I use Spring Boot, not Spring MVC")
- State persists during long tasks; interrupted conversations resume
- Multiple agents in a team share one user's preference memory
- Historical decisions are traceable ("why did we pick DeepSeek over Kimi last time" is in the memory)

## Capability 4: Plugin Tools + Built-in Toolset

Agents call tools to actually operate systems. OryxOS provides two categories:

- **Built-in tools**: fundamental tools shipped with OryxOS (read/write files, execute shell, make HTTP requests)
- **Plugin tools**: extended by business teams, in three tiers by barrier to entry

| Tier | Barrier | How | Best for |
| --- | --- | --- | --- |
| Zero-code | Lowest | Write SKILL.md + reuse community MCP servers | Business team describes intent; the LLM composes calls |
| Light-code | Medium | Write an MCP server in any language | Integrating in-house systems (ERP, CRM) |
| Full-code | Highest | Java Spring Bean with `@Tool` | Deep integration, best performance |

**What this enables:**

- Connect agents to enterprise ERP, CRM, CMDB — agents that do real enterprise work
- Connect GitHub, Jira, Confluence — build a研发 (R&D) assistant
- Connect Prometheus, Grafana, SSH — build ops self-healing
- Zero-code extension: SKILL.md + reused MCP — pure markdown ships a new scenario

## Capability 5: Web Service

OryxOS exposes all capabilities through a complete REST API. Business systems use the agent with a simple HTTP call, never caring about internals. Web Service is OryxOS's outward face — **the only channel through which enterprises embed AI capability into existing business systems**.

Six categories of API operations:

| Category | Endpoints |
| --- | --- |
| Session management | Create session, post message, get history, archive session |
| Agent invocation | Stateless one-shot invocation (streaming in extension stage) |
| Profile management | List profiles, view details, reload |
| Memory operations | Query long-term memory, manual write, clear |
| Tool information | List available tools, view metadata |
| System status | Health check, runtime metrics, provider status |

### About Channels

The core stage also has a basic module: Channel (message access). Channels solve "messages in, responses out"; the core stage ships only the CLI channel, with WeCom/Lark/DingTalk IM channels in the extension stage. Channels are a core module but not one of the "five core capabilities."

## Scenarios Composed from the Five Capabilities

| Scenario | LLM | ReAct | Memory | Tool | Web Service |
| --- | --- | --- | --- | --- | --- |
| Omnichannel support | Understand questions | Loop over knowledge base | Remember customer history | Connect CRM | HTTP into support system |
| Ops assistant | Analyze alerts | Query logs + restart | Remember past incidents | Prometheus / SSH | Webhook triggers |
| R&D assistant | Understand requirements | Read & modify code | Remember project conventions | GitHub / CI | IDE plugin |
| Knowledge management | Understand questions | Retrieve documents | Remember team conventions | Confluence | Intranet portal embed |
| Sales assistant | Build customer profile | CRM + business registry | Remember customer preferences | Sales systems | Sales app calls |
| Data analysis | Generate SQL | Execute queries + chart | Remember table structures | BI systems | BI tool integration |

## Three Typical Scenarios

The following scenarios describe the target usage of OryxOS in its complete form (including extension-stage capabilities); the core stage provides their runtime kernel.

### Scenario 1: Ops Assistant

A mid-size SaaS company's ops team builds an ops assistant on OryxOS, connected to WeCom. The agent has several tools (alert triage, log query, service restart, change approval). A 3am alert enters OryxOS via webhook; the agent pulls error stacks with the log query tool, cross-references the historical incident library, finds a known bug, applies the mitigation skill to restart the service, and reports "self-healed, details attached" in the WeCom ops group. The on-call engineer reviews the record in the morning.

**OryxOS's role here**: channel access (WeCom), provider routing (primary/backup LLM), tool calling (SSH, Prometheus, Slack notify), memory (incident library), skill (self-healing runbook).

### Scenario 2: Knowledge Management Assistant

A financial enterprise's legal team builds a knowledge agent on OryxOS, connected to Lark. The agent indexes internal contract templates, regulations, historical cases, and consultation records. An employee asks in Lark: "How did we handle the data-residency clause in the last SaaS service agreement?" The agent retrieves historical cases from memory, synthesizes relevant regulations, and produces a draft recommendation with cited sources.

**Key point**: memory retrieval accuracy and citation traceability (compliance requires every agent response to be traceable to its sources).

### Scenario 3: Sales Assistant

A manufacturing enterprise's sales department builds a customer-insight agent on OryxOS, connected to WeCom and CRM. Before visiting a customer, a salesperson asks: "I'm visiting Company A tomorrow — what should I know?" The agent calls the CRM connector for transaction history, the business-registry MCP tool for latest registration info, and the knowledge-base tool for key decision-makers and procurement habits — and composes a customer briefing.

**Core capabilities here**: MCP integration (external data), enterprise IT connector (in-house CRM), tool orchestration.
