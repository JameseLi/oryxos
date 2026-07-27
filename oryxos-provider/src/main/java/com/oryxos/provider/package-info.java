/**
 * oryxos-provider：核心能力一（对接 LLM）。
 *
 * <p>基于 Spring AI Alibaba 的 {@code ChatClient} 做薄包装，包含：{@code ProviderService}、
 * Function Calling 适配、Provider 配置模块。
 *
 * <p>关键约束（技术方案第 3 章）：维护 <b>provider name 到 ChatModel 的显式映射</b>，
 * 禁止靠"扫描容器里所有 ChatModel"区分 Provider；只用 Spring AI 的协议转换和 schema 生成，
 * 禁用其自动 tool 执行（constitution 原则四）。
 */
package com.oryxos.provider;
