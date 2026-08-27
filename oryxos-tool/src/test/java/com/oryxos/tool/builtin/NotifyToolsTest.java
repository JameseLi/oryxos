package com.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.oryxos.core.agent.ProfileContext;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.notify.NotifyChannelAdapter;
import com.oryxos.tool.notify.NotifyTarget;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link NotifyTools} 验收 harness（课件「四」第二批中不依赖 Sandbox 的子集，本节交付即测）。
 *
 * <p>覆盖验收点：{@code notify_channels} 未配置 -&gt; 明确报错（不是静默失败，Agent 不会以为发出去了）； {@code channel} 参数缺省
 * -&gt; 取第一个渠道；推送内容经 adapter 原样送出。
 *
 * <p>「enforce 先于 send」（课件原文方法名「发送前必须先过白名单校验」，InOrder 钉死）依赖 20 节 Sandbox 前向接口 与 24 节 {@code
 * Sandbox.enforce} 接线，归 24 节补进本测试类--实现侧调用位已就位（NotifyTools#notify 内注释）。
 */
class NotifyToolsTest {

  private final NotifyChannelAdapter adapter = mock(NotifyChannelAdapter.class);
  private final NotifyTools notifyTools = new NotifyTools(adapter);

  @AfterEach
  void clearProfileContext() {
    // 静态 ThreadLocal 用完必须清，否则串到下一个测试（17 节 AgentServiceTest 同款纪律）
    ProfileContext.clear();
  }

  @Test
  @DisplayName("notify_channels 未配置 -> 明确报错，不是静默失败")
  void noChannelsConfiguredFailsLoudly() {
    ProfileContext.set(profile());

    assertThatThrownBy(() -> notifyTools.notify("日报来了", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("notify_channels");
    verify(adapter, org.mockito.Mockito.never())
        .send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("channel 参数缺省 -> 取第一个渠道")
  void blankChannelResolvesToFirstConfiguredChannel() {
    ProfileContext.set(
        profile(
            new Profile.ChannelRef("webhook", Map.of("url", "https://a.example/hook")),
            new Profile.ChannelRef("webhook", Map.of("url", "https://b.example/hook"))));

    ToolResult result = notifyTools.notify("今日科技日报", null);

    assertThat(result.success()).isTrue();
    assertThat(result.output()).isEqualTo("已推送");
    verify(adapter)
        .send(
            argThat(
                (NotifyTarget t) ->
                    "webhook".equals(t.channelType())
                        && "https://a.example/hook".equals(t.config().get("url"))),
            eq("今日科技日报"));
  }

  @Test
  @DisplayName("指定 channel 命中第一个同 type 渠道")
  void namedChannelResolvesFirstMatchingType() {
    ProfileContext.set(
        profile(
            new Profile.ChannelRef("webhook", Map.of("url", "https://a.example/hook")),
            new Profile.ChannelRef("email", Map.of("to", "ops@example.com"))));

    notifyTools.notify("告警：磁盘超 90%", "email");

    verify(adapter)
        .send(
            argThat(
                (NotifyTarget t) ->
                    "email".equals(t.channelType())
                        && "ops@example.com".equals(t.config().get("to"))),
            eq("告警：磁盘超 90%"));
  }

  @Test
  @DisplayName("指定 channel 未命中 -> 明确报错")
  void unknownChannelFailsLoudly() {
    ProfileContext.set(
        profile(new Profile.ChannelRef("webhook", Map.of("url", "https://a.example/hook"))));

    assertThatThrownBy(() -> notifyTools.notify("hello", "feishu"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("feishu");
    verify(adapter, org.mockito.Mockito.never())
        .send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("当前线程未绑定 Profile -> 明确报错（不该静默推送）")
  void missingProfileContextFailsLoudly() {
    assertThatThrownBy(() -> notifyTools.notify("hello", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ProfileContext");
  }

  @Test
  @DisplayName("ChannelRef 的非 String 配置值收窄为字符串、null 值剔除")
  void configValuesNarrowedToStrings() {
    ProfileContext.set(
        profile(
            new Profile.ChannelRef(
                "webhook", Map.of("url", "https://a.example/hook", "timeoutMillis", 3000))));

    notifyTools.notify("hello", null);

    verify(adapter)
        .send(
            argThat(
                (NotifyTarget t) ->
                    "3000".equals(t.config().get("timeoutMillis"))
                        && "https://a.example/hook".equals(t.config().get("url"))),
            eq("hello"));
  }

  private static Profile profile(Profile.ChannelRef... notifyChannels) {
    return new Profile(
        "digest",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(notifyChannels),
        null,
        null,
        null);
  }
}
