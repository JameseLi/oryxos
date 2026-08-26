package com.oryxos.core.session;

import com.oryxos.core.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * 会话域类型（第 17 节最小形态，第 18 节补全会话身份与历史恢复）。
 *
 * <p>存着这次对话的全部状态：身份（id + 所属 Profile 名 + 接入三元组的 channel/user）与消息累积。课件骨架的三个累积入口都在这-- {@code
 * append(userMessage)}（用户话入史）、{@code append(resp)}（每轮 LLM 响应先入史再说，可审计）、 {@code
 * appendToolResult(call, result)}（工具结果回填入史）--坑三（不累积就没法审计、下一轮接不上）的落点。
 *
 * <p>第 18 节起 id 由 {@link SessionManager} 按 channel+user+profile 三元组生成（拼接只在那一处）； {@link #restore}
 * 供持久层把存量的 {@code messages_json} 恢复成会话。两参构造器保留给第 17 节时代的调用方（channel/user 未知，置 null），兼容优先。
 */
public final class Session {

  private final String id;
  private final String profileName;
  private final String channel;
  private final String userId;
  private final List<Message> messages = new ArrayList<>();

  /** 第 17 节形态：调用方给定 id（channel/user 未知，置 null）。 */
  public Session(String id, String profileName) {
    this(id, profileName, null, null);
  }

  /** 第 18 节形态：id 已由 SessionManager 按三元组生成，channel/user 随身携带。 */
  public Session(String id, String profileName, String channel, String userId) {
    this.id = id;
    this.profileName = profileName;
    this.channel = channel;
    this.userId = userId;
  }

  /**
   * 从存量历史恢复会话（第 18 节持久层回读用：进程重启后同一三元组拿回带历史的老会话）。
   *
   * @param messages 存量历史（防御拷贝入列，外部改动不得穿透）
   */
  public static Session restore(
      String id, String profileName, String channel, String userId, List<Message> messages) {
    Session session = new Session(id, profileName, channel, userId);
    if (messages != null) {
      session.messages.addAll(messages);
    }
    return session;
  }

  public String id() {
    return id;
  }

  public String profileName() {
    return profileName;
  }

  /** 接入 Channel（"cli" / "web" / "scheduler"；第 17 节时代会话可能为 null）。 */
  public String channel() {
    return channel;
  }

  /** 用户标识（第 17 节时代会话可能为 null）。 */
  public String userId() {
    return userId;
  }

  /** 当前对话历史（防御拷贝：外部改动不得穿透进会话）。 */
  public List<Message> messages() {
    return List.copyOf(messages);
  }

  /** 用户消息入史（循环开始前先追加，之后每轮才基于完整历史组装 Prompt）。 */
  public void append(String userMessage) {
    messages.add(new UserMessage(userMessage));
  }

  /** LLM 响应入史：取首个 generation 的 assistant 消息原样保存（带 toolCalls 的也原样，下一轮要靠它配对）。 */
  public void append(ChatResponse response) {
    // 空响应/无 generation 不入史（Optional 链：getResult 在空列表时确实返回 null，字节码已核）
    Optional.ofNullable(response)
        .map(ChatResponse::getResult)
        .map(Generation::getOutput)
        .ifPresent(messages::add);
  }

  /** 工具结果回填入史：按 call 的 id/name 配对构造 tool 消息（模型协议要求响应能对应上请求）。 */
  public void appendToolResult(AssistantMessage.ToolCall toolCall, ToolResult result) {
    messages.add(
        ToolResponseMessage.builder()
            .responses(
                List.of(
                    new ToolResponseMessage.ToolResponse(
                        toolCall.id(), toolCall.name(), result.toModelText())))
            .build());
  }
}
