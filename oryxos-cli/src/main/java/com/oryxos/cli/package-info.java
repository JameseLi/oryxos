/**
 * oryxos-cli：命令行入口（Picocli）。
 *
 * <p>包含：{@code OryxOsCli} 主入口（整个 OryxOS 的 main 函数所在）、12 个子命令 （init / status / chat / serve /
 * gateway / profile list|create|show|delete / provider list / tool list / session list）、{@code
 * ConfigLoader}（配置与密钥加载）。
 *
 * <p>关键约束（技术方案 8.7 / 8.8）：不需要 Spring 上下文的命令（init、profile list） 直接走文件操作保证启动速度，需要 LLM 调用的命令才启动 Spring
 * 上下文； 敏感配置一律 {@code ${ENV_VAR}} 占位 + 环境变量注入，禁止明文落盘。
 */
package com.oryxos.cli;
