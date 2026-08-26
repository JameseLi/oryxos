package com.oryxos.storage.audit;

import com.oryxos.core.audit.ToolInvocationAudit;
import com.oryxos.storage.entity.ToolInvocationEntity;
import com.oryxos.storage.repository.ToolInvocationRepository;
import org.springframework.stereotype.Component;

/**
 * {@link ToolInvocationAudit} 的 JPA 实现：把 {@code record} 参数映射成 {@link ToolInvocationEntity} 存库。
 *
 * <p>成功 / 失败两条路径都落库（宪法原则 VI + 课件「配角二」）。失败时 {@code result} 可为 {@code null}、原因进 {@code
 * error_message}--与 {@link JpaLlmCallAudit} 同口径：不拼假数据。
 */
@Component
public class JpaToolInvocationAudit implements ToolInvocationAudit {

  private final ToolInvocationRepository repository;

  public JpaToolInvocationAudit(ToolInvocationRepository repository) {
    this.repository = repository;
  }

  @Override
  public void record(
      String sessionId,
      String toolName,
      String arguments,
      String result,
      boolean success,
      String errorMessage,
      long elapsedMillis) {
    ToolInvocationEntity entity = new ToolInvocationEntity();
    entity.setSessionId(sessionId);
    entity.setToolName(toolName);
    entity.setArguments(arguments);
    entity.setResult(result);
    entity.setElapsedMillis(elapsedMillis);
    entity.setSuccess(success);
    entity.setErrorMessage(errorMessage);
    repository.save(entity);
  }
}
