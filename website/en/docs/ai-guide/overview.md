---
title: Overview
description: The hybrid model of Spec-Kit + manual prompting, user-story-based decomposition, and fit assessment
---

# Overview

## 1. Implementation Overview

### 1.1 The Main Approach: Spec-Kit + Manual Prompting Hybrid

OryxOS's AI-assisted implementation has two phases, each using a different collaboration tool:

| Phase | Tool | Best for |
| --- | --- | --- |
| **Main development** | Spec-Kit | Building OryxOS 1.0's five core capabilities from scratch — 9 Maven modules |
| **Incremental development** | Manual prompting + Claude Code | Extension features, bug fixes, new Plugin Tools — small-granularity increments |

The boundary is clear: **Spec-Kit suits large-granularity greenfield work; manual prompting suits small-granularity increments**. OryxOS's main development is the former; community continuation is the latter. Tool choice matches the nature of the work.

---

### 1.2 Relationship with the Requirements Doc + Technical Solution

The implementation guide doesn't rewrite the requirements and technical solution — it feeds the existing documents into Spec-Kit. The mapping:

| Spec-Kit input | Source document | Role |
| --- | --- | --- |
| `/speckit.specify` input | Requirements doc | Spec-Kit turns the requirements doc into specs for 5 user stories |
| `/speckit.plan` input | Technical solution | Spec-Kit turns the technical solution into a modular implementation plan |
| `constitution.md` | Requirements doc Chapter 3 (design goals) + Technical solution §1.1 (key decisions) | Non-negotiable principles |
| Acceptance criteria | Requirements doc Chapter 13 (5 acceptance demos) | Reused directly |

Prior investment in documentation is not wasted — Spec-Kit merely converts it into a format AI agents can consume directly.

> **Key caution:** the technical solution is the input to `/speckit.plan`, so the module structure in the plan must match the 9 modules in Technical Solution Chapter 10. Make sure the latest version of the technical solution is fed in, or the generated plan will split by the wrong module count.

---

### 1.3 Decomposition Strategy: By User Story, Not by Time

The whole of OryxOS main development is organized into **5 user stories**, one per core capability.

The 5 user stories are not parallel — they have explicit dependencies, and dependencies determine the order:

| User Story | Core capability | Depends on | Parallelizable |
| --- | --- | --- | --- |
| **US-1** | LLM integration (Provider abstraction) | None | — |
| **US-2** | ReAct loop (agent brain) | US-1 | — |
| **US-3** | Three-layer Memory | US-2 | Parallel with US-4 |
| **US-4** | Plugin Tool system | US-2 | Parallel with US-3 |
| **US-5** | Web Service | All of US-1 ~ US-4 | — |

**Order**: US-1 → US-2 → (US-3 ∥ US-4) → US-5

- US-1 is the foundation — without LLM calls, no agent capability works
- US-2 depends on US-1 — the ReAct loop calls the LLM every round
- US-3 + US-4 run in parallel, both depending on US-2 (Memory injects into ReAct's prompt; Tools are called by ReAct)
- US-5 depends on all four — it exposes everything externally

> The time invested per story is up to the project team. This doc decomposes by dependency order and doesn't prescribe durations. The core-stage cadence defined in the requirements doc and technical solution is 4 weeks × 3 hours; the mapping between the 5 user stories and that cadence is in Technical Solution Chapter 12 and not repeated here.

This user-story decomposition fits Spec-Kit's mechanics naturally: Spec-Kit's `/speckit.tasks` command organizes tasks by user story, each story becoming an independent implementation phase, with tasks ordered by dependency and parallelizable ones marked.

---

## 2. Fit Assessment: Spec-Kit × OryxOS

Before writing an implementation plan, answer a fundamental question: is Spec-Kit really suitable for OryxOS?

### 2.1 What Spec-Kit Suits

Spec-Kit is GitHub's open-source spec-driven development toolchain — one of the fastest-growing developer tools of 2026, with high community adoption and support for twenty-plus AI coding agents. It structures AI-assisted coding into a repeatable specify → plan → tasks → implement flow. Its core idea: make the spec the contract and single source of truth for code behavior, turning the AI agent from a "code generator" into a "collaborator working to spec" — the cure for vibe coding.

**Scenarios the community agrees it suits:**
- Medium to large greenfield projects (from-scratch builds, medium-to-large effort, modules spanning multiple folders)
- Clear requirements (upstream requirements docs or product decisions exist)
- AI agent collaboration (Claude Code, Copilot, Cursor, etc. doing main development)
- Methodology scenarios (enforced spec-driven flow; teams learn engineering methodology)

**Scenarios it does not suit:**
- Small features, quick prototypes, single-file changes (process overhead exceeds benefit)
- Large brownfield renovations (legacy code context too complex, exceeding LLM context limits)
- Exploratory research projects (running spec with unsettled requirements means repeated rework)

### 2.2 OryxOS's Fit

Assessing OryxOS against Spec-Kit's suitable scenarios:

| Dimension | OryxOS situation | Fit |
| --- | --- | --- |
| greenfield | A brand-new from-scratch project, not modifying existing code | ✅ Full match |
| Scale | 9 Maven modules, five clear core capabilities — typical medium scale | ✅ Full match |
| Clear requirements | Complete requirements doc + technical solution; all five capabilities described at user-story level | ✅ Full match |
| AI agent collaboration | OryxOS's main development is done with Claude Code | ✅ Full match |
| Methodology scenario | Spec-Kit's enforced flow aligns output with requirements; valuable for developers mastering methodology | ✅ Match |

**Conclusion**: Spec-Kit is the best tool choice for OryxOS's main development. The community debates Spec-Kit on brownfield projects, but OryxOS is pure greenfield — those debates don't apply.

### 2.3 Spec-Kit's Limitations and Countermeasures

| Limitation | Description | OryxOS's countermeasure |
| --- | --- | --- |
| Flow too heavy for small increments | The full Spec-Kit flow costs more than a small change | OryxOS main development is large-granularity; the incremental phase switches to manual prompting |
| Specs don't auto-sync with implementation | AI agents may drift from spec during implement | Run `/speckit.analyze` after each user story for consistency checks |
| Context limits fail on large brownfields | LLMs can't see hundred-thousand-file legacy projects | OryxOS is pure greenfield — the whole project fits in the LLM context window; N/A |
| Spec-Kit itself is iterating fast | Command names, artifact formats, and integration methods keep changing | This doc doesn't pin version details; follow the official docs at implementation time |
