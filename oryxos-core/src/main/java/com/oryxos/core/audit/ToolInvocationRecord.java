package com.oryxos.core.audit;

/**
 * 工具调用审计记录（对应 tool_invocations 表，FR-017）。只增不改。
 */
public record ToolInvocationRecord(
        String sessionId,
        String toolName,
        String argumentsJson,
        String resultPreview,
        long durationMs,
        boolean success,
        String errorMessage) {
}
