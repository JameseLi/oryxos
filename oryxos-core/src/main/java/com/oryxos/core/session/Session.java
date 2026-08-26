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
 * 会话域类型（第 17 节最小形态，spec R1）。
 *
 * <p>存着这次对话的全部状态：身份（id + 所属 Profile 名）与消息累积。课件骨架的三个累积入口都在这--{@code
 * append(userMessage)}（用户话入史）、{@code append(resp)}（每轮 LLM 响应先入史再说，可审计）、 {@code
 * appendToolResult(call, result)}（工具结果回填入史）--坑三（不累积就没法审计、下一轮接不上）的落点。
 *
 * <p>完整形态（{@code session_id} 按 channel+user+profile 拼接公式、{@code sessions} 表持久化）是第 18 节交付； 本节 {@code
 * id} 由调用方给定，SessionManager 仅有 {@code save}。
 */
public final class Session {

  private final String id;
  private final String profileName;
  private final List<Message> messages = new ArrayList<>();

  public Session(String id, String profileName) {
    this.id = id;
    this.profileName = profileName;
  }

  public String id() {
    return id;
  }

  public String profileName() {
    return profileName;
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
