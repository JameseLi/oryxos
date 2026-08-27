package com.oryxos.storage.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oryxos.storage.entity.SessionEntity;
import com.oryxos.storage.repository.SessionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link SessionRepository} 验收 harness（课件「验收 harness」第二个单测类）。
 *
 * <p>覆盖验收点：手工建表脚本建出的 {@code sessions} 表能存能读；{@code messages_json} 序列化回读后消息完整； 模拟「重启」（新建 context
 * 重查）历史还在。
 *
 * <p>讲究：建表走手工脚本 {@code scripts/sql/001-init.sql}（测试经 {@code spring.sql.init} 执行），不让 Hibernate
 * 自动建（否则测试绿、生产跑真脚本时列名对不上，白测）；「重启」用文件型 datasource + {@link DirtiesContext} 令下一个测试方法拿到全新 context 重读同一
 * DB 文件--内存库随 context 销毁，模拟不了重启。
 */
@SpringBootTest
@TestPropertySource(
    properties = "spring.datasource.url=jdbc:sqlite:target/test-dbs/sessions-restart.db")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SessionRepositoryTest {

  private static final String RESTART_SESSION_ID = "cli:wang:default";

  @Autowired private SessionRepository repository;

  private final MessageJsonCodec codec = new MessageJsonCodec();

  @BeforeAll
  static void prepareTestDbDir() throws IOException {
    Files.createDirectories(Path.of("target/test-dbs"));
    // 删掉上次运行残留，保证「重启」断言不被旧数据污染（每次测试类运行都从空库开始）
    Files.deleteIfExists(Path.of("target/test-dbs/sessions-restart.db"));
  }

  @Test
  @Order(1)
  @DirtiesContext
  @DisplayName("重启前：带历史的会话落库（messages_json 序列化写入）")
  void writeSessionHistoryBeforeRestart() {
    SessionEntity entity = new SessionEntity();
    entity.setSessionId(RESTART_SESSION_ID);
    entity.setProfileName("default");
    entity.setChannel("cli");
    entity.setUserId("wang");
    entity.setMessagesJson(codec.serialize(historyMessages()));
    entity.setStatus("active");
    repository.save(entity);

    SessionEntity saved = repository.findById(RESTART_SESSION_ID).orElseThrow();
    assertNotNull(saved.getMessagesJson());
    assertFalse(saved.getMessagesJson().isBlank());
    assertTrue(saved.getMessagesJson().contains("今天天气怎么样"));
  }

  @Test
  @Order(2)
  @DisplayName("模拟重启（新建 context 重查）历史还在：messages_json 回读后消息完整")
  void historySurvivesContextRestart() {
    // 本方法拿到的是 @DirtiesContext 重建后的全新 context（模拟进程重启后重新起 Spring），
    // 读的是同一 DB 文件--断言历史不丢、四型消息回读完整。
    SessionEntity entity = repository.findById(RESTART_SESSION_ID).orElseThrow();
    List<Message> messages = codec.deserialize(entity.getMessagesJson());

    assertEquals(6, messages.size());
    assertTrue(messages.get(0) instanceof SystemMessage);
    assertEquals("你是 OryxOS 助手", ((SystemMessage) messages.get(0)).getText());
    assertTrue(messages.get(1) instanceof UserMessage);
    assertEquals("今天天气怎么样", ((UserMessage) messages.get(1)).getText());

    assertTrue(messages.get(2) instanceof AssistantMessage);
    AssistantMessage assistant = (AssistantMessage) messages.get(2);
    assertEquals("我帮你查一下天气", assistant.getText());
    assertEquals(1, assistant.getToolCalls().size());
    AssistantMessage.ToolCall call = assistant.getToolCalls().get(0);
    assertEquals("call-1", call.id());
    assertEquals("function", call.type());
    assertEquals("get_weather", call.name());
    assertEquals("{\"city\":\"北京\"}", call.arguments());

    assertTrue(messages.get(3) instanceof ToolResponseMessage);
    ToolResponseMessage toolMessage = (ToolResponseMessage) messages.get(3);
    assertEquals(1, toolMessage.getResponses().size());
    ToolResponseMessage.ToolResponse response = toolMessage.getResponses().get(0);
    assertEquals("call-1", response.id());
    assertEquals("get_weather", response.name());
    assertEquals("北京 晴，25 度", response.responseData());

    assertTrue(messages.get(4) instanceof UserMessage);
    assertEquals("谢谢，那我穿短袖可以吗", ((UserMessage) messages.get(4)).getText());
    assertTrue(messages.get(5) instanceof AssistantMessage);
    assertEquals("可以，今天很暖和", ((AssistantMessage) messages.get(5)).getText());
  }

  @Test
  @DisplayName("手工脚本建出的 sessions 存一条后可读回，各列对齐且身份/时间戳列真实存在")
  void saveRemainsReadableWithScriptBuiltTable() {
    SessionEntity entity = new SessionEntity();
    entity.setSessionId("web:li:weather");
    entity.setProfileName("weather");
    entity.setChannel("web");
    entity.setUserId("li");
    entity.setMessagesJson(codec.serialize(List.of(new UserMessage("你好"))));
    entity.setStatus("active");
    repository.save(entity);

    SessionEntity readBack = repository.findById("web:li:weather").orElseThrow();
    assertEquals("weather", readBack.getProfileName());
    assertEquals("web", readBack.getChannel());
    assertEquals("li", readBack.getUserId());
    assertEquals("active", readBack.getStatus());
    assertTrue(readBack.getMessagesJson().contains("你好"));
    assertNotNull(readBack.getCreatedAt(), "createdAt 由实体层写入 ISO 字符串");
    assertNotNull(readBack.getLastActiveAt(), "lastActiveAt 由实体层写入 ISO 字符串");
    assertNull(readBack.getArchivedAt(), "未归档时 archived_at 为 NULL");
  }

  @Test
  @DisplayName("空历史序列化为 [] 且可回读为空列表（新会话第一次 save 不报错）")
  void emptyHistoryRoundTripsAsEmptyArray() {
    String json = codec.serialize(List.of());
    assertEquals("[]", json);
    assertTrue(codec.deserialize(json).isEmpty());
    assertTrue(codec.deserialize(null).isEmpty());
    assertTrue(codec.deserialize("  ").isEmpty());
  }

  private static List<Message> historyMessages() {
    return List.of(
        new SystemMessage("你是 OryxOS 助手"),
        new UserMessage("今天天气怎么样"),
        AssistantMessage.builder()
            .content("我帮你查一下天气")
            .toolCalls(
                List.of(
                    new AssistantMessage.ToolCall(
                        "call-1", "function", "get_weather", "{\"city\":\"北京\"}")))
            .build(),
        ToolResponseMessage.builder()
            .responses(
                List.of(new ToolResponseMessage.ToolResponse("call-1", "get_weather", "北京 晴，25 度")))
            .build(),
        new UserMessage("谢谢，那我穿短袖可以吗"),
        AssistantMessage.builder().content("可以，今天很暖和").build());
  }
}
