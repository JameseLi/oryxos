package com.oryxos.web.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：一切异常出参都收敛为 {@link ApiResponse} 标准 JSON（AGENTS.md §9）。
 *
 * <p>覆盖：400（参数/绑定/消息不可读）、404（无资源）、500（未预期兜底）；503 等业务主动状态码经 {@link ResponseStatusException} 路径转换。5xx
 * 记 error 日志（带堆栈），4xx 记 warn（不带堆栈）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** 业务/框架主动抛出的带状态码异常（含 503 Service Unavailable）。 */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
    HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
    int code = ex.getStatusCode().value();
    String message =
        ex.getReason() != null
            ? ex.getReason()
            : (status != null ? status.getReasonPhrase() : "请求失败");
    if (ex.getStatusCode().is5xxServerError()) {
      log.error("request failed: {} {}", code, message, ex);
    } else {
      log.warn("request rejected: {} {}", code, message);
    }
    return ResponseEntity.status(code).body(ApiResponse.error(code, message));
  }

  /** 400：参数校验失败、缺参、请求体不可读、非法参数。 */
  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    MissingServletRequestParameterException.class,
    HttpMessageNotReadableException.class,
    IllegalArgumentException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
    log.warn("bad request: {}", ex.getMessage());
    return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.BAD_REQUEST));
  }

  /** 404：无匹配资源（Spring 6.1+ 静态资源/路径未命中）。 */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
    log.warn("not found: {}", ex.getResourcePath());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ErrorCode.NOT_FOUND));
  }

  /** 500：未预期异常兜底。不向前端泄漏内部细节，细节进日志。 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
    log.error("unexpected error", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
  }
}
