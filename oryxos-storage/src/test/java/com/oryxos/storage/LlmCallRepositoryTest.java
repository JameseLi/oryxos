package com.oryxos.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link LlmCallRepository} 验收 harness（课件「验收 harness」第四个单测类）。
 *
 * <p>讲究：建表走手工脚本 {@code scripts/sql/001-init.sql}（测试经 {@code spring.sql.init} 执行）， 不让 Hibernate
 * 自动建——否则测试绿、生产跑真脚本时列名对不上，白测。
 *
 * <p>覆盖验收点：手工建表脚本建出的 {@code llm_calls} 能存能读，{@code success}/{@code error_message} 两列真实存在。
 */
@SpringBootTest
class LlmCallRepositoryTest {

  @Autowired private LlmCallRepository repository;

  @Test
  @DisplayName("手工脚本建出的 llm_calls 存一条成功记录后可读回，success=true 且 error_message 为 null")
  void saveSuccessRemainsReadable_withScriptBuiltTable() {
    LlmCallEntity row = newSuccessRow("s-1", "deepseek", "deepseek-chat", 120L);
    LlmCallEntity saved = repository.save(row);
    long savedId = saved.getId();

    LlmCallEntity readBack = repository.findById(savedId).orElseThrow();
    assertEquals("s-1", readBack.getSessionId());
    assertEquals("deepseek", readBack.getProvider());
    assertEquals("deepseek-chat", readBack.getModel());
    assertEquals(12, readBack.getPromptTokens());
    assertEquals(34, readBack.getCompletionTokens());
    assertEquals(46, readBack.getTotalTokens());
    assertEquals(120L, readBack.getElapsedMillis());
    assertTrue(readBack.getSuccess());
    assertNull(readBack.getErrorMessage());
    assertNotNull(readBack.getCreatedAt(), "createdAt 由实体层写入 ISO 字符串，不应为 null");
  }

  @Test
  @DisplayName("失败记录也落 llm_calls：success=false 且 error_message 列真实存在并记原因")
  void saveFailureKeepsErrorMessageColumn() {
    LlmCallEntity row = newFailureRow("s-2", "kimi", "moonshot-v1-8k", "connect timeout", 500L);
    LlmCallEntity saved = repository.save(row);

    LlmCallEntity readBack = repository.findById(saved.getId()).orElseThrow();
    assertFalse(readBack.getSuccess());
    assertEquals("connect timeout", readBack.getErrorMessage());
    assertNull(readBack.getPromptTokens());
    assertNull(readBack.getCompletionTokens());
    assertNull(readBack.getTotalTokens());
  }

  @Test
  @DisplayName("按会话查审计记录（集成冒烟消费用）：findBySessionId 返回该会话所有记录")
  void findBySessionIdReturnsAllRowsForSession() {
    repository.deleteAll();
    repository.save(newSuccessRow("s-3", "deepseek", "deepseek-chat", 100L));
    repository.save(newSuccessRow("s-3", "deepseek", "deepseek-chat", 80L));
    repository.save(newSuccessRow("s-4", "kimi", "moonshot-v1-8k", 60L));

    List<LlmCallEntity> s3 = repository.findBySessionId("s-3");
    assertEquals(2, s3.size());
    assertTrue(s3.stream().allMatch(r -> "s-3".equals(r.getSessionId())));
  }

  private static LlmCallEntity newSuccessRow(
      String sessionId, String provider, String model, long elapsedMillis) {
    LlmCallEntity e = new LlmCallEntity();
    e.setSessionId(sessionId);
    e.setProvider(provider);
    e.setModel(model);
    e.setPromptTokens(12);
    e.setCompletionTokens(34);
    e.setTotalTokens(46);
    e.setElapsedMillis(elapsedMillis);
    e.setSuccess(true);
    return e;
  }

  private static LlmCallEntity newFailureRow(
      String sessionId, String provider, String model, String errorMessage, long elapsedMillis) {
    LlmCallEntity e = new LlmCallEntity();
    e.setSessionId(sessionId);
    e.setProvider(provider);
    e.setModel(model);
    e.setElapsedMillis(elapsedMillis);
    e.setSuccess(false);
    e.setErrorMessage(errorMessage);
    return e;
  }
}
