package com.oryxos.tool.sandbox;

/**
 * 默认放行的临时实现（第 20 节临时装配，24 节由 {@code WhitelistSandbox} 替换）。
 *
 * <p>为什么先放行：白名单校验本体（路径/命令/域名匹配逻辑与 {@code file.allowed_paths}、 {@code shell.allowed_commands}、{@code
 * http.allowed_domains} 配置键）是 23/24 节 Sandbox 模块的交付； 本节先把「执行方法第一行过 {@code
 * enforce}」的调用位与异常路径立起来，届时换实现类、调用方零改动 （接口先行的兑现）。放行 ≠ 没有校验位——工具侧调用语义与 harness「越界会被拦」守点由拒绝型 mock
 * Sandbox 在单测中钉死。
 */
public class PermissiveSandbox implements Sandbox {

  @Override
  public void enforce(SandboxAction action) {
    // 默认放行：核心阶段临时装配；24 节 WhitelistSandbox 接管后此实现退出装配。
  }
}
