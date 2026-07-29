package com.oryxos.core.session;

import java.util.List;
import java.util.Optional;

/**
 * 会话存储接口（DIP：core 定义，oryxos-storage 以 JPA 实现）。
 */
public interface SessionStore {

    StoredSession createSession(String agentName, String channel, String userId);

    Optional<StoredSession> findSession(String sessionId);

    /** 追加消息，seq 由存储层在会话内单调递增分配。 */
    void appendMessage(String sessionId, String role, String content, String toolCallsJson, String toolCallId);

    /** 按 seq 升序读取全部消息。 */
    List<StoredMessage> loadMessages(String sessionId);
}
