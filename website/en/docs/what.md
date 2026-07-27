---
title: What is OryxOS
description: A Java-native enterprise Agent OS — a privately deployable, fully auditable foundation for running agents
---

# What is OryxOS

**OryxOS** is an enterprise-grade **Agent OS** implemented in Java. It runs on your own K8s or servers as a shared foundation for business agents (ops assistants, support assistants, HR assistants, sales assistants, knowledge assistants, and more) — sharing one set of capabilities: channel access, model routing, tool calling, memory, and sandboxed execution.

> Data stays entirely on your own infrastructure. No cloud ecosystem lock-in.

## Agent OS ≠ Agent Runtime

The first key to understanding OryxOS is separating two easily confused concepts:

- **Agent Runtime**: the execution kernel that runs a **single** agent — LLM calls, tool execution, context management, loop control
- **Agent OS**: includes a runtime at its core, but above it manages the **fleet**: agent lifecycles, unified inbound/outbound access, unified memory, governance and audit

By OS analogy: a runtime is the execution environment of one process; an Agent OS is the layer that manages many processes, schedules resources, and provides shared services and governance. In short: **a runtime runs one agent; an Agent OS keeps a fleet of agents managed inside an enterprise**.

## Five Core Capabilities

| Capability | In one sentence |
| --- | --- |
| 🔌 LLM Integration | A Provider abstraction over mainstream LLMs — agents never know which vendor they call; switch at runtime with no lock-in |
| 🧠 ReAct Loop | The agent brain: LLM reasons → calls a tool → observes → decides next step, completing multi-step tasks autonomously |
| 💾 Three-layer Memory | Session memory + long-term memory (MEMORY.md) — remembers user preferences and project context across conversations |
| 🛠️ Tool System | 9 built-in tools + three tiers of Plugin Tools (zero-code / light-code / full-code) |
| 🌐 Web Service | A complete REST API exposing everything — the single integration surface for business systems |

See [Five Core Capabilities](/en/docs/demand/capabilities).

## Delivery in Two Stages

1. **Core stage**: build the **runtime kernel** solidly in Java, matching the foundation layer of open-source Agent OS projects — this is what the repo is doing now
2. **Extension stage**: the differentiating governance layer (multi-tenancy, SSO, full audit, tool governance) is built on top of the kernel over time

**The core stage is the foundation; enterprise governance is the endgame.**

## Four Design Goals

| Goal | Description |
| --- | --- |
| **Unified** | Multiple business agents share one foundation; a new agent is just one directory |
| **Private** | Data stays entirely on enterprise infrastructure; OryxOS itself collects nothing |
| **Easy to integrate** | A standard Spring Boot project that connects directly to existing ERP / CRM / CMDB / SSO / monitoring |
| **Observable** | Structured JSON logs, health checks, standard metrics — fits existing alerting systems |

## Documentation Map

| Section | Question answered | Start here |
| --- | --- | --- |
| Industry Research | Why — why build OryxOS | [What is an Agent OS](/en/docs/research/agent-os) |
| Requirements | What — what to build | [Five Core Capabilities](/en/docs/demand/capabilities) |
| Technical Solution | How — how it's designed | [Key Decisions](/en/docs/tech/decisions) |
| AI Programming Guide | How it's built — AI collaboration | [Overview](/en/docs/ai-guide/overview) |

Next: [Quick Start](/en/docs/quick-start) — run your first agent in three minutes.
