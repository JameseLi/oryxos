---
title: Incremental Phase & Risks
description: The manual-prompting mode after main development, common challenges, and countermeasures
---

# Incremental Phase & Risks

## 6. The Incremental Phase: Manual Prompting

### 6.1 Why Switch from Spec-Kit to Manual Prompting

After main development, OryxOS enters the incremental phase. The nature of the work is completely different from main development:

| Dimension | Main development | Incremental development |
| --- | --- | --- |
| Task granularity | Large (9 modules built together) | Small (add a channel, fix a bug) |
| Files touched | Many (cross-module) | Few (usually 1–3) |
| Cross-module collaboration | Yes | Usually no |
| Context | From-scratch design | Existing code |

For this kind of work, the Spec-Kit flow is too heavy: running the full constitution + specify + plan + tasks + implement cycle costs more than the task itself. Manual prompting with Claude Code fits better: open Claude Code, describe the change, let it edit directly in the existing code context, run the tests, and submit a PR — no formal spec and plan artifacts needed.

### 6.2 The Incremental Workflow

A typical incremental-phase workflow:

```
1. A community contributor claims an issue (labeled good-first-issue / feature-request / long-term-goal in the main repo)
2. Fork + clone OryxOS locally
3. Open the project with Claude Code and describe the intended change
4. Claude modifies on top of the existing code, adds tests, gets them passing
5. Submit a PR to the main repo
6. The project team reviews + merges
```

This flow doesn't mandate Spec-Kit — each contributor works their own way. For large features demanding rigor, teams may choose to run Spec-Kit, but it isn't enforced.

### 6.3 Connecting with Main-Phase Spec-Kit Artifacts

The `constitution.md` and `spec.md` produced in the main phase remain in the repo as reference documents during the incremental phase:

- **`constitution.md` remains non-negotiable**: community-contributed code must obey it (JDK 21 + Spring Boot, self-built ReAct, only half of Spring AI, three Plugin Tool tiers, etc.)
- **`spec.md` is the contract of core capabilities**: contributors changing a core capability must not break its acceptance criteria
- **`plan.md` is basically frozen after the main phase**: the technical solution document stays as community reference

**Handling new user stories:**
- Small features: manual prompting + PR directly
- Large features (new Maven modules, constitution changes, spanning multiple core capabilities): the project team decides whether to run a dedicated Spec-Kit specify → plan → tasks flow

---

## 7. Risks & Caveats

### 7.1 Spec-Kit's Current Limitations

Spec-Kit is still iterating fast; the tool itself changes frequently. Points to watch when using it:

| Caveat | Description |
| --- | --- |
| **Version pinning** | Pin a specific Specify CLI version before implementation; don't upgrade during main development — command names, artifact formats, and integration methods may change between versions |
| **Consult official docs anytime** | This doc covers approach and cadence; follow the official docs at implementation time for concrete commands and installation |
| **Community extensions with caution** | Spec-Kit has 70+ community extensions; during main development use only official core commands — don't introduce extensions and their uncertainty |

### 7.2 Common Challenges During Implementation

| Challenge | Countermeasure |
| --- | --- |
| **AI agents drifting from the constitution** | Human review after each implement run; on deviation, have the AI agent re-read the constitution and fix immediately |
| **Context breaks across user stories** | At the start of each user story, have the AI agent re-read `spec.md` + `plan.md` + recent code |
| **`/speckit.analyze` getting skipped** | Make analyze a hard gate at the end of every user story — no skipping |
| **MCP server integration pitfalls** | Before US-4's MCP work, test connectivity with a minimal MCP server (stdio transport can hit process-launch failures, encoding issues) |
| **Java engineering fundamentals as prerequisite** | Ensure team members have basic Spring Boot + Maven + JPA proficiency before implementation |

---

## 8. Summary

OryxOS's AI-assisted implementation has two phases:

### Main Development (Spec-Kit)

The existing requirements doc + technical solution are fed into Spec-Kit, producing artifacts like constitution + spec + plan + tasks. Preparation produces constitution, spec, and plan in one go; then implementation proceeds in dependency order across the 5 user stories:

```
US-1 → US-2 → ┌─ US-3 ─┐ → US-5
               └─ US-4 ─┘
```

Each user story completes with a demonstrable demo, corresponding to the 5 acceptance demos in the requirements doc Chapter 13.

### Incremental Phase (Manual Prompting + Claude Code)

Small-granularity increments don't suit the full Spec-Kit flow. Community contributors use Claude Code to modify existing code directly, while the main phase's constitution + spec remain as long-term references.

---

**Spec-Kit fits OryxOS extremely well**: pure greenfield, medium scale (9 modules), clear requirements, AI agent collaboration, methodology scenario — every criterion matches.

**The core strategy is feeding existing documents into Spec-Kit — never rewriting them.** OryxOS has already invested in complete industry research + requirements doc + technical solution; these are Spec-Kit's best inputs. **The key is feeding the latest version**: the module count is 9, not 11; the constitution must include newer decisions like "use only half of Spring AI" and "audit tables from day one" — otherwise Spec-Kit generates plans that drift along the old structure.
