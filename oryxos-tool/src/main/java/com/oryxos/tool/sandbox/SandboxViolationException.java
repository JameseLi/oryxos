package com.oryxos.tool.sandbox;

/**
 * 安全校验失败（技术方案 §6.7）：校验不过直接抛出、工具执行终止。
 *
 * <p>异常信息直接复用 17 节 {@code ToolExecutor} 已有的失败审计路径写入 {@code tool_invocations} （{@code
 * success=false}、{@code error_message}），不需要为 Sandbox 单独新增审计逻辑； 第 20 节课件 harness
 * 对「命中白名单外域名应被拦下」用例明确点名本类型 （课件注：「23、24 节会讲清楚这里具体抛的是 SandboxViolationException」）。
 */
public class SandboxViolationException extends RuntimeException {

  public SandboxViolationException(String message) {
    super(message);
  }

  public SandboxViolationException(String message, Throwable cause) {
    super(message, cause);
  }
}
