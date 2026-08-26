package com.oryxos.core.audit;

/**
 * 工具调用审计门面（第 17 节，DIP：接口立在 {@code oryxos-core}，实现在 {@code oryxos-storage}）。
 *
 * <p>与 {@link LlmCallAudit} 同构、同一口径：{@code ToolExecutor} 每执行一次工具都调一次 {@link #record}，成功 {@code
 * success=true}、失败 {@code success=false}+{@code errorMessage}（课件「配角二」：成功要记、失败也要记--只记成功不记失败 =
 * 一次真实事故在系统里没痕迹，属易漏缺陷）。
 */
public interface ToolInvocationAudit {

  /**
   * 记一次工具调用。
   *
   * @param sessionId 所属会话（审计按会话关联，{@code null} 表示无会话绑定）
   * @param toolName 工具名（来自模型的工具调用请求）
   * @param arguments 模型给出的 JSON 入参原文
   * @param result 工具结果文本（失败时可为 {@code null}，原因在 {@code errorMessage}）
   * @param success 本次调用是否成功
   * @param errorMessage 失败原因；成功时为 {@code null}
   * @param elapsedMillis 耗时（毫秒）
   */
  void record(
      String sessionId,
      String toolName,
      String arguments,
      String result,
      boolean success,
      String errorMessage,
      long elapsedMillis);
}
