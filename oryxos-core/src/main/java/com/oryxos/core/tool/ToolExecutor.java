package com.oryxos.core.tool;

import com.oryxos.core.audit.AuditRecorder;
import com.oryxos.core.audit.ToolInvocationRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

/**
 * 工具调度执行器（宪法原则四：工具执行的唯一入口）。 职责：按名派发、失败捕获（FR-008：失败是回填给模型的业务结果，不是异常）、 进度事件（FR-011）、tool_invocations
 * 审计（FR-017）。
 */
@Component
public class ToolExecutor {

  private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

  private final ToolCatalog toolCatalog;
  private final AuditRecorder auditRecorder;

  public ToolExecutor(ToolCatalog toolCatalog, AuditRecorder auditRecorder) {
    this.toolCatalog = toolCatalog;
    this.auditRecorder = auditRecorder;
  }

  /** 执行一次模型请求的工具调用，返回可直接回填的 ToolResponse。 任何失败（未知工具/执行异常/业务失败）都转化为结果文本，绝不抛出打断对话。 */
  public ToolResponseMessage.ToolResponse execute(
      String sessionId, AssistantMessage.ToolCall toolCall, ToolProgressListener listener) {
    String toolName = toolCall.name();
    String arguments = toolCall.arguments();
    ToolProgressListener safeListener = listener == null ? ToolProgressListener.noop() : listener;

    safeListener.onToolStart(toolName, arguments);
    long startNanos = System.nanoTime();
    ToolResult result;
    try {
      OryxTool tool =
          toolCatalog
              .find(toolName)
              .orElseThrow(() -> new IllegalStateException("未知工具: " + toolName));
      result = tool.execute(arguments);
    } catch (Exception e) {
      log.warn("工具执行异常已捕获: tool={} session={} cause={}", toolName, sessionId, e.toString());
      result = ToolResult.failure(e.getMessage() == null ? e.toString() : e.getMessage());
    }
    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

    auditRecorder.recordToolInvocation(
        new ToolInvocationRecord(
            sessionId,
            toolName,
            arguments,
            result.success() ? result.output() : null,
            durationMs,
            result.success(),
            result.errorMessage()));
    safeListener.onToolEnd(toolName, result.success(), durationMs, result.errorMessage());
    return new ToolResponseMessage.ToolResponse(toolCall.id(), toolName, result.toModelText());
  }
}
