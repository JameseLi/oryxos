package com.oryxos.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oryxos.storage.audit.JpaToolInvocationAudit;
import com.oryxos.storage.entity.ToolInvocationEntity;
import com.oryxos.storage.repository.ToolInvocationRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link ToolInvocationRepository} 验收 harness（第 17 节，spec R6：口径与 {@code LlmCallRepositoryTest} 一致）。
 *
 * <p>讲究：建表走手工脚本 {@code scripts/sql/001-init.sql}（测试经 {@code spring.sql.init} 执行）， 不让 Hibernate
 * 自动建--否则测试绿、生产跑真脚本时列名对不上，白测。
 *
 * <p>覆盖验收点：手工建表脚本建出的 {@code tool_invocations} 能存能读、{@code success}/{@code error_message} 两列真实存在；
 * {@link JpaToolInvocationAudit} 成功/失败两条 {@code record(...)} 路径都真落库。
 */
@SpringBootTest
class ToolInvocationRepositoryTest {

  @Autowired private ToolInvocationRepository repository;

  @Autowired private JpaToolInvocationAudit audit;

  @Test
  @DisplayName("手工脚本建出的 tool_invocations 存一条成功记录后可读回，success=true 且 error_message 为 null")
  void saveSuccessRemainsReadable_withScriptBuiltTable() {
    ToolInvocationEntity row = newSuccessRow("s-1", "http_get", 120L);
    ToolInvocationEntity saved = repository.save(row);

    ToolInvocationEntity readBack = repository.findById(saved.getId()).orElseThrow();
    assertEquals("s-1", readBack.getSessionId());
    assertEquals("http_get", readBack.getToolName());
    assertEquals("{\"url\":\"https://example.com\"}", readBack.getArguments());
    assertEquals("200 OK", readBack.getResult());
    assertEquals(120L, readBack.getElapsedMillis());
    assertTrue(readBack.getSuccess());
    assertNull(readBack.getErrorMessage());
    assertNotNull(readBack.getCreatedAt(), "createdAt 由实体层写入 ISO 字符串，不应为 null");
  }

  @Test
  @DisplayName("失败记录也落 tool_invocations：success=false 且 error_message 列真实存在并记原因")
  void saveFailureKeepsErrorMessageColumn() {
    ToolInvocationEntity row = newFailureRow("s-2", "shell", "command not allowed", 500L);
    ToolInvocationEntity saved = repository.save(row);

    ToolInvocationEntity readBack = repository.findById(saved.getId()).orElseThrow();
    assertFalse(readBack.getSuccess());
    assertEquals("command not allowed", readBack.getErrorMessage());
    assertNull(readBack.getResult());
  }

  @Test
  @DisplayName("按会话查工具调用审计记录：findBySessionId 返回该会话所有记录")
  void findBySessionIdReturnsAllRowsForSession() {
    repository.deleteAll();
    repository.save(newSuccessRow("s-3", "http_get", 100L));
    repository.save(newFailureRow("s-3", "shell", "boom", 80L));
    repository.save(newSuccessRow("s-4", "read_file", 60L));

    List<ToolInvocationEntity> s3 = repository.findBySessionId("s-3");
    assertEquals(2, s3.size());
    assertTrue(s3.stream().allMatch(r -> "s-3".equals(r.getSessionId())));
  }

  @Test
  @DisplayName("JpaToolInvocationAudit 成功路径：record 落一条 success=true 且字段逐项对齐")
  void jpaAuditRecordsSuccessPath() {
    repository.deleteAll();
    audit.record("s-5", "http_get", "{\"url\":\"https://a.com\"}", "200 OK", true, null, 42L);

    List<ToolInvocationEntity> rows = repository.findBySessionId("s-5");
    assertEquals(1, rows.size());
    ToolInvocationEntity row = rows.get(0);
    assertEquals("http_get", row.getToolName());
    assertEquals("200 OK", row.getResult());
    assertTrue(row.getSuccess());
    assertNull(row.getErrorMessage());
    assertEquals(42L, row.getElapsedMillis());
  }

  @Test
  @DisplayName("JpaToolInvocationAudit 失败路径：record 落一条 success=false 且 error_message 带原因")
  void jpaAuditRecordsFailurePath() {
    repository.deleteAll();
    audit.record("s-6", "shell", "rm -rf /", null, false, "command not allowed", 7L);

    List<ToolInvocationEntity> rows = repository.findBySessionId("s-6");
    assertEquals(1, rows.size());
    ToolInvocationEntity row = rows.get(0);
    assertFalse(row.getSuccess());
    assertEquals("command not allowed", row.getErrorMessage());
    assertNull(row.getResult());
    assertEquals("shell", row.getToolName());
  }

  private static ToolInvocationEntity newSuccessRow(
      String sessionId, String toolName, long elapsedMillis) {
    ToolInvocationEntity e = new ToolInvocationEntity();
    e.setSessionId(sessionId);
    e.setToolName(toolName);
    e.setArguments("{\"url\":\"https://example.com\"}");
    e.setResult("200 OK");
    e.setElapsedMillis(elapsedMillis);
    e.setSuccess(true);
    return e;
  }

  private static ToolInvocationEntity newFailureRow(
      String sessionId, String toolName, String errorMessage, long elapsedMillis) {
    ToolInvocationEntity e = new ToolInvocationEntity();
    e.setSessionId(sessionId);
    e.setToolName(toolName);
    e.setArguments("rm -rf /");
    e.setElapsedMillis(elapsedMillis);
    e.setSuccess(false);
    e.setErrorMessage(errorMessage);
    return e;
  }
}
