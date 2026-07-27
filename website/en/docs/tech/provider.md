---
title: Provider Abstraction
description: ProviderService, function-calling adaptation, and the explicit provider-name → ChatModel mapping
---

# Provider Abstraction

## 3. Capability 1: LLM Integration (Provider Abstraction)

The complexity of LLM calling is absorbed by **Spring AI Alibaba**. OryxOS adds a thin wrapper on top, turning Spring AI's `ChatClient` into OryxOS's internal **`ProviderService`** abstraction.

### 3.1 Module Composition

**The `ProviderService` module.** Manages all LLM providers uniformly, shielding the ReAct loop from vendor differences. When the ReAct loop calls an LLM, it passes the Profile and prompt; `ProviderService` selects the underlying `ChatModel` per the Profile configuration and completes the call.

**The function-calling adaptation module.** Converts OryxOS's internal **`OryxTool`** abstraction into Spring AI's tool-calling format. Spring AI already handles conversion to each vendor's protocol (OpenAI tools, Anthropic tools, Gemini function declarations), so OryxOS never cares about per-vendor protocol differences. Note: only Spring AI's *format conversion* is used — not its automatic execution (see Decision 2).

**The provider configuration module.** Provider API keys and base URLs are configured via `application.yaml`; Spring AI Alibaba creates the corresponding `ChatModel` beans from this configuration.

### 3.2 Explicit Provider-Name → ChatModel Mapping

This is a key point worth spelling out. When multiple providers are configured with Spring AI Alibaba, the Spring container holds multiple `ChatModel` beans. Simply "scanning all `ChatModel` beans in the container" cannot reliably tell which is DeepSeek and which is Kimi — the bean types are identical, and bean names don't necessarily equal provider names.

OryxOS's approach: maintain an explicit **provider name → `ChatModel` mapping** instead of relying on type scanning.

Concretely, the provider configuration declares a unique provider name for each provider (`deepseek`, `qwen`, `kimi`, …); at startup `ProviderService` builds the mapping table from these names, and Profiles reference providers by name. This keeps multiple coexisting providers unambiguous. The mapping's concrete implementation (Spring `@Qualifier` vs a self-maintained config table) is decided during development — but the principle "**explicit mapping, never type scanning**" must hold, or multiple providers won't work.

### 3.3 Key Design Points

**No fallback or hedge racing in the core stage.** On provider failure, the error is reported to the agent directly. Fallback chains, circuit breakers, and hedge racing come in the extension stage, declared via the Profile's `fallback` field as backup providers.

**Cost transparency: basic version in the core stage.** Every LLM call records token usage, provider, and model into the `llm_calls` table (see Chapter 8). Full cost aggregation and a web dashboard come in the extension stage.
