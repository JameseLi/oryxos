package com.oryxos.web.api;

import java.time.Instant;

/**
 * Web 层统一响应信封（AGENTS.md §9）：{@code code=0} 表示成功，非 0 为错误码。
 *
 * <p>错误码约定：默认与 HTTP 状态码一致（400/404/500/503…），业务自定义错误码从 10000 起。 所有 Controller 返回 {@code
 * ApiResponse<T>}，异常由 {@link GlobalExceptionHandler} 统一转换。
 */
public record ApiResponse<T>(int code, String message, T data, Instant timestamp) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(
        ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data, Instant.now());
  }

  public static ApiResponse<Void> ok() {
    return ok(null);
  }

  public static ApiResponse<Void> error(ErrorCode errorCode) {
    return error(errorCode.getCode(), errorCode.getMessage());
  }

  public static ApiResponse<Void> error(int code, String message) {
    return new ApiResponse<>(code, message, null, Instant.now());
  }
}
