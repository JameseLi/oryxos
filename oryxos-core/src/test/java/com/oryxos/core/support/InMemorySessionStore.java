package com.oryxos.core.support;

import com.oryxos.core.session.SessionStore;
import com.oryxos.core.session.StoredMessage;
import com.oryxos.core.session.StoredSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 内存版 SessionStore（单测用，按 sessionId 分桶）。 */
public class InMemorySessionStore implements SessionStore {

  public final List<StoredSession> sessions = new ArrayList<>();
  public final Map<String, List<StoredMessage>> messagesBySession = new HashMap<>();
  private int sessionSeq = 0;

  @Override
  public StoredSession createSession(String agentName, String channel, String userId) {
    StoredSession session =
        new StoredSession("s-" + (++sessionSeq), agentName, channel, userId, "now", "now");
    sessions.add(session);
    messagesBySession.put(session.id(), new ArrayList<>());
    return session;
  }

  @Override
  public Optional<StoredSession> findSession(String sessionId) {
    return sessions.stream().filter(s -> s.id().equals(sessionId)).findFirst();
  }

  @Override
  public void appendMessage(
      String sessionId, String role, String content, String toolCallsJson, String toolCallId) {
    List<StoredMessage> bucket =
        messagesBySession.computeIfAbsent(sessionId, k -> new ArrayList<>());
    bucket.add(new StoredMessage(bucket.size(), role, content, toolCallsJson, toolCallId, "now"));
  }

  @Override
  public List<StoredMessage> loadMessages(String sessionId) {
    return messagesBySession.getOrDefault(sessionId, List.of());
  }
}
