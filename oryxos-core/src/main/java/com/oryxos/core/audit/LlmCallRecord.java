package com.oryxos.core.audit;

/**
 * LLM 调用审计记录（对应 llm_calls 表，FR-016）。只增不改。
 */
public record LlmCallRecord(
        String sessionId,
        String providerName,
        String model,
        int iteration,
        String requestJson,
        String responseJson,
        Integer requestBytes,
        Integer responseBytes,
        Integer promptTokens,
        Integer completionTokens,
        long durationMs,
        boolean success,
        String errorMessage) {
}
