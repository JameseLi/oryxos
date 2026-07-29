package com.oryxos.core.session;

/**
 * 已持久化的会话消息（对应 session_messages 表）。
 * role: user / assistant / tool；tool_calls_json 与 toolCallId 视角色使用。
 */
public record StoredMessage(
        int seq,
        String role,
        String content,
        String toolCallsJson,
        String toolCallId,
        String createdAt) {
}
