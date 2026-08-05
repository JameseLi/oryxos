package com.oryxos.storage.repository;

import com.oryxos.storage.entity.LlmCallEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code llm_calls} 审计仓储（宪法原则 VI）。
 *
 * <p>核心阶段「只写不查」（{@code record} 落库 + 按会话查），查询接口与审计报表放扩展阶段。
 */
public interface LlmCallRepository extends JpaRepository<LlmCallEntity, Long> {

  /** 按会话列出审计记录（集成冒烟 {@code ProviderSmokeIT} 用它断言「多了一条 success=true」）。 */
  List<LlmCallEntity> findBySessionId(String sessionId);
}
