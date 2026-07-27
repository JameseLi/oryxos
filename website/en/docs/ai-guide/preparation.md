---
title: Preparation
description: Spec-Kit installation, the seven non-negotiable constitution principles, specify and plan
---

# Preparation

## 3. The Preparation Phase

Preparation is the scaffolding work before formal implementation, done by the project team. It produces three Spec-Kit artifacts (constitution, spec, plan) so every user story that follows has a clear basis.

### 3.1 Spec-Kit Installation + Claude Code Configuration

The Specify CLI is Spec-Kit's entry tool (implemented in Python, requires Python 3.11+, recommended to install via `uv`). After installation, run `specify init` to initialize OryxOS's Spec-Kit workspace, which contains `.specify/memory/constitution.md` and the directory structure for spec, plan, tasks, and other artifacts.

Claude Code is the recommended AI agent, officially supported by Spec-Kit. The exact integration method (early on it was slash commands; Claude Code now uses a skills model, specified via init parameters) follows the official docs.

---

### 3.2 `/speckit.constitution`: Writing the OryxOS Project Charter

`constitution.md` holds the project's **non-negotiable principles** — every subsequent spec, plan, tasks, and implement step must obey them. OryxOS's constitution is distilled from the requirements doc Chapter 3 (design goals) + technical solution §1.1 (key decisions):

| # | Principle | Description |
| --- | --- | --- |
| 1 | JDK 21 + Spring Boot 3.x monolith | Maven multi-module (9 modules), single-binary deployment |
| 2 | Five core capabilities first | The core stage delivers the runtime kernel; the enterprise governance layer goes to the extension stage |
| 3 | Self-implemented ReAct loop | Do not use Spring AI's agent abstraction directly |
| 4 | **Use only half of Spring AI** | Only the Provider abstraction, protocol conversion, and `@Tool` schema generation; **automatic tool execution disabled**; tool scheduling is fully controlled by `ReActLoop` + `ToolExecutor`. **The easiest principle to get wrong** |
| 5 | Three tiers of Plugin Tools | Primarily recommend the zero-code SKILL.md + MCP tier |
| 6 | SQLite + MEMORY.md file storage | Vector retrieval goes to the extension stage; `tool_invocations` and `llm_calls` are **written to the database from the core stage** |
| 7 | A demonstrable demo after each user story | Working beats perfect |

> `constitution.md` is written once and fixed for the entire main development. If a principle turns out wrong mid-way, stop and re-discuss — **AI agents are not allowed to modify the constitution themselves**.

---

### 3.3 `/speckit.specify`: Turning the Requirements Doc into 5 User Stories

`/speckit.specify` takes the requirements doc as input and outputs specs for 5 user stories, one per core capability.

The 5 user stories are ordered by dependency, not importance. One clarification: US-5 (Web Service) is implemented last because it depends on the first four capabilities being ready — **not because it is unimportant**. On the contrary, Web Service is the key capability distinguishing OryxOS from personal-assistant projects; its importance is high. This doc deliberately avoids P1/P2/P3 priority labels, which could be misread as "later means optional" — only dependency order is stated.

Each user story's acceptance criteria reuse the requirements doc Chapter 13's 5 acceptance demos directly:

| User Story | Corresponding demo | Scenario |
| --- | --- | --- |
| US-1 + US-2 | Demo 1 | Weather check with outfit advice |
| US-3 | Demo 2 | Remembering preferences across conversations |
| US-4 | Demo 3 | Zero-code PR digest |
| US-5 | Demo 4 + Demo 5 | Web Service synchronous calls + multi-endpoint coordination |

After `/speckit.specify` runs, it produces `spec.md`, from which the AI agent understands what OryxOS as a whole must do. Running `/speckit.clarify` afterward is recommended — the AI agent asks a few clarifying questions (e.g., max-iterations default, history truncation strategy). Optional but recommended.

---

### 3.4 `/speckit.plan`: Turning the Technical Solution into an Implementation Plan

`/speckit.plan` takes the technical solution + the previous step's `spec.md` + `constitution.md` as input, and outputs an implementation plan containing:

- Tech stack selection (JDK 21 + Spring Boot 3.x + Spring AI Alibaba + SQLite + Picocli)
- The 9 Maven modules' responsibilities (against Technical Solution Chapter 10)
- The key technical decisions elaborated (self-built ReAct, the "only half of Spring AI" boundary, three Plugin Tool tiers, SQLite + MEMORY.md, audit tables from day one)
- Data flow and inter-module collaboration (`PromptBuilder` + `ProviderService` + `ToolExecutor` + the `MemoryService` three-layer facade)

**Human review after plan generation is mandatory.** The AI agent may have made trade-offs it shouldn't have based on its own reading of the technical solution. Check specifically:

- [ ] Did it simplify Memory into a merge with Session? (It should be the `MemoryService` three-layer unified facade)
- [ ] Did it split Tools back into multiple modules? (It should be the single merged `oryxos-tool` module)
- [ ] Did it treat `AgentLoader`/`AGENT.md` as a Tool? (Agent directories belong to core's `ContextLoader`; the body injects into prompts)
- [ ] Did it enable Spring AI's automatic tool execution? (Must be disabled)

Once review passes, `plan.md` is locked.

---

### 3.5 Preparation-Phase Deliverables

At the end of preparation, the OryxOS repo should contain:

```
.specify/
└── memory/
    └── constitution.md       # non-negotiable principles
spec.md                       # 5 user stories
plan.md                       # tech stack + 9 modules + technical decisions
docs/
├── DemandAnalysis.md         # requirements doc (source reference)
├── TechnicalSolution.md      # technical solution (source reference)
├── IndustryResearch.md       # industry research (source reference)
└── AiProgrammingGuide.md     # this document
```

With preparation complete, the basis for implementing all 5 user stories is ready, and work can proceed in dependency order.
