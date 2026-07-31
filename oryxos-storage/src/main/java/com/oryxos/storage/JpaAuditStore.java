package com.oryxos.storage;

import com.oryxos.core.audit.AuditStore;
import com.oryxos.core.audit.LlmCallRecord;
import com.oryxos.core.audit.ToolInvocationRecord;
import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.entity.ToolInvocationEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import com.oryxos.storage.repository.ToolInvocationRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** core {@link AuditStore} 的 JPA 实现。审计写入独立事务（REQUIRES_NEW）： 即使主流程事务回滚，已产生的审计行也不丢（FR-018）。 */
@Component
public class JpaAuditStore implements AuditStore {

  private final LlmCallRepository llmCallRepository;
  private final ToolInvocationRepository toolInvocationRepository;

  public JpaAuditStore(
      LlmCallRepository llmCallRepository, ToolInvocationRepository toolInvocationRepository) {
    this.llmCallRepository = llmCallRepository;
    this.toolInvocationRepository = toolInvocationRepository;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void saveLlmCall(LlmCallRecord record) {
    LlmCallEntity entity = new LlmCallEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setSessionId(record.sessionId());
    entity.setProviderName(record.providerName());
    entity.setModel(record.model());
    entity.setIteration(record.iteration());
    entity.setRequestJson(record.requestJson());
    entity.setResponseJson(record.responseJson());
    entity.setRequestBytes(record.requestBytes());
    entity.setResponseBytes(record.responseBytes());
    entity.setPromptTokens(record.promptTokens());
    entity.setCompletionTokens(record.completionTokens());
    entity.setDurationMs(record.durationMs());
    entity.setSuccess(record.success() ? 1 : 0);
    entity.setErrorMessage(record.errorMessage());
    entity.setCreatedAt(Instant.now().toString());
    llmCallRepository.save(entity);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void saveToolInvocation(ToolInvocationRecord record) {
    ToolInvocationEntity entity = new ToolInvocationEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setSessionId(record.sessionId());
    entity.setToolName(record.toolName());
    entity.setArgumentsJson(record.argumentsJson());
    entity.setResultPreview(record.resultPreview());
    entity.setDurationMs(record.durationMs());
    entity.setSuccess(record.success() ? 1 : 0);
    entity.setErrorMessage(record.errorMessage());
    entity.setCreatedAt(Instant.now().toString());
    toolInvocationRepository.save(entity);
  }
}
