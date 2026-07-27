---
title: Extensions & Community
description: Beyond the core stage — the enterprise governance layer and community-driven directions
---

# Extensions & Community

## Extension Features

Extension features proceed after the core features are complete, filling in what production use requires beyond the shortest path — driven by the open-source community over time.

### Channels & Model Layer

- **Multi-channel access**: WeCom, Lark, DingTalk, Slack, email — extended through the Channel Adapter plugin mechanism
- **Provider fallback & reliability**: three-layer failover (hedge racing, circuit breaker, automatic switching) — on provider failure, switch to backup automatically
- **Adaptive routing**: LLM routing upgraded from static config to dynamic decisions based on task type, historical call quality, and current provider load

### Memory & Capability Layer

- **Automatic memory extraction**: at conversation end, the LLM automatically extracts facts worth keeping into MEMORY.md
- **Semantic memory retrieval**: integrate vector databases (Milvus, Qdrant, Weaviate, PostgreSQL pgvector) for similarity-based matching
- **Episodic memory**: the third memory layer — files modified, decisions made, outcomes produced during tasks
- **Memory Wiki**: structured claim/evidence, contradiction detection, freshness management
- **Skill system**: full SKILL.md support, compatible with the agentskills.io open standard

### Tooling & Security Layer

- **MCP server exposure**: OryxOS itself acts as an MCP server, exposing internal agent capabilities to other systems
- **Tool Policy**: profile-level allow/deny rules for tools — the lightest and most OS-flavored governance capability, prioritized in the extension stage
- **Tool LRU loading**: with many tools, load dynamically to avoid stuffing all tools into the LLM context
- **Full sandbox isolation**: Docker-container and K8s-pod sandbox implementations, with WebAssembly as a high-performance option

### Governance & Operations Layer

> This layer is the core differentiation between OryxOS and personal-grade Agent OS projects

- **Web dashboard**: profile management, session viewing, monitoring boards, audit log queries
- **SSO & multi-tenancy**: SAML/OIDC integration with enterprise AD/Okta/Entra ID/Aliyun IDaaS; three-level tenant model (organization, department, project); RBAC down to agent, tool, and skill granularity
- **Audit & traceability**: complete audit event records, structured JSON output, trace-ID chaining, sensitive-data masking, SIEM export
- **Observability**: Prometheus metrics, structured logs, health checks, Grafana dashboard templates
- **Clustered deployment & HA**: multi-node coordination via Nacos or ETCD; automatic load migration on node failure

### Enterprise Integration Layer

- **Enterprise IT connectors**: ready-made connectors for ERP (Yonyou, Kingdee, SAP), CRM (Xiaoshouyi, FXiaoKe, Salesforce), CMDB, monitoring systems, and intranet knowledge bases

## Community-Driven Features

Community features are not on OryxOS's mainline roadmap — they are long-term directions open to community contribution, with no fixed schedule.

- **Remaining project docs**: API reference, deployment & ops handbook, contributor guide CONTRIBUTING.md, scenario handbooks
- **Skills Marketplace**: a community skill-sharing platform compatible with agentskills.io, interoperable with OpenClaw and Hermes Agent
- **Multi-language SDKs**: priority Java → Python → TypeScript → Go
- **Visual Profile editor**: let non-engineers configure agents — approaching Dify's agent configuration UI
- **Native file generation**: generate pptx, docx, xlsx without LibreOffice (Apache POI)
- **Multi-region deployment**: cross-region OryxOS clusters with cross-region agent, memory, and session coordination
- **Kubernetes Operator**: one-click deployment, declarative configuration, GitOps workflows
- **Mobile console**: check cluster status and handle alerts from a phone
- **Voice Channel**: wake word and continuous voice conversations
- **RISC-V and edge deployment**: run on Raspberry Pi and edge gateways (GraalVM Native Image)
