package com.oryxos.tool.sandbox;

/**
 * 受控执行入口（技术方案 §6.7，接口先行）：表达「在受控环境里执行一个动作」的意图，只有一个方法。
 *
 * <p>中立性自查（§6.7）：签名里不出现「白名单」「容器镜像」「VM 配置」这类某一档实现特有的词—— 用最重的 microVM 实现去反向套这个签名，也应该能干净套入。
 *
 * <p>核心阶段唯一在装配里的实现是 {@link PermissiveSandbox}（默认放行临时装配，24 节 {@code WhitelistSandbox}
 * 替换、调用方零改动）。所有内置工具的执行方法第一行调用本接口 （课件「三」http_get 示例：「方法体里第一件事就是过安全校验」，这是硬规矩）；校验失败抛 {@link
 * SandboxViolationException}，异常沿 ToolExecutor 既有失败审计路径写入 {@code tool_invocations}。
 */
public interface Sandbox {

  /** 校验动作是否被允许；不允许则抛 {@link SandboxViolationException} 终止执行。 */
  void enforce(SandboxAction action);
}
