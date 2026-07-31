package com.oryxos.core.react;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.audit.AuditRecorder;
import com.oryxos.core.audit.LlmCallRecord;
import com.oryxos.core.tool.ToolExecutor;
import com.oryxos.core.tool.ToolProgressListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.stereotype.Component;

/**
 * 自实现 ReAct 循环（宪法原则三：不依赖 Spring AI 的 Agent 抽象）。
 *
 * <p>循环：调 LLM → 有 tool call 则经 {@link ToolExecutor} 执行、回填 ToolResponseMessage
 * 继续循环；无则返回最终文本（FR-005/006）。 上限 maxIterations（FR-007）；无法解析的 tool call 反馈模型纠正并计一次迭代（FR-009）。 每轮 LLM
 * 调用落 llm_calls 审计（FR-016），循环中产出的消息经 messageSink 逐条外抛持久化。
 */
@Component
public class ReActLoop {

  private static final Logger log = LoggerFactory.getLogger(ReActLoop.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 达到迭代上限时的用户可见说明（FR-007）。 */
  public static final String MAX_ITERATIONS_NOTICE = "（已达推理步数上限，以上为当前已掌握的信息；可换个问法或缩小问题范围重试）";

  private final ToolExecutor toolExecutor;
  private final AuditRecorder auditRecorder;

  public ReActLoop(ToolExecutor toolExecutor, AuditRecorder auditRecorder) {
    this.toolExecutor = toolExecutor;
    this.auditRecorder = auditRecorder;
  }

  /**
   * @param model 目标 ChatModel（Provider 层显式解析）
   * @param messages 初始消息（system + 历史 + 本轮用户消息），循环内就地追加
   * @param options 工具调用 options（已禁用自动执行）
   * @param maxIterations 迭代上限
   * @param audit 审计上下文
   * @param listener 工具进度监听（可空）
   * @param messageSink 循环产出消息的外抛通道（持久化），assistant/tool 消息逐条推送
   * @return 最终回答文本
   */
  public String run(
      ChatModel model,
      List<Message> messages,
      ToolCallingChatOptions options,
      int maxIterations,
      LlmAuditContext audit,
      ToolProgressListener listener,
      Consumer<Message> messageSink) {
    String lastText = "";
    for (int iteration = 0; iteration < maxIterations; iteration++) {
      Prompt prompt = new Prompt(messages, options);
      ChatResponse response = callAudited(model, prompt, iteration, audit);
      AssistantMessage output = response.getResult().getOutput();

      if (!response.hasToolCalls()) {
        messageSink.accept(output);
        return output.getText();
      }

      List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
      if (hasMalformed(toolCalls)) {
        // FR-009：反馈模型纠正，计入迭代上限，系统不崩溃
        log.warn("无法解析的 tool call（第 {} 轮）: {}", iteration, toolCalls);
        UserMessage feedback =
            new UserMessage(
                "你刚才发起的工具调用无法解析（工具名缺失或为空）。请严格按照工具清单中的名称与参数 schema 重新发起调用；" + "如不需要工具请直接回答。");
        messages.add(feedback);
        messageSink.accept(feedback);
        continue;
      }

      // assistant 消息（含 toolCalls）入列并外抛
      messages.add(output);
      messageSink.accept(output);
      String outputText = output.getText();
      if (outputText != null && !outputText.isBlank()) {
        lastText = outputText;
      }

      // 执行全部 tool call（同一响应内可多个），一次性回填（FR-005 / Edge Case）
      List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
      for (AssistantMessage.ToolCall toolCall : toolCalls) {
        toolResponses.add(toolExecutor.execute(audit.sessionId(), toolCall, listener));
      }
      ToolResponseMessage toolResponseMessage =
          ToolResponseMessage.builder().responses(toolResponses).build();
      messages.add(toolResponseMessage);
      messageSink.accept(toolResponseMessage);
    }
    log.warn("ReAct 达到迭代上限 {}: session={}", maxIterations, audit.sessionId());
    return (lastText.isBlank() ? "" : lastText + "\n\n") + MAX_ITERATIONS_NOTICE;
  }

  private boolean hasMalformed(List<AssistantMessage.ToolCall> toolCalls) {
    return toolCalls == null
        || toolCalls.isEmpty()
        || toolCalls.stream()
            .anyMatch(tc -> tc == null || tc.name() == null || tc.name().isBlank());
  }

  /** 调模型并落 llm_calls 审计：成功记用量/耗时，失败记错误后原样上抛（FR-016/018）。 */
  private ChatResponse callAudited(
      ChatModel model, Prompt prompt, int iteration, LlmAuditContext audit) {
    long startNanos = System.nanoTime();
    try {
      ChatResponse response = model.call(prompt);
      long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
      Integer promptTokens = null;
      Integer completionTokens = null;
      if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
        promptTokens = response.getMetadata().getUsage().getPromptTokens();
        completionTokens = response.getMetadata().getUsage().getCompletionTokens();
      }
      String requestJson = toJson(prompt.getInstructions());
      String responseJson = toJson(response.getResult().getOutput());
      auditRecorder.recordLlmCall(
          new LlmCallRecord(
              audit.sessionId(),
              audit.providerName(),
              audit.model(),
              iteration,
              requestJson,
              responseJson,
              lengthOf(requestJson),
              lengthOf(responseJson),
              promptTokens,
              completionTokens,
              durationMs,
              true,
              null));
      return response;
    } catch (Exception e) {
      long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
      String requestJson = toJson(prompt.getInstructions());
      auditRecorder.recordLlmCall(
          new LlmCallRecord(
              audit.sessionId(),
              audit.providerName(),
              audit.model(),
              iteration,
              requestJson,
              null,
              lengthOf(requestJson),
              null,
              null,
              null,
              durationMs,
              false,
              e.getMessage() == null ? e.toString() : e.getMessage()));
      throw e;
    }
  }

  private static Integer lengthOf(String value) {
    return value == null ? null : value.length();
  }

  private static String toJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception e) {
      return String.valueOf(value);
    }
  }

  /** LLM 调用审计上下文。 */
  public record LlmAuditContext(String sessionId, String providerName, String model) {}
}
