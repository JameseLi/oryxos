---
title: Web Service
description: The complete outward face — 10 core REST endpoints and business-system integration scenarios
---

# Web Service

## 7. Capability 5: Web Service

Web Service is OryxOS's complete outward face; business systems integrate via REST API. The first four capabilities are OryxOS's internal powers; Web Service is their external exposure. Without it, OryxOS is just a CLI tool that cannot integrate with existing enterprise systems. This is the key capability distinguishing OryxOS from personal-positioned projects like OpenClaw and Hermes.

### 7.1 Module Composition

**The `WebServer` module.** Starts the Spring MVC server, triggered by the `oryxos serve` command; default port `8080`, with Java 21 virtual threads enabled.

**The `ApiController` collection.** Six controllers organized by resource: `SessionApiController` (session management), `AgentApiController` (stateless invocation), `ProfileApiController` (profile queries), `MemoryApiController` (memory queries), `ToolApiController` (tool information), `SystemApiController` (system status). Each controller only validates parameters, wraps responses, and handles errors — real logic delegates to core-layer services.

**The `GlobalExceptionHandler` module.** Unified exception handling, converting exceptions into the standard JSON envelope `ApiResponse` (`code`, `message`, `data`, `timestamp` — success and error share one envelope).

**The OpenAPI documentation module.** Auto-generates OpenAPI 3.0 docs via `springdoc-openapi`, exposed at `/swagger-ui`.

### 7.2 Core-Stage Endpoints (10)

**Session management (4):**

1. `POST /api/v1/sessions` (create)
2. `POST /api/v1/sessions/{id}/messages` (post message)
3. `GET /api/v1/sessions/{id}` (get history)
4. `DELETE /api/v1/sessions/{id}` (archive)

**Agent invocation (1):**

1. `POST /api/v1/agents/{name}/invoke` (stateless)

**Profile / Memory / Tool information (3):**

1. `GET /api/v1/profiles`
2. `GET /api/v1/memory`
3. `GET /api/v1/tools`

**System status (2):**

1. `GET /api/v1/health`
2. `GET /api/v1/info`

### 7.3 Endpoints Added in the Extension Stage

**Agent directory upload/view/update/delete** (business teams create new agents via API instead of manually dropping directories into `.oryxos/agents/`, including one-sentence generation of `AGENT.md` — the complete closed loop of "defining a new agent purely via API"); Memory append/clear/search; tool describe and invocation history; LLM call history and token statistics; **`AgentScheduler` schedule management** (add/remove/query/modify an agent's frontmatter `schedules` without editing files and restarting); webhook triggers; SSE streaming responses; Prometheus metrics; OpenAPI spec.

> **Why these are not in the core stage:** in the core stage, "defining an agent" means a business team hand-writes a `.oryxos/agents/<name>/` directory (`AGENT.md` [+ scripts / sub-instructions]) that takes effect on restart or hot reload (`ContextLoader` re-reads at every prompt assembly, so restarts aren't strictly needed — see 8.3). Core-stage Web Service endpoints are all queries, not creations — a different thing from "business systems dynamically creating new agents via API." The latter requires the agent-directory upload endpoint (with one-sentence generation) and `AgentScheduler`'s runtime add/remove endpoints delivered together — without that whole chain it's incomplete — so they ship as one batch in the extension stage, not split halfway.

### 7.4 Key Design Points

- **Error-code conventions:** standard HTTP status codes plus internal error codes (400 parameter errors, 404 resource not found, 500 internal errors, 503 provider failure).
- **CORS:** all origins allowed in the core stage for debugging convenience; whitelists in the extension stage.
- **Request size limits:** max 32KB per message; session history returns at most the latest 100 entries.
- **Timeout:** agent calls time out at 60 seconds, returning 504.

### 7.5 Not in the Core Stage

- Authentication (assumes trusted intranet; API Key + JWT in extension)
- Streaming responses (SSE)
- WebSocket
- RBAC permissions
- Rate limiting

These come in the extension stage.

### 7.6 Business-System Integration Scenarios

- **Synchronous calls** (most common — the business system calls invoke and waits; good for stateless short tasks)
- **Session continuity** (create a session first, then post multiple messages; good for continuous conversation)
- **Webhook triggers** (alerting systems, CI/CD, scheduled jobs calling agents — closing the monitor→analyze→act loop)
- **Cross-language integration** (any language that can make HTTP requests; no SDKs in the core stage — they come in the extension stage)
