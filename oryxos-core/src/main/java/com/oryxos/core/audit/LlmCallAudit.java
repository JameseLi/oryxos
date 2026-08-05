package com.oryxos.core.audit;

import org.springframework.ai.chat.metadata.Usage;

/**
 * LLM 调用审计门面（DIP：接口立在 {@code oryxos-core}，实现在 {@code oryxos-storage}）。
 *
 * <p>Provider 每调用一次大模型都调一次 {@link #record}：成功 {@code success=true}、失败 {@code success=false}+{@code
 * errorMessage}（课件「第三步/第四步」：成功失败都要留痕——只记成功不记失败 = 一次真实事故在系统里没痕迹，属易漏缺陷）。{@code usage} 可为 {@code
 * null}（调用失败或模型不返回用量时）。
 */
public interface LlmCallAudit {

  /**
   * 记一次 LLM 调用。
   *
   * @param sessionId 所属会话（PPP 按会话关联审计，{@code null} 表示无会话绑定）
   * @param provider provider name（来自 Profile）
   * @param model 模型名（来自 Profile；可能为 {@code null}）
   * @param usage token 用量（Spring AI {@link Usage}）；{@code null} 表示无用量信息
   * @param success 本次调用是否成功
   * @param errorMessage 失败原因；成功时为 {@code null}
   * @param elapsedMillis 耗时（毫秒）
   */
  void record(
      String sessionId,
      String provider,
      String model,
      Usage usage,
      boolean success,
      String errorMessage,
      long elapsedMillis);
}
