package com.oryxos.core.react;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.chat.ChatInvoker;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.prompt.Prompt;
import com.oryxos.core.session.Session;
import com.oryxos.core.tool.ToolExecutor;
import com.oryxos.core.tool.ToolResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * {@link ReActLoop} 验收 harness（课件「验收 harness」第一个单测类）。
 *
 * <p>覆盖验收点：无工具调用一轮收尾；有工具调用则执行并回填进下一轮；转满最大轮数强制停（坑一回归）； 每轮响应和工具结果都累积进 Session（坑三回归）。
 */
class ReActLoopTest {

  private final ChatInvoker chatInvoker = mock(ChatInvoker.class);
  private final ToolExecutor toolExecutor = mock(ToolExecutor.class);
  private final PromptBuilder promptBuilder = mock(PromptBuilder.class);

  private ReActLoop loop;

  @BeforeEach
  void setUp() {
    loop = new ReActLoop(promptBuilder, chatInvoker, toolExecutor);
    when(promptBuilder.build(any(), any())).thenReturn(new Prompt(List.of(), List.of()));
  }

  @Test
  @DisplayName("无工具调用一轮收尾：chat 恰 1 次、返回模型文本、assistant 消息入史")
  void noToolCall_finishesInOneRound() {
    when(chatInvoker.chat(anyString(), any(), any())).thenReturn(textResponse("今天北京晴，25 度"));
    Session session = new Session("s-1", "weather");

    String reply = loop.run(session, "今天天气如何", weatherProfile(null));

    assertEquals("今天北京晴，25 度", reply);
    verify(chatInvoker, times(1)).chat(anyString(), any(), any());
    List<Message> history = session.messages();
    assertEquals(2, history.size(), "用户消息 + assistant 消息");
    assertEquals("今天天气如何", history.get(0).getText());
    assertEquals("今天北京晴，25 度", history.get(1).getText());
  }

  @Test
  @DisplayName("有工具调用则执行并回填进下一轮：工具结果进 Session、第二轮基于含 tool 消息的历史组装")
  void toolCallExecutedAndFedBackIntoNextRound() {
    AssistantMessage.ToolCall call =
        new AssistantMessage.ToolCall("call-1", "function", "http_get", "{\"city\":\"北京\"}");
    when(chatInvoker.chat(anyString(), any(), any()))
        .thenReturn(toolCallResponse(call), textResponse("建议穿短袖"));
    when(toolExecutor.execute(anyString(), any())).thenReturn(ToolResult.ok("北京 25°C 晴"));

    Session session = new Session("s-1", "weather");
    String reply = loop.run(session, "查天气并给穿搭建议", weatherProfile(null));

    assertEquals("建议穿短袖", reply);
    verify(chatInvoker, times(2)).chat(anyString(), any(), any());
    verify(toolExecutor, times(1)).execute(eq("s-1"), any());
    verify(promptBuilder, times(2)).build(any(), any());

    // 回填进下一轮的直接证据：会话历史里 assistant(带 toolCall) 之后紧跟 tool 结果消息
    List<Message> history = session.messages();
    assertEquals(4, history.size(), "user + assistant(toolCall) + tool + assistant(最终)");
    ToolResponseMessage toolMsg = assertInstanceOf(ToolResponseMessage.class, history.get(2));
    assertEquals("call-1", toolMsg.getResponses().get(0).id());
    assertEquals("http_get", toolMsg.getResponses().get(0).name());
    assertEquals("北京 25°C 晴", toolMsg.getResponses().get(0).responseData());
  }

  @Test
  @DisplayName("模型一直要调工具_转满最大轮数强制停")
  void modelKeepsRequestingTools_stopsAtMaxIterations() {
    AssistantMessage.ToolCall httpGetCall =
        new AssistantMessage.ToolCall("call-1", "function", "http_get", "{\"url\":\"w.com\"}");
    // 每轮都要调工具，永不收敛
    when(chatInvoker.chat(anyString(), any(), any())).thenReturn(toolCallResponse(httpGetCall));
    when(toolExecutor.execute(anyString(), any())).thenReturn(ToolResult.ok("data"));

    String reply = loop.run(new Session("s-1", "weather"), "查天气", weatherProfile(10));

    verify(chatInvoker, times(10)).chat(anyString(), any(), any()); // 恰好 10 轮，一轮不多
    assertTrue(reply.contains("达到最大轮数"));
  }

  @Test
  @DisplayName("每轮响应和工具结果都累积进 Session：多轮后消息链完整（坑三回归）")
  void everyRoundAndToolResultAccumulatedIntoSession() {
    AssistantMessage.ToolCall firstCall =
        new AssistantMessage.ToolCall("c1", "function", "http_get", "{\"city\":\"北京\"}");
    AssistantMessage.ToolCall secondCall =
        new AssistantMessage.ToolCall("c2", "function", "http_get", "{\"city\":\"上海\"}");
    when(chatInvoker.chat(anyString(), any(), any()))
        .thenReturn(
            toolCallResponse(firstCall), toolCallResponse(secondCall), textResponse("两地都晴"));
    when(toolExecutor.execute(anyString(), any()))
        .thenReturn(ToolResult.ok("晴"), ToolResult.ok("晴"));

    Session session = new Session("s-1", "weather");
    loop.run(session, "对比京沪天气", weatherProfile(null));

    // user + (assistant + tool) x2 + 最终 assistant = 6 条，全链留痕
    assertEquals(6, session.messages().size());
    assertEquals(
        2, session.messages().stream().filter(m -> m instanceof ToolResponseMessage).count());
  }

  @Test
  @DisplayName("Profile.settings.maxIterations 覆盖默认值：2 轮即停；非法值（<=0）报清晰错误")
  void maxIterationsOverrideAndValidation() {
    AssistantMessage.ToolCall call =
        new AssistantMessage.ToolCall("c1", "function", "http_get", "{}");
    when(chatInvoker.chat(anyString(), any(), any())).thenReturn(toolCallResponse(call));
    when(toolExecutor.execute(anyString(), any())).thenReturn(ToolResult.ok("x"));

    loop.run(new Session("s-1", "weather"), "hi", weatherProfile(2));
    verify(chatInvoker, times(2)).chat(anyString(), any(), any());

    assertThrows(
        IllegalArgumentException.class,
        () -> loop.run(new Session("s-2", "weather"), "hi", weatherProfile(0)));
  }

  @Test
  @DisplayName("LLM 调用异常不吞：直接上抛（Provider 层已落失败审计）")
  void llmExceptionPropagates() {
    when(chatInvoker.chat(anyString(), any(), any()))
        .thenThrow(new RuntimeException("connect timeout"));

    assertThrows(
        RuntimeException.class,
        () -> loop.run(new Session("s-1", "weather"), "hi", weatherProfile(null)));
  }

  // ---- helpers ----

  private static ChatResponse textResponse(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  private static ChatResponse toolCallResponse(AssistantMessage.ToolCall call) {
    AssistantMessage message =
        AssistantMessage.builder().content("").toolCalls(List.of(call)).build();
    return new ChatResponse(List.of(new Generation(message)));
  }

  private Profile weatherProfile(Integer maxIterations) {
    return new Profile(
        "weather",
        null,
        null,
        null,
        List.of("http_get"),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        maxIterations == null ? null : new Profile.Settings(maxIterations, null));
  }
}
