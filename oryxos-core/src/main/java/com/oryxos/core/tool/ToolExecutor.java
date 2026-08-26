package com.oryxos.core.tool;

import com.oryxos.core.audit.ToolInvocationAudit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;

/**
 * 工具执行器（课件「配角二」）：LLM 返回的工具调用请求在这、且只在这执行。
 *
 * <p>职责：从工具注册表按名找到工具 -> 过一道沙箱/白名单检查（调用位预留，24 节接线）-> 执行 -> 结果包成 {@link ToolResult} 返回 ->
 * 记一条调用审计。审计口径跟上一节 Provider 同源：成功要记、失败也要记 （{@code tool_invocations} 本就有 {@code success}/{@code
 * error_message} 两列）--一次工具调用不管成没成，事后都得能查到。
 *
 * <p>「工具执行只在这一个地方发生」也是上一节关掉 Spring AI 自动执行的原因：执行权必须收在这里，不能有第二条路（宪法原则四）。
 * 工具失败是业务语义：结果文本回填给模型继续对话，不打断；但异常不吞--审计与日志都要留痕。
 */
public class ToolExecutor {

  private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

  private final ToolRegistry toolRegistry;
  private final ToolInvocationAudit audit;

  public ToolExecutor(ToolRegistry toolRegistry, ToolInvocationAudit audit) {
    this.toolRegistry = toolRegistry;
    this.audit = audit;
  }

  /**
   * 执行一次工具调用（异常全部转为失败结果，绝不打断对话）。
   *
   * @param sessionId 所属会话（{@code tool_invocations} 按 session 关联）
   * @param toolCall 模型回传的工具调用请求（名 + JSON 入参）
   */
  public ToolResult execute(String sessionId, AssistantMessage.ToolCall toolCall) {
    String toolName = toolCall.name();
    String arguments = toolCall.arguments();
    long startNanos = System.nanoTime();
    ToolResult result;
    try {
      OryxTool tool =
          toolRegistry
              .find(toolName)
              .orElseThrow(() -> new IllegalStateException("未知工具: " + toolName));
      // Sandbox.enforce(...) 接线点（第 24 节交付）：沙箱/白名单检查应在此处、真正执行之前；
      // 校验失败将抛 SandboxViolationException 并走下方既有失败审计路径（spec FR-016 预留位）。
      result = tool.execute(arguments);
    } catch (RuntimeException e) {
      // 异常不吞：审计 success=false + 日志留痕，原因回填给模型自行纠正
      String reason = e.getMessage() == null ? e.toString() : e.getMessage();
      log.warn("工具执行失败已捕获: tool={} session={} cause={}", toolName, sessionId, reason);
      result = ToolResult.failure(reason);
    }
    long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
    audit.record(
        sessionId,
        toolName,
        arguments,
        result.success() ? result.output() : null,
        result.success(),
        result.errorMessage(),
        elapsedMillis);
    return result;
  }
}
