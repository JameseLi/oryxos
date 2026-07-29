package com.oryxos.core.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * 会话上下文管理：Spring AI 消息模型 ↔ 持久化行的双向映射。
 * 持久化委托 {@link SessionStore}（DIP），本类不感知 JPA。
 *
 * <p>tool 消息一行一个 ToolResponse；重建时把 assistant(toolCalls) 之后连续的
 * tool 行合并回一个 {@link ToolResponseMessage}。
 */
@Component
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SessionStore sessionStore;

    public SessionManager(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public StoredSession getOrCreateSession(String sessionId, String agentName, String channel, String userId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionStore.findSession(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        }
        return sessionStore.createSession(agentName, channel, userId);
    }

    /** 追加一条 Spring AI 消息（user/assistant/tool 三类）。 */
    public void append(String sessionId, Message message) {
        if (message instanceof UserMessage user) {
            sessionStore.appendMessage(sessionId, "user", user.getText(), null, null);
        } else if (message instanceof AssistantMessage assistant) {
            String toolCallsJson = null;
            if (assistant.hasToolCalls()) {
                try {
                    toolCallsJson = MAPPER.writeValueAsString(assistant.getToolCalls().stream()
                            .map(tc -> Map.of("id", nullSafe(tc.id()), "type", nullSafe(tc.type()),
                                    "name", nullSafe(tc.name()), "arguments", nullSafe(tc.arguments())))
                            .toList());
                } catch (Exception e) {
                    log.warn("序列化 tool_calls 失败，按无工具调用存储: {}", e.toString());
                }
            }
            sessionStore.appendMessage(sessionId, "assistant", assistant.getText(), toolCallsJson, null);
        } else if (message instanceof ToolResponseMessage toolResponse) {
            for (ToolResponseMessage.ToolResponse resp : toolResponse.getResponses()) {
                sessionStore.appendMessage(sessionId, "tool", resp.responseData(), null, resp.id());
            }
        } else {
            log.debug("忽略不支持的消息类型: {}", message.getClass().getSimpleName());
        }
    }

    /** 读取历史并重建为 Spring AI 消息序列（按 seq 升序）。 */
    public List<Message> history(String sessionId) {
        List<StoredMessage> rows = sessionStore.loadMessages(sessionId);
        List<Message> messages = new ArrayList<>();
        List<ToolResponseMessage.ToolResponse> pendingToolResponses = new ArrayList<>();
        for (StoredMessage row : rows) {
            switch (row.role()) {
                case "user" -> {
                    flushToolResponses(messages, pendingToolResponses);
                    messages.add(new UserMessage(row.content() == null ? "" : row.content()));
                }
                case "assistant" -> {
                    flushToolResponses(messages, pendingToolResponses);
                    List<AssistantMessage.ToolCall> toolCalls = parseToolCalls(row.toolCallsJson());
                    messages.add(AssistantMessage.builder()
                            .content(row.content() == null ? "" : row.content())
                            .properties(Map.of())
                            .toolCalls(toolCalls)
                            .build());
                }
                case "tool" -> pendingToolResponses.add(new ToolResponseMessage.ToolResponse(
                        row.toolCallId(), "", row.content() == null ? "" : row.content()));
                default -> log.warn("未知消息角色已跳过: role={} seq={}", row.role(), row.seq());
            }
        }
        flushToolResponses(messages, pendingToolResponses);
        return messages;
    }

    private static void flushToolResponses(List<Message> messages,
                                           List<ToolResponseMessage.ToolResponse> pending) {
        if (!pending.isEmpty()) {
            messages.add(ToolResponseMessage.builder().responses(new ArrayList<>(pending)).build());
            pending.clear();
        }
    }

    private static List<AssistantMessage.ToolCall> parseToolCalls(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, String>> raw = MAPPER.readValue(json, new TypeReference<>() {
            });
            return raw.stream()
                    .map(m -> new AssistantMessage.ToolCall(m.get("id"), m.get("type"),
                            m.get("name"), m.get("arguments")))
                    .toList();
        } catch (Exception e) {
            log.warn("反序列化 tool_calls 失败，按无工具调用处理: {}", e.toString());
            return List.of();
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
