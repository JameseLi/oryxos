package com.oryxos.core.session;

/**
 * 已持久化的会话（对应 sessions 表）。
 */
public record StoredSession(
        String id,
        String agentName,
        String channel,
        String userId,
        String createdAt,
        String updatedAt) {
}
