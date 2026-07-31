package com.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.audit.AuditStore;
import com.oryxos.core.audit.LlmCallRecord;
import com.oryxos.core.audit.ToolInvocationRecord;
import com.oryxos.core.session.SessionStore;
import com.oryxos.core.session.StoredMessage;
import com.oryxos.core.session.StoredSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** 存储冒烟测试（T013）：真实 SQLite + 001-init.sql 建表 + SessionStore/AuditStore 往返。 */
@SpringBootTest(classes = StorageSmokeTest.TestApp.class)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:sqlite:target/test-dbs/smoke.db",
      "spring.datasource.driver-class-name=org.sqlite.JDBC",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
      "spring.sql.init.mode=always",
      "spring.sql.init.schema-locations=classpath:scripts/sql/001-init.sql"
    })
class StorageSmokeTest {

  @SpringBootApplication(scanBasePackages = "com.oryxos.storage")
  static class TestApp {}

  @BeforeAll
  static void cleanDb() throws Exception {
    Files.createDirectories(Path.of("target/test-dbs"));
    Files.deleteIfExists(Path.of("target/test-dbs/smoke.db"));
  }

  @Autowired private SessionStore sessionStore;

  @Autowired private AuditStore auditStore;

  @Test
  @DisplayName("会话建查与消息追加有序")
  void sessionCreateFindAndOrderedMessageAppend() {
    StoredSession session = sessionStore.createSession("weather", "cli", "local");
    assertThat(session.id()).isNotBlank();
    assertThat(sessionStore.findSession(session.id())).isPresent();

    sessionStore.appendMessage(session.id(), "user", "你好", null, null);
    sessionStore.appendMessage(session.id(), "assistant", "你好呀", null, null);

    List<StoredMessage> messages = sessionStore.loadMessages(session.id());
    assertThat(messages).hasSize(2);
    assertThat(messages.get(0).seq()).isEqualTo(0);
    assertThat(messages.get(1).seq()).isEqualTo(1);
    assertThat(messages.get(1).role()).isEqualTo("assistant");
  }

  @Test
  @DisplayName("审计两张表写入成功")
  void auditTablesWritable() {
    auditStore.saveLlmCall(
        new LlmCallRecord(
            "sess-x", "deepseek", "deepseek-chat", 0, "{}", "{}", 2, 2, 10, 5, 123L, true, null));
    auditStore.saveToolInvocation(
        new ToolInvocationRecord(
            "sess-x",
            "http_get",
            "{\"url\":\"https://wttr.in\"}",
            "HTTP 200 ...",
            88L,
            true,
            null));
    auditStore.saveLlmCall(
        new LlmCallRecord(
            "sess-x",
            "deepseek",
            "deepseek-chat",
            1,
            "{}",
            null,
            2,
            null,
            null,
            null,
            45L,
            false,
            "timeout"));
    // 无异常即通过（真实 SQLite 写入 + 约束生效）
  }
}
