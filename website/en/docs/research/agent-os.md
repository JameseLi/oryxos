---
title: What is an Agent OS
description: The definition and deployment shape of an Agent OS, and its boundaries vs orchestration platforms, frameworks, and cloud SaaS
---

# What is an Agent OS

## A Clear Definition

An **Agent OS** is a foundation system for running and managing AI agents. It is installed on the user's (or enterprise's) own machines; upward, it provides a unified runtime environment for all kinds of agents (ops assistants, support assistants, HR assistants, sales assistants); downward, it connects models, channels, tools, memory, identity, and audit infrastructure.

A qualified Agent OS must do five things:

1. **Agent configuration and lifecycle management.** Register, start, monitor, and destroy multiple agents, each with its own prompt, model, tools, channels, and memory. Agents are **configured**, not coded.
2. **Unified outbound channel access.** IM (WeCom, Lark, DingTalk, Slack, Telegram, Discord…), email, Web, HTTP API — all agents share one channel layer.
3. **Unified inbound system access.** LLM providers, tools (MCP or plugins), enterprise IT systems, knowledge bases — all agents share one access layer.
4. **Unified memory.** Long-term memory across sessions, reusable skill templates, knowledge沉淀 across agents.
5. **Tool calling and sandboxed execution.** Agents call tools via LLM function calling; tools execute in a sandbox that enforces the security boundary.

One easily confused term must be clarified: an **Agent OS** is not the same as an **agent runtime**.

An **agent runtime** is the execution kernel that runs a single agent — LLM calls, tool execution, context management, loop control; item five above roughly belongs to this layer. An Agent OS contains a runtime at its core, but above the runtime it also handles the first four items: lifecycle of multiple agents, unified inbound/outbound access, unified memory — plus multi-tenancy and audit discussed later.

By OS analogy: a runtime is like the execution environment of a single process; an Agent OS is like the layer that manages a fleet of processes, schedules resources, and provides shared services and governance. In one sentence: **a runtime runs one agent; an Agent OS keeps a fleet of agents managed inside an enterprise**.

> An Agent OS answers: "when a user runs N agents at once, what should the shared infrastructure layer look like?"

## What an Agent OS Looks Like

Deployment shape:

1. The user deploys one Agent OS instance (a single service) on their own machine, server, or K8s.
2. The Agent OS ships with a web console or CLI.
3. The user creates agents, registers tools and skills, and configures channels via the web console or CLI.
4. Once running, end users talk to agents through IM or Web.
5. The Agent OS can run agents automatically (on schedules).

What a business team cares about is simple:

1. Write a tool that implements one concrete function (in any language).
2. Configure an agent on the Agent OS that puts the tool to work.

Business teams do not write agent backend code — **agents are configured, not coded**. They never worry about where messages come from, how LLMs are called, how identity is managed, how audit lands, or how context continues — those are the Agent OS's job.

## Boundaries with Adjacent Concepts

| Dimension | **Agent OS** | **Orchestration platform** | **Framework** | **Cloud suite / SaaS** |
| --- | --- | --- | --- | --- |
| Product | Configured, resident agents | Executable workflow | Code (libraries / SDKs) | Complete SaaS application |
| Users | Business teams (configure) + developers (write tools) | Business / developers (drag-and-drop) | Developers (write code) | Business / end users |
| Runs on | User's own machines / servers / K8s | The platform's own runtime | The developer's own environment | Vendor cloud / SaaS |
| Deployment | Private, open-source, self-hostable | Mostly cloud-hosted, some private | No runtime provided | SaaS, bound to vendor cloud |
| Ecosystem lock-in | None | Weak (some bind their own ecosystem) | None | Strong |

- **Orchestration platforms** are visual workflow builders: users drag nodes into a workflow and the platform executes it. Their product is a *flow* — good for explicit business processes. An Agent OS's product is *configured, resident agents*. Different layers: an orchestration platform can run on top of an Agent OS, using it as the backend.
- **Frameworks** are libraries or SDKs for writing agents in code; their product is code, and the runtime environment is your own problem. An Agent OS is an install-and-run service with infrastructure included. Frameworks can serve as components inside an Agent OS — e.g., an Agent OS may use **Spring AI** for LLM calls.
- **Cloud suites / SaaS** are complete applications bound to their cloud or collaboration ecosystems. An Agent OS is an open-source, privately deployable foundation with no lock-in.

With these three boundaries drawn, the Agent OS's position in the ecosystem is clear.
