package com.oryxos.core.react;

import com.oryxos.core.chat.ChatInvoker;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.prompt.Prompt;
import com.oryxos.core.session.Session;
import com.oryxos.core.tool.ToolExecutor;
import com.oryxos.core.tool.ToolResult;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * 自实现 ReAct 循环（宪法原则三：不依赖 Spring AI 的 Agent 抽象）--Agent 的「想-做-看」核心引擎。
 *
 * <p>循环本身只做调度（课件「第一」）：转圈、判断该不该停、把每轮结果攒起来。拼 Prompt 交 {@link PromptBuilder}、调 LLM 经 {@link
 * ChatInvoker}（Provider 端口，带 sessionId 供 {@code llm_calls} 按会话关联审计）、执行工具交 {@link
 * ToolExecutor}（执行权唯一收口，同样带 sessionId 供 {@code tool_invocations} 关联）。
 *
 * <p>停止条件（课件「一」）：模型这一轮没提出调工具 = 它能给最终答复了，返回收尾；万一一直要调工具停不下来，靠最大轮数兜底（坑一）。 每轮 LLM 响应与工具结果都先存回
 * Session（坑三：可审计、下一轮接得上）。LLM 调用异常不在此捕获--Provider 层已落失败审计，循环上抛。
 */
public class ReActLoop {

  /** 达到最大轮数时的收尾提示（课件字面量，验收 harness 断言 contains("达到最大轮数")）。 */
  public static final String MAX_ITERATIONS_NOTICE = "达到最大轮数，已停止";

  /** 最大轮数默认值（课件「本节交付物」约定；Profile settings.maxIterations 可覆盖）。 */
  static final int DEFAULT_MAX_ITERATIONS = 10;

  private final PromptBuilder promptBuilder;
  private final ChatInvoker chatInvoker;
  private final ToolExecutor toolExecutor;

  public ReActLoop(
      PromptBuilder promptBuilder, ChatInvoker chatInvoker, ToolExecutor toolExecutor) {
    this.promptBuilder = promptBuilder;
    this.chatInvoker = chatInvoker;
    this.toolExecutor = toolExecutor;
  }

  /** 跑完一次「想-做-看」循环，返回最终响应文本。 */
  public String run(Session session, String userMessage, Profile profile) {
    session.append(userMessage);
    int maxIterations = maxIterations(profile);
    for (int i = 0; i < maxIterations; i++) {
      Prompt prompt = promptBuilder.build(session, profile);
      ChatResponse response = chatInvoker.chat(session.id(), profile, prompt);
      session.append(response); // 先累积再说：每一轮都留痕，事后能审计（坑三）
      if (!hasToolCalls(response)) {
        return text(response); // 模型没要调工具 = 它能给答复了，收尾
      }
      for (AssistantMessage.ToolCall call : toolCalls(response)) {
        // 逐个按序执行（核心阶段不做并行）；执行是 ToolExecutor 干的，不是这，也不是 Provider 自动干
        ToolResult result = toolExecutor.execute(session.id(), call);
        session.appendToolResult(call, result);
      }
    }
    return MAX_ITERATIONS_NOTICE; // 坑一兜底：转够强制收尾
  }

  private int maxIterations(Profile profile) {
    Integer configured = profile.settings() == null ? null : profile.settings().maxIterations();
    if (configured == null) {
      return DEFAULT_MAX_ITERATIONS;
    }
    if (configured <= 0) {
      throw new IllegalArgumentException("settings.max_iterations 必须为正数: " + configured);
    }
    // 显式 intValue：null 分支已在上处理，但拆箱交给编译器会被 P3C 判 NPE 风险
    return configured.intValue();
  }

  // ChatResponse 可能为空响应（无 generation）：空响应按「无工具调用的空文本」收尾处理
  private static AssistantMessage outputOf(ChatResponse response) {
    if (response == null || response.getResult() == null) {
      return null;
    }
    return response.getResult().getOutput();
  }

  private static boolean hasToolCalls(ChatResponse response) {
    AssistantMessage output = outputOf(response);
    return output != null && output.hasToolCalls();
  }

  private static List<AssistantMessage.ToolCall> toolCalls(ChatResponse response) {
    AssistantMessage output = outputOf(response);
    return output == null ? List.of() : output.getToolCalls();
  }

  private static String text(ChatResponse response) {
    AssistantMessage output = outputOf(response);
    return output == null ? "" : output.getText();
  }
}
