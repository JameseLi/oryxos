package com.oryxos.storage.repository;

import com.oryxos.storage.entity.ToolInvocationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code tool_invocations} 审计仓储（第 17 节，宪法原则 VI）。
 *
 * <p>核心阶段「只写不查」（{@code record} 落库 + 按会话查），查询接口与审计报表放扩展阶段--与 {@link LlmCallRepository} 同口径。
 */
public interface ToolInvocationRepository extends JpaRepository<ToolInvocationEntity, Long> {

  /** 按会话列出工具调用审计记录（测试断言与后续审计消费用）。 */
  List<ToolInvocationEntity> findBySessionId(String sessionId);
}
