package com.oryxos.tool.sandbox;

/** 违反沙箱策略（如目标域名不在白名单）。 */
public class SandboxViolationException extends RuntimeException {

  public SandboxViolationException(String message) {
    super(message);
  }
}
