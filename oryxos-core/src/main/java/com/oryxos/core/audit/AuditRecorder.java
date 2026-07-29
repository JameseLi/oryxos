package com.oryxos.core.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 审计落库门面：统一的截断策略（data-model.md：llm 快照 ≤16KB、工具结果预览 ≤4KB）
 * 与失败兜底（写库失败不阻断主流程，但必须输出结构化 ERROR 日志——SC-005 验收
 * 要求该日志计数为 0，见 quickstart 审计核对环节）。
 */
@Component
public class AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    /** llm_calls 请求/响应快照上限（字符，≈16KB） */
    static final int LLM_SNAPSHOT_MAX = 16 * 1024;
    /** tool_invocations 结果预览上限（字符，≈4KB） */
    static final int TOOL_PREVIEW_MAX = 4 * 1024;

    private final AuditStore auditStore;

    public AuditRecorder(AuditStore auditStore) {
        this.auditStore = auditStore;
    }

    public void recordLlmCall(LlmCallRecord record) {
        LlmCallRecord truncated = new LlmCallRecord(
                record.sessionId(), record.providerName(), record.model(), record.iteration(),
                truncate(record.requestJson(), LLM_SNAPSHOT_MAX),
                truncate(record.responseJson(), LLM_SNAPSHOT_MAX),
                record.requestBytes(), record.responseBytes(),
                record.promptTokens(), record.completionTokens(),
                record.durationMs(), record.success(), record.errorMessage());
        try {
            auditStore.saveLlmCall(truncated);
        } catch (Exception e) {
            // 宪法 VI 兜底：主流程可继续，但必须留下可验收的 ERROR 痕迹
            log.error("AUDIT_WRITE_FAILURE type=llm_call session={} provider={} model={} iteration={} cause={}",
                    record.sessionId(), record.providerName(), record.model(), record.iteration(), e.toString());
        }
    }

    public void recordToolInvocation(ToolInvocationRecord record) {
        ToolInvocationRecord truncated = new ToolInvocationRecord(
                record.sessionId(), record.toolName(), record.argumentsJson(),
                truncate(record.resultPreview(), TOOL_PREVIEW_MAX),
                record.durationMs(), record.success(), record.errorMessage());
        try {
            auditStore.saveToolInvocation(truncated);
        } catch (Exception e) {
            log.error("AUDIT_WRITE_FAILURE type=tool_invocation session={} tool={} cause={}",
                    record.sessionId(), record.toolName(), e.toString());
        }
    }

    private static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...(truncated, original=" + text.length() + " chars)";
    }
}
