/**
 * oryxos-tool：核心能力四（Tool 体系，三合一模块）。
 *
 * <p>包含：内置 Tool（{@code FileTools} / {@code ShellTools} / {@code HttpTools} / {@code NotifyTools}）、
 * {@code McpClientService}、{@code McpToolAdapter}、{@code ToolRegistry}、
 * {@code Sandbox} 接口 + {@code WhitelistSandbox}（应用层白名单）、
 * {@code NotifyChannelAdapter} 接口 + {@code WebhookNotifyAdapter}（出站通知）。
 *
 * <p>关键约束（技术方案第 6 章）：内置 Tool / @Tool Plugin Tool / MCP Tool 统一包装为
 * {@code OryxTool} 注册进 {@code ToolRegistry}；不使用 Java SecurityManager（JDK 21 已不可用）；
 * Agent 目录（AGENT.md）不是 Tool，归 core 的 {@code ContextLoader}。
 */
package com.oryxos.tool;
