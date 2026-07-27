---
title: Three-layer Memory
description: The MemoryService unified facade, three pluggable LongTermMemoryStore backends, and the MEMORY.md file design
---

# Three-layer Memory

## 5. Capability 3: Three-layer Memory

Memory is the core capability distinguishing an Agent OS from an ordinary chatbot. The three-layer design is the complete picture; the core stage implements the session and long-term layers, with episodic memory in the extension stage.

> **Architecture adjustment note:** Memory is built as a unified facade over the three layers, exposing a single `MemoryService` interface to the ReAct loop; internally it splits into session memory and long-term memory. This keeps the external "three-layer memory" narrative consistent with the internal implementation — the ReAct loop doesn't have to ask Session and `MEMORY.md` separately.

### 5.1 Module Composition

**The `MemoryService` module (unified facade).** Exposes a unified memory read/write interface to the ReAct loop. Internally it delegates session memory to `SessionManager` (backed by SQLite session storage) and long-term memory to `LongTermMemory` (backed by the `MEMORY.md` file). When assembling a prompt, the ReAct loop calls only `MemoryService` to get the complete context. This is the key adjustment over the original design — avoiding the Memory concept spanning two modules without a unified entry.

**The `LongTermMemoryStore` backend interface (pluggable).** Long-term memory is abstracted behind a backend interface, decoupling "the read/write contract of long-term memory" from "where and how it's stored." Three methods:

- `append(content, scope)` — append content to the given section; `scope` is `MemoryScope.CORE` or `ARCHIVAL`, default `ARCHIVAL`
- `load` — returns the full core section plus the truncated archival section; the core section is always complete, never truncated
- `recallByKeyword` — keyword retrieval, matching only in the archival section; the core section doesn't participate because it is already fully injected

All implementations obey four behavioral contracts: ① no caching (re-read the file / query the DB / call the API every time); ② the core section is never truncated — truncation applies only to the archival section; ③ whether a write goes to core or archival is explicitly specified by the agent via `scope` — the system never guesses; ④ `recall` is keyword retrieval, not complicated further. The core stage does **no automatic extraction** — partitioning is decided manually by the agent through the timing of its `save_memory` calls and the `scope` parameter. This is the signal-driven upgrade principle applied to the Memory module; automatic distillation from conversation history goes to the extension stage.

**Three backend implementations (all delivered in the core stage; select one via the `memory.backend` config):**

- **`MarkdownMemoryStore` (default).** Operates on the single Markdown file `.oryxos/memory/MEMORY.md`, partitioned by two headers — `## Core Memory` / `## Archival Memory` (see 5.2). Truncation is string-cutting the archival segment; retrieval is `String.contains` line matching. Zero dependencies, human-readable, git-trackable — the first choice when memory volume is small.
- **`SqliteMemoryStore`.** Memories are stored as rows in a `memory_entries` table (hand-maintained DDL script, same discipline as sessions/audit tables); truncation becomes `LIMIT N` on archival queries, retrieval becomes SQL `LIKE`, and the core section is fetched in full with `WHERE scope='CORE'`. Still **zero external dependencies** (reuses the existing SQLite) — the upgrade tier when memories grow into the thousands or need structured queries.
- **`Mem0MemoryStore`.** Connects to a **self-hosted** Mem0 memory layer (data never leaves the domain); the Java side integrates over REST, translating `append/load/recall` into Mem0's add/get/search — distillation, conflict resolution, and semantic retrieval are delegated to Mem0. Credentials and addresses use environment-variable placeholders. This is the external-integration tier for "genuinely needing intelligent memory."

Switching backends changes one line of config (`memory.backend`); everything above `MemoryService` (`PromptBuilder` / `MemoryTools` / `ReActLoop`) stays untouched — that is the payoff of the interface wall. `recallByKeyword` also reserves semantic upgrade headroom (the Mem0 tier already is semantic retrieval); switching the底层 doesn't affect the upper layers.

**The `MemoryTools` submodule.** Exposes long-term memory to agents as two built-in tools — `save_memory` and `recall_memory` — annotated with `@Tool` and auto-registered into the `ToolRegistry`, treated the same as other built-in tools.

**Session memory.** Implemented by `SessionManager` (see Chapter 8), persisted via SQLite, identified jointly by channel + user + profile. `MemoryService` exposes it as one of the three layers.

### 5.2 MEMORY.md File Design (default backend `MarkdownMemoryStore`)

The default backend's file lives at `.oryxos/memory/MEMORY.md`, organized into two top-level sections, each memory entry carrying a date header.

The format is intentionally not stricter — the LLM understands whatever the agent writes; simple but effective. The two sections are just an organizational distinction. Switching to `SqliteMemoryStore` maps the same "core/archival" semantics onto the `scope` column of `memory_entries`; switching to `Mem0MemoryStore` maps them onto Mem0 metadata — **the partitioning convention stays; the storage shape follows the backend**.

### 5.3 Memory Injection into the System Prompt

At every prompt assembly, `MemoryService` hands the session history and long-term memory (core + archival sections, obtained via `LongTermMemoryStore.load()`) to `PromptBuilder`. Long-term memory is re-read every time with no caching (contract ①), so after an agent calls `save_memory`, the next round sees it immediately — reading a small file (Markdown tier), querying the DB (SQLite tier), or calling the API (Mem0 tier) are all performant enough. An in-memory cache with invalidation can be added behind the facade in the extension stage.

### 5.4 MEMORY.md vs USER.md

| File | Source | Read/write | Purpose |
| --- | --- | --- | --- |
| `USER.md` | Hand-written by the user | OryxOS reads, never writes | The user's "initial settings" (Bootstrap file) |
| `MEMORY.md` | Written by the agent via `save_memory` | OryxOS reads and writes | The agent's "growth journal" (long-term memory) |

Both enter the system prompt, but their sources and lifecycles differ.

### 5.5 Not in the Core Stage

- Automatic extraction (the LLM decides when to call `save_memory`; the Markdown/SQLite tiers don't auto-extract from conversations. The Mem0 tier's automatic extraction is its own built-in capability — whether you get it depends on whether you switch to that backend)
- Built-in vector store (the md/sqlite tiers introduce no vector dependencies in the core stage; when semantic retrieval is needed, switch to `Mem0MemoryStore` and let the external service handle it — no vector layer is built inside the OryxOS process)
- Episodic memory (extension stage)
- Memory Wiki (structured claim/evidence, contradiction detection)
- Memory compression (simple truncation when too long — string cutting for md, `LIMIT` for sqlite)
- Knowledge-graph backend (a higher bar than vectors; if truly needed, integrate a self-hostable solution like Graphiti rather than building one)
