---
title: Landscape & Enterprise Needs
description: The gaps left by OpenClaw and Hermes Agent, and why heavily regulated enterprises need a private, controllable agent foundation
---

# Landscape & Enterprise Needs

## The Two Most Representative Open-Source Agent OS Projects

Two projects stand out in the open-source Agent OS space: **OpenClaw** and **Hermes Agent**. Without diving into their architectures, here is what each represents and the landscape they together form.

**OpenClaw** was released in November 2025 by PSPDFKit founder Peter Steinberger — **Node.js**, MIT license. It represents a consumer-grade, developer-first orientation: twenty-plus channels, tens of thousands of community skills, extreme playability; it passed 300k GitHub stars by April 2026, the fastest-growing open-source project in GitHub history. Its strength is community vitality and capability breadth; its weakness is enterprise-grade security and governance — **CVEs**, malicious skills, credential harvesting recur structurally, and enterprises need hardening layers like **Tank OS** to reach production. In one sentence: OpenClaw is the Agent OS for individuals and small teams.

**Hermes Agent** was released in February 2026 by open-source AI lab NousResearch — **Python**, MIT license. It represents an engineering-grade, robustness-first orientation: three-layer memory, self-evolving skill mechanism, security scanning, **HERMES_HOME** multi-user isolation; clear investment in the enterprise direction, commercialization samples, and cloud-vendor endorsement — about 150k stars by May 2026, at one point surpassing OpenClaw in daily inference volume. It is closer to the enterprise than OpenClaw, but enterprise-grade OS governance (multi-tenant RBAC, SSO, full audit) is still absent. In one sentence: Hermes leans toward teams and enterprises.

Together they sketch today's open-source Agent OS landscape: one consumer-grade and playful, one engineering-grade and robust — both started from individuals and small teams, both shaped as "a foundation installed on your own machine providing a complete agent runtime." The significance lies not in what each did, but in **what they left unfilled together** — the starting point for understanding OryxOS's positioning.

## The Gaps They Left Together

**First, complete enterprise-grade governance.** Both projects target individuals to small teams; true enterprise multi-tenant RBAC, SSO integration, audit architecture, and compliance evidence remain blank. Hardening solutions like PTG and Tank OS exist, but they are patches, not day-one design.

**Second, deep integration with enterprise IT systems.** Both support enterprise IM channels (Lark, WeCom, DingTalk) via community extensions, but deep integration with existing IT systems (ERP, CRM, CMDB, monitoring) remains blank — every company writes its own adapters.

**Third, the absence of the Java ecosystem.** OpenClaw is Node.js; Hermes is Python. No project in the Java ecosystem positions itself as an "Agent OS." This matters especially for the enterprise market — see [The Java Ecosystem Gap](/en/docs/research/java-gap).

## Why Enterprises Need a Private, Controllable Agent Foundation

The core argument: what enterprises truly need is not "the Agent OS category," but "**a private, controllable, auditable, unified agent foundation**." The two sound alike, but the anchor is completely different — the former is a concept that may evolve; the latter is a demand that will not change.

### The Macro Demand Is Real

The industry consensus on enterprise AI agents is formed. The **Anthropic 2026 State of AI Agents** report (surveying 500+ technology leaders) shows over half have deployed multi-step agent workflows, the vast majority plan to expand more complex use cases in 2026, and most report measurable economic returns from AI agent investment. No suspense on the demand side.

### The Hard Part Is Not "Building an Agent" but "Running It Under Control"

The same data contains a contrast: a large number of agent pilots never reach production; a significant share of enterprise pilots fail to scale. Behind the contradiction is one thing: a single agent demo is easy, but making it enterprise-grade, production-running, company-serving immediately hits a pile of engineering problems.

The hardest data in the Anthropic report points exactly at these engineering problems: enterprises cite integration, data access quality, implementation cost, and change management as the biggest challenges. On security governance, most organizations reported an AI agent security incident in the past year, while only a small fraction had full IT/security approval before agents entered production. **Tool calling** has a non-trivial failure rate in production, mostly silent failures.

None of these problems can be solved by "a stronger model." They are all "the foundation is not stable enough, not controllable enough."

### Heavily Regulated Enterprises: A Demand That Is Certain and Unchanging

Focus on the hardest customers: **banking, government, telecom, energy, healthcare**. These heavily regulated enterprises have iron rules:

1. Core business data must not leave the enterprise
2. Systems must be fully auditable
3. Any new component must pass existing security and compliance processes
4. The technology stack must align with the existing one

Under these rules, their choices narrow dramatically. They will not run core-business agents on SaaS (data leaves), will not run on a product bound to one public cloud (ecosystem lock-in), and can hardly put a Node.js open-source project with a history of CVEs and credential harvesting into production (fails security review). What they need is an agent foundation that is **privately deployed, fully auditable, governable within existing IT governance, and aligned with the existing tech stack**.

This demand is certain, rigid, and currently unmet. Crucially: whether or not the term "Agent OS" stays popular, whether this middle layer ends up independent or absorbed by another layer — "heavily regulated enterprises need an agent foundation they fully control" will not change. It is anchored in the enterprise's essential constraints, not in a technology concept.

### Value Anchors

The value of this private, controllable foundation is concrete: direct cost reduction, direct efficiency gains, cross-system collaboration, implementable compliance and audit, and letting ordinary employees use AI capabilities.

The deepest anchor is **knowledge沉淀 (knowledge retention)**. Through the skill system, senior employees' experience is turned into an asset that stays in the enterprise and that new employees can inherit. Accumulated knowledge, experience, and AI capabilities stay with the enterprise forever — the fundamental value distinguishing a private foundation from SaaS.
