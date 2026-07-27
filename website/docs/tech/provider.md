---
title: Provider 抽象
description: ProviderService、Function Calling 适配，以及 provider name 到 ChatModel 的显式映射
---

# Provider 抽象

## 3. 核心能力一：对接 LLM（Provider 抽象）

LLM 调用的复杂度都被 **Spring AI Alibaba** 吸收掉了。OryxOS 在其上做一层薄包装，把 Spring AI 的 `ChatClient` 转成 OryxOS 内部的 **`ProviderService`** 抽象。

### 3.1 模块组成

**`ProviderService` 模块。** 职责是统一管理所有 LLM Provider，对 ReAct 循环屏蔽不同 LLM 厂商的差异。ReAct 循环调 LLM 时传入 Profile 和 Prompt，`ProviderService` 按 Profile 配置选对应的底层 `ChatModel` 完成调用。

**Function Calling 适配模块。** 职责是把 OryxOS 内部的 **`OryxTool`** 抽象转成 Spring AI 的工具调用格式。Spring AI 已经做好了向各家 LLM 协议的转换（OpenAI tools、Anthropic tools、Gemini function declarations），OryxOS 不需要关心每家协议的差异。注意这里只用 Spring AI 的格式转换，不用它的自动执行（见决策二）。

**Provider 配置模块。** 通过 `application.yaml` 配置 Provider 的 API key 和 base URL，Spring AI Alibaba 根据配置创建对应的 `ChatModel` Bean。


### 3.2 Provider 名到 ChatModel 的显式映射

这是一个需要讲清楚的关键点。Spring AI Alibaba 配多个 Provider 时，Spring 容器里会有多个 `ChatModel` Bean。仅靠"扫描容器里所有 `ChatModel`"无法可靠区分哪个是 deepseek、哪个是 kimi，因为 Bean 类型相同、Bean name 未必等于 provider name。

OryxOS 的做法是维护一份显式的 **provider name 到 `ChatModel` 的映射**，而不是靠类型扫描自动来。

具体是在 Provider 配置里为每个 Provider 声明唯一的 provider name（`deepseek`、`qwen`、`kimi` 等），`ProviderService` 启动时按这个 name 建立映射表，Profile 通过 provider name 引用。这样多 Provider 并存时不会有歧义。映射的具体实现方式（用 Spring 的 `@Qualifier`、还是自己维护配置表）在研发阶段定，但"**显式映射、不靠类型扫描**"这个原则要守住，否则多 Provider 跑不起来。

### 3.3 关键设计点

**核心阶段不做 fallback 和 hedge racing。** Provider 故障时直接报错给 Agent。fallback 链路、circuit breaker、hedge racing 放扩展阶段，通过 Profile 的 `fallback` 字段声明备用 Provider。

**成本透明在核心阶段做基础版。** 每次 LLM 调用记录 token 使用量、Provider、模型，写入 `llm_calls` 表（见第 8 章）。扩展阶段做完整的成本聚合和 Web 看板。

---
