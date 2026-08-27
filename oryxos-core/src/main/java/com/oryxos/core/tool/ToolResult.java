package com.oryxos.core.tool;

/**
 * 工具执行结果（第 17 节最小形态，spec R1）。
 *
 * <p>失败是正常业务语义--回填给模型让它自行调整，不是打断对话的异常（课件「配角二」）。 {@code success=false} 时 {@code output} 为 {@code
 * null}、原因在 {@code errorMessage}； {@code ToolInvocationAudit} 落库与此口径一致（成功记 result、失败记
 * error_message，不拼假数据）。
 *
 * <p>{@code ToolResult} 终局形态（§6.1）还含"是否可重试"，由第 20 节 Tool 体系在其上演进。
 */
public record ToolResult(boolean success, String output, String errorMessage) {

  public static ToolResult ok(String output) {
    return new ToolResult(true, output, null);
  }

  public static ToolResult failure(String errorMessage) {
    return new ToolResult(false, null, errorMessage);
  }

  /** 回填给模型的文本：成功为 output，失败为 {@code "ERROR: " + 原因}（模型据此自行纠正）。 */
  public String toModelText() {
    return success ? output : "ERROR: " + errorMessage;
  }
}
