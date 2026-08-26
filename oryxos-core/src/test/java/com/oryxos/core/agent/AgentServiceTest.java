package com.oryxos.core.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.react.ReActLoop;
import com.oryxos.core.session.Session;
import com.oryxos.core.session.SessionManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AgentService} 验收 harness（课件「验收 harness」第四个单测类）。
 *
 * <p>覆盖验收点：处理期间 {@code ProfileContext} 可取到当前 Profile；处理抛异常时 finally 也把它清掉； 结束后 Session 被持久化。
 */
class AgentServiceTest {

  private final ReActLoop reActLoop = mock(ReActLoop.class);
  private final SessionManager sessionManager = mock(SessionManager.class);

  private ProfileRegistry registry;
  private AgentService agentService;
  private Profile weatherProfile;

  @BeforeEach
  void setUp() {
    registry = new ProfileRegistry();
    weatherProfile =
        new Profile(
            "weather", null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), null, null);
    registry.register(weatherProfile);
    agentService = new AgentService(registry, reActLoop, sessionManager);
  }

  @AfterEach
  void tearDown() {
    ProfileContext.clear(); // 用例间隔离：防上一用例泄漏污染下一用例
  }

  @Test
  @DisplayName("处理期间 ProfileContext 可取到当前 Profile")
  void profileContextAvailableDuringProcessing() {
    AtomicReference<Profile> seenInsideLoop = new AtomicReference<>();
    when(reActLoop.run(any(), anyString(), any()))
        .thenAnswer(
            invocation -> {
              seenInsideLoop.set(ProfileContext.current()); // 循环执行现场取值
              return "ok";
            });

    String reply = agentService.process(new Session("s-1", "weather"), "你好");

    assertEquals("ok", reply);
    assertEquals(weatherProfile, seenInsideLoop.get(), "循环执行现场取到的是当前 Profile");
  }

  @Test
  @DisplayName("处理中抛异常_ProfileContext也必须被清掉")
  void exceptionDuringProcess_profileContextStillCleared() {
    when(reActLoop.run(any(), anyString(), any())).thenThrow(new RuntimeException("boom"));

    assertThrows(
        RuntimeException.class, () -> agentService.process(new Session("s-1", "weather"), "hi"));

    assertNull(ProfileContext.current()); // finally 没清，下一个复用此线程的请求会拿到别人的 Profile
  }

  @Test
  @DisplayName("结束后 Session 被持久化")
  void sessionPersistedAfterProcessing() {
    when(reActLoop.run(any(), anyString(), any())).thenReturn("done");
    Session session = new Session("s-1", "weather");

    agentService.process(session, "查天气");

    verify(sessionManager).save(session);
  }

  @Test
  @DisplayName("会话引用的 Profile 名不存在时报清晰错误，不悄悄用错")
  void unknownProfileName_throwsClearError() {
    Session session = new Session("s-9", "no-such-profile");

    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> agentService.process(session, "hi"));

    assertEquals("会话引用的 Profile 不存在: no-such-profile", e.getMessage());
  }
}
