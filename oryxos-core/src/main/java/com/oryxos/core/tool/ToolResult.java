package com.oryxos.core.tool;

/**
 * 工具执行结果（第 17 节最小形态，spec R1；第 20 节演进为 §6.1 四维度终局形态）。
 *
 * <p>失败是正常业务语义--回填给模型让它自行调整，不是打断对话的异常（课件「配角二」）。 {@code success=false} 时 {@code output} 为 {@code
 * null}、原因在 {@code errorMessage}； {@code ToolInvocationAudit} 落库与此口径一致（成功记 result、失败记
 * error_message，不拼假数据）。
 *
 * <p>四维度（§6.1/课件「三」）：成功标识、结果内容、错误信息、是否可重试。为什么需要第四维：ReAct 循环拿到失败结果时，得知道这错值不值得再调一次 （如 MCP server
 * 瞬时失联值得重试，白名单拦截不值得）；17 节 javadoc 预告「由第 20 节 Tool 体系在其上演进」，本演进即兑现。
 */
public record ToolResult(boolean success, String output, String errorMessage, boolean retryable) {

  public static ToolResult ok(String output) {
    return new ToolResult(true, output, null, false);
  }

  /** 成功结果的重载：可重试位只对失败语义有意义，成功路径固定 {@code false}（形态完整性占位）。 */
  public static ToolResult ok(String output, boolean retryable) {
    return new ToolResult(true, output, null, retryable);
  }

  public static ToolResult failure(String errorMessage) {
    return new ToolResult(false, null, errorMessage, false);
  }

  /**
   * 失败结果的重载：调用方明确知道失败是否值得重试（课件「三」MCP 骨架 {@code ToolResult.failure("MCP 调用失败", true)}
   * --外部系统瞬时失败值得再调一次）。
   */
  public static ToolResult failure(String errorMessage, boolean retryable) {
    return new ToolResult(false, null, errorMessage, retryable);
  }

  /** 回填给模型的文本：成功为 output，失败为 {@code "ERROR: " + 原因}（模型据此自行纠正）。 */
  public String toModelText() {
    return success ? output : "ERROR: " + errorMessage;
  }
}
