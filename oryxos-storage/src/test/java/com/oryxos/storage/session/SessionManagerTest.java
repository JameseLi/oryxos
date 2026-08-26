package com.oryxos.storage.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.session.Session;
import com.oryxos.storage.entity.SessionEntity;
import com.oryxos.storage.repository.SessionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link JpaSessionManager} 验收 harness（课件「验收 harness」第一个单测类，mock 仓储）。
 *
 * <p>覆盖验收点：同一三元组两次 {@code getOrCreate} 返回<b>同一个</b> Session（幂等）；channel/user/profile 任一不同则是不同
 * Session；id 生成只此一处（拼接公式只存在于 JpaSessionManager）；{@code get}/{@code save} 语义。
 */
class SessionManagerTest {

  private SessionRepository repository;
  private JpaSessionManager sessionManager;

  @BeforeEach
  void setUp() {
    repository = mock(SessionRepository.class);
    sessionManager = new JpaSessionManager(repository);
  }

  @Test
  @DisplayName("同一三元组_历次getOrCreate都是同一个Session")
  void sameTripleGetOrCreateAlwaysSameSession() {
    when(repository.findById(any())).thenReturn(Optional.empty());

    Session first = sessionManager.getOrCreate("cli", "wang", "default");
    Session second = sessionManager.getOrCreate("cli", "wang", "default");
    assertEquals(first.id(), second.id()); // 幂等：多轮对话靠它串起来

    Session other = sessionManager.getOrCreate("web", "wang", "default");
    assertNotEquals(first.id(), other.id()); // channel 不同就是不同会话
  }

  @Test
  @DisplayName("user 或 profile 任一不同也是不同会话（三元组隔离）")
  void differentUserOrProfileYieldsDifferentSession() {
    when(repository.findById(any())).thenReturn(Optional.empty());

    Session base = sessionManager.getOrCreate("cli", "wang", "default");
    assertNotEquals(base.id(), sessionManager.getOrCreate("cli", "li", "default").id());
    assertNotEquals(base.id(), sessionManager.getOrCreate("cli", "wang", "weather").id());
  }

  @Test
  @DisplayName("session_id 按 channel:user:profile 拼接，只存在一处（get 用同公式能查回）")
  void sessionIdFollowsSingleJoinFormula() {
    when(repository.findById(any())).thenReturn(Optional.empty());

    Session created = sessionManager.getOrCreate("cli", "wang", "default");
    assertEquals("cli:wang:default", created.id());
    assertEquals("cli", created.channel());
    assertEquals("wang", created.userId());
    assertEquals("default", created.profileName());

    // get 与 getOrCreate 走同一公式（拼接逻辑收敛在一处的外在证据）：两串查询的 id 都是公式产物
    sessionManager.get("cli:wang:default");
    verify(repository, org.mockito.Mockito.times(2)).findById("cli:wang:default");
  }

  @Test
  @DisplayName("getOrCreate 查有则携历史恢复（重启后同一三元组拿回老会话）")
  void getOrCreateExistingSessionRestoresHistory() {
    SessionEntity entity = new SessionEntity();
    entity.setSessionId("cli:wang:default");
    entity.setProfileName("default");
    entity.setChannel("cli");
    entity.setUserId("wang");
    entity.setMessagesJson("[{\"type\":\"user\",\"content\":\"上次聊到一半\"}]");
    when(repository.findById("cli:wang:default")).thenReturn(Optional.of(entity));

    Session session = sessionManager.getOrCreate("cli", "wang", "default");
    assertEquals(1, session.messages().size());
    assertEquals("上次聊到一半", session.messages().get(0).getText());
  }

  @Test
  @DisplayName("get 查无返回 empty；查有携历史返回")
  void getReturnsEmptyOrRestoredSession() {
    when(repository.findById("nope")).thenReturn(Optional.empty());
    assertTrue(sessionManager.get("nope").isEmpty());

    SessionEntity entity = new SessionEntity();
    entity.setSessionId("cli:wang:default");
    entity.setProfileName("default");
    entity.setMessagesJson("[]");
    when(repository.findById("cli:wang:default")).thenReturn(Optional.of(entity));
    assertEquals("cli:wang:default", sessionManager.get("cli:wang:default").orElseThrow().id());
  }

  @Test
  @DisplayName("save 查无则整行插入：身份列与序列化历史齐全")
  void saveNewSessionInsertsFullRow() {
    when(repository.findById(any())).thenReturn(Optional.empty());
    Session session = sessionManager.getOrCreate("cli", "wang", "default");
    session.append("你好");
    session.append("再见");

    sessionManager.save(session);

    ArgumentCaptor<SessionEntity> captor = ArgumentCaptor.forClass(SessionEntity.class);
    verify(repository).save(captor.capture());
    SessionEntity saved = captor.getValue();
    assertEquals("cli:wang:default", saved.getSessionId());
    assertEquals("default", saved.getProfileName());
    assertEquals("cli", saved.getChannel());
    assertEquals("wang", saved.getUserId());
    assertEquals("active", saved.getStatus());
    assertTrue(saved.getMessagesJson().contains("你好"));
    assertTrue(saved.getMessagesJson().contains("再见"));
  }

  @Test
  @DisplayName("save 查有则只更新历史与活跃时间：channel/user 等身份列不被抹掉")
  void saveExistingSessionUpdatesHistoryOnly() {
    SessionEntity existing = new SessionEntity();
    existing.setSessionId("cli:wang:default");
    existing.setProfileName("default");
    existing.setChannel("cli");
    existing.setUserId("wang");
    existing.setMessagesJson("[]");
    existing.setStatus("active");
    when(repository.findById("cli:wang:default")).thenReturn(Optional.of(existing));

    // 第 17 节时代的会话（channel/user 为 null）经 save 落库：身份列不被动
    Session legacySession = new Session("cli:wang:default", "default");
    legacySession.append("老会话新消息");
    sessionManager.save(legacySession);

    verify(repository).save(existing);
    assertEquals("cli", existing.getChannel(), "既有身份列不被 null 覆盖");
    assertEquals("wang", existing.getUserId());
    assertTrue(existing.getMessagesJson().contains("老会话新消息"));
  }

  @Test
  @DisplayName("messages_json 损坏（未知 type）反序列化抛清晰异常，不静默吞历史")
  void corruptedMessagesJsonFailsLoudly() {
    SessionEntity entity = new SessionEntity();
    entity.setSessionId("cli:wang:default");
    entity.setProfileName("default");
    entity.setMessagesJson("[{\"type\":\"alien\",\"content\":\"?\"}]");
    when(repository.findById("cli:wang:default")).thenReturn(Optional.of(entity));

    assertThrows(
        IllegalStateException.class, () -> sessionManager.getOrCreate("cli", "wang", "default"));
  }
}
