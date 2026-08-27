package com.oryxos.tool.builtin;

import com.oryxos.core.agent.ProfileContext;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.notify.NotifyChannelAdapter;
import com.oryxos.tool.notify.NotifyTarget;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * {@code notify} 内置工具（课件「三」骨架，归 oryxos-tool）。
 *
 * <p>为什么需要它：定时触发的 Agent（25 节）到点跑完 ReAct 循环后没有人在等响应，结果必须主动送出去； 没有统一出口的话每个 Skill 都要手写「调 http_post 打这个
 * webhook」，重复且不统一。它把「往外推一条消息」这件最常见的事统一掉。
 *
 * <p>推送目标配置在 Profile 的 {@code notify_channels}（type + 渠道特定配置如 url），不暴露在对话里--地址是运行时配置，
 * 不是模型需要知道的信息；LLM 大多数情况只传 {@code content} 就够（课件「二」第四）。
 *
 * <p>完成时点（课件「实现顺序说明」）：类本体第 19 节交付；{@code @Tool} 注册（模型可调用）第 20 节 {@code AnnotatedToolAdapter}
 * 完成；{@code Sandbox.enforce} 白名单接线第 24 节完成（本节先按接口调用的方式留调用位）， 「校验在前、发送在后」的 InOrder 顺序断言随 24 节补进
 * NotifyToolsTest。
 */
@Component
public class NotifyTools {

  private final NotifyChannelAdapter adapter;

  public NotifyTools(NotifyChannelAdapter adapter) {
    this.adapter = adapter;
  }

  /**
   * 把一条消息推送到当前 Agent 配置好的通知渠道。
   *
   * <p>渠道解析语义：{@code channel} 缺省取 {@code notify_channels} 第一个渠道；非空时取第一个 {@code type} 匹配项。
   * 未配置/未命中一律抛 {@link IllegalStateException} 明确报错--不是静默失败，Agent 不会以为发出去了； 异常沿 17 节 {@code
   * ToolExecutor} 既有路径转失败结果并落 {@code tool_invocations} 审计（本工具不新增审计逻辑）。
   */
  @Tool(description = "把一条消息推送到当前 Agent 配置好的通知渠道")
  public ToolResult notify(
      String content,
      @ToolParam(required = false, description = "通知渠道类型，缺省用 Profile 配置的第一个渠道") String channel) {
    Profile profile = ProfileContext.current();
    if (profile == null) {
      throw new IllegalStateException("当前线程未绑定 Profile：notify 需要 ProfileContext 提供的 Agent 上下文");
    }
    NotifyTarget target = resolveTarget(profile, channel);
    // Sandbox.enforce(...) 接线点（第 24 节交付）：出站 HTTP 与 http_post 共用同一道域名白名单墙，
    // 校验必须发生在真正发送之前（「顺序反了就是漏洞」，课件「四」第二批守点 3，24 节以 InOrder 断言钉死）。
    adapter.send(target, content);
    return ToolResult.ok("已推送");
  }

  private NotifyTarget resolveTarget(Profile profile, String channel) {
    if (profile.notifyChannels().isEmpty()) {
      throw new IllegalStateException(
          "Profile '" + profile.name() + "' 未配置 notify_channels，notify 无法推送（不是静默失败）");
    }
    Profile.ChannelRef ref =
        (channel == null || channel.isBlank())
            ? profile.notifyChannels().get(0)
            : profile.notifyChannels().stream()
                .filter(c -> channel.equals(c.type()))
                .findFirst()
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Profile '"
                                + profile.name()
                                + "' 的 notify_channels 中不存在渠道类型 '"
                                + channel
                                + "'"));
    return toNotifyTarget(ref);
  }

  /**
   * ChannelRef.config（Map&lt;String,Object&gt;，YAML 通用形态）收窄为
   * NotifyTarget.config（Map&lt;String,String&gt;）。
   */
  private static NotifyTarget toNotifyTarget(Profile.ChannelRef ref) {
    Map<String, String> narrowed = new LinkedHashMap<>();
    ref.config()
        .forEach(
            (key, value) -> {
              if (value != null) {
                narrowed.put(key, String.valueOf(value));
              }
            });
    return new NotifyTarget(ref.type(), narrowed);
  }
}
