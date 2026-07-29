package com.oryxos.storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.oryxos.core.session.SessionStore;
import com.oryxos.core.session.StoredMessage;
import com.oryxos.core.session.StoredSession;
import com.oryxos.storage.entity.SessionEntity;
import com.oryxos.storage.entity.SessionMessageEntity;
import com.oryxos.storage.repository.SessionMessageRepository;
import com.oryxos.storage.repository.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** core {@link SessionStore} 的 JPA 实现（DIP：storage 依赖 core 接口，反向不可）。 */
@Component
public class JpaSessionStore implements SessionStore {

    private final SessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;

    public JpaSessionStore(SessionRepository sessionRepository, SessionMessageRepository messageRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    @Transactional
    public StoredSession createSession(String agentName, String channel, String userId) {
        String now = Instant.now().toString();
        SessionEntity entity = new SessionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setAgentName(agentName);
        entity.setChannel(channel);
        entity.setUserId(userId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return toStored(sessionRepository.save(entity));
    }

    @Override
    public Optional<StoredSession> findSession(String sessionId) {
        return sessionRepository.findById(sessionId).map(JpaSessionStore::toStored);
    }

    @Override
    @Transactional
    public void appendMessage(String sessionId, String role, String content, String toolCallsJson, String toolCallId) {
        int nextSeq = messageRepository.findTopBySessionIdOrderBySeqDesc(sessionId)
                .map(m -> m.getSeq() + 1)
                .orElse(0);
        SessionMessageEntity entity = new SessionMessageEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setSessionId(sessionId);
        entity.setSeq(nextSeq);
        entity.setRole(role);
        entity.setContent(content);
        entity.setToolCallsJson(toolCallsJson);
        entity.setToolCallId(toolCallId);
        entity.setCreatedAt(Instant.now().toString());
        messageRepository.save(entity);

        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setUpdatedAt(Instant.now().toString());
            sessionRepository.save(session);
        });
    }

    @Override
    public List<StoredMessage> loadMessages(String sessionId) {
        return messageRepository.findBySessionIdOrderBySeqAsc(sessionId).stream()
                .map(m -> new StoredMessage(m.getSeq(), m.getRole(), m.getContent(),
                        m.getToolCallsJson(), m.getToolCallId(), m.getCreatedAt()))
                .toList();
    }

    private static StoredSession toStored(SessionEntity entity) {
        return new StoredSession(entity.getId(), entity.getAgentName(), entity.getChannel(),
                entity.getUserId(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
