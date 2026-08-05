package com.oryxos.storage.audit;

import com.oryxos.core.audit.LlmCallAudit;
import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

/**
 * {@link LlmCallAudit} 的 JPA 实现：把 {@code record} 参数映射成 {@link LlmCallEntity} 存库。
 *
 * <p>成功 / 失败两条路径都落库（宪法原则 VI + 课件「第四步」）。{@code usage} 为 {@code null} 时 token 列写 {@code
 * null}——调用失败或不返回用量时不应拼出假数据。
 */
@Component
public class JpaLlmCallAudit implements LlmCallAudit {

  private final LlmCallRepository repository;

  public JpaLlmCallAudit(LlmCallRepository repository) {
    this.repository = repository;
  }

  @Override
  public void record(
      String sessionId,
      String provider,
      String model,
      Usage usage,
      boolean success,
      String errorMessage,
      long elapsedMillis) {
    LlmCallEntity entity = new LlmCallEntity();
    entity.setSessionId(sessionId);
    entity.setProvider(provider);
    entity.setModel(model);
    if (usage != null) {
      entity.setPromptTokens(usage.getPromptTokens());
      entity.setCompletionTokens(usage.getCompletionTokens());
      entity.setTotalTokens(usage.getTotalTokens());
    }
    entity.setElapsedMillis(elapsedMillis);
    entity.setSuccess(success);
    entity.setErrorMessage(errorMessage);
    repository.save(entity);
  }
}
