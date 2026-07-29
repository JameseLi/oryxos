package com.oryxos.core.support;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** 测试桩：脚本化返回 ChatResponse 的 ChatModel（断言可检查收到的 Prompt）。 */
public class ScriptedChatModel implements ChatModel {

    private final Queue<ChatResponse> script = new ArrayDeque<>();
    private final java.util.List<Prompt> receivedPrompts = new java.util.ArrayList<>();
    private long sleepMillis = 0;

    public ScriptedChatModel(ChatResponse... responses) {
        script.addAll(List.of(responses));
    }

    public ScriptedChatModel sleep(long millis) {
        this.sleepMillis = millis;
        return this;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        receivedPrompts.add(prompt);
        if (sleepMillis > 0) {
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        ChatResponse response = script.poll();
        if (response == null) {
            throw new IllegalStateException("脚本已耗尽，仍有新的模型调用");
        }
        return response;
    }

    public List<Prompt> receivedPrompts() {
        return receivedPrompts;
    }

    public static ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    public static ChatResponse toolCallResponse(AssistantMessage.ToolCall... toolCalls) {
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .properties(Map.of())
                .toolCalls(List.of(toolCalls))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    public static AssistantMessage.ToolCall toolCall(String id, String name, String arguments) {
        return new AssistantMessage.ToolCall(id, "function", name, arguments);
    }
}
