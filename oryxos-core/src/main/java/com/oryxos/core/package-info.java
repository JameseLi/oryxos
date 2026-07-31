/**
 * oryxos-core：核心抽象和接口，不依赖其他业务模块。
 *
 * <p>包含：{@code OryxTool} 接口、{@code Session}、{@code Profile}、{@code ContextLoader}、 {@code
 * AgentLoader}（扫 {@code .oryxos/agents/}、deriveProfile）、{@code ReActLoop}、 {@code
 * PromptBuilder}、{@code ToolExecutor}、{@code AgentService}、{@code AgentScheduler}（定时触发）。
 *
 * <p>职责定义见 docs/TechnicalSolution.md 第 10 章；实现按 docs/AiProgrammingGuide.md 的 user story 顺序推进（US-1 →
 * US-2 → US-3∥US-4 → US-5）。
 */
package com.oryxos.core;
