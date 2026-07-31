package com.oryxos.web.api;

/**
 * 统一错误码（AGENTS.md §9 响应信封的 code 字段取值）。
 *
 * <p>核心阶段只定义与 HTTP 语义对齐的基础码；业务自定义错误码从 10000 起，随 US-5 业务落地再扩。
 */
public enum ErrorCode {
  SUCCESS(0, "success"),
  BAD_REQUEST(400, "请求参数错误"),
  NOT_FOUND(404, "资源不存在"),
  INTERNAL_ERROR(500, "服务器内部错误"),
  SERVICE_UNAVAILABLE(503, "服务暂不可用");

  private final int code;
  private final String message;

  ErrorCode(int code, String message) {
    this.code = code;
    this.message = message;
  }

  public int getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}
