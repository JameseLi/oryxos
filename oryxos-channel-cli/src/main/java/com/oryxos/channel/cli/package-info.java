/**
 * oryxos-channel-cli：CLI Channel（核心阶段唯一内置 Channel）。
 *
 * <p>包含：{@code CliChannel}——{@code oryxos chat} 命令的实现，读 stdin 写 stdout
 * 实现交互式多轮对话，维护当前 Session，每次输入调 {@code AgentService.process}。
 *
 * <p>扩展阶段的企业微信 / 飞书 / 钉钉 / Slack 等 IM Channel 各自由新模块承载，
 * 底层都调 oryxos-web 的 Agent 接口，不重复实现 Agent 逻辑（技术方案 8.4）。
 */
package com.oryxos.channel.cli;
