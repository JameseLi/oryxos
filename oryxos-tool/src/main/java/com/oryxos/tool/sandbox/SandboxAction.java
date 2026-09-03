package com.oryxos.tool.sandbox;

/**
 * 一次受控动作的描述（技术方案 §6.7）：{@code SandboxAction = { type: ActionType, target: String }}。
 *
 * <p>{@code target} 语义由 {@code type} 决定：文件类为路径、{@code SHELL_COMMAND} 为命令行、 {@code HTTP_REQUEST} 为完整
 * URL。类型体系只表达「在受控环境里执行这个动作」的意图， 不携带任何实现细节（白名单/容器/VM 等词汇不出现在签名与字段里，§6.7 中立性自查）。
 */
public record SandboxAction(ActionType type, String target) {

  public SandboxAction {
    if (type == null) {
      throw new IllegalArgumentException("SandboxAction.type 不能为空");
    }
    if (target == null || target.isBlank()) {
      throw new IllegalArgumentException("SandboxAction.target 不能为空");
    }
  }
}
