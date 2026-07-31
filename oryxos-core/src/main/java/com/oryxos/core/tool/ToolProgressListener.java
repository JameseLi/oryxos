package com.oryxos.core.tool;

/** 工具执行进度回调（FR-011）。接口中立：CLI 打印一行式进度只是其中一种实现， 未来 Web/SSE 可复用同一事件源。 */
public interface ToolProgressListener {

  /** 工具开始执行。 */
  void onToolStart(String toolName, String argumentsJson);

  /** 工具执行结束（成功或失败都会回调）。 */
  void onToolEnd(String toolName, boolean success, long durationMs, String errorMessage);

  /** 空实现（无监听场景）。 */
  static ToolProgressListener noop() {
    return new ToolProgressListener() {
      @Override
      public void onToolStart(String toolName, String argumentsJson) {}

      @Override
      public void onToolEnd(
          String toolName, boolean success, long durationMs, String errorMessage) {}
    };
  }
}
