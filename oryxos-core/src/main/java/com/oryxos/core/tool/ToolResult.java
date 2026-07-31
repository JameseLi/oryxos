package com.oryxos.core.tool;

/** 工具执行结果。失败是正常业务语义（回填给 LLM 兜底），不是异常。 */
public record ToolResult(boolean success, String output, String errorMessage) {

  public static ToolResult ok(String output) {
    return new ToolResult(true, output, null);
  }

  public static ToolResult failure(String errorMessage) {
    return new ToolResult(false, null, errorMessage);
  }

  /** 回填给 LLM 的文本：成功为 output，失败为 "ERROR: ..."。 */
  public String toModelText() {
    return success ? output : "ERROR: " + errorMessage;
  }
}
