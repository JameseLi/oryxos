package com.oryxos.core.react;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import com.oryxos.core.tool.OryxTool;

/**
 * Prompt 组装器（FR-004）：system（任务指令 + Bootstrap）+ 会话历史 + 工具清单。
 *
 * <p>已知限制（spec Assumptions）：历史超过 {@link #MAX_HISTORY_MESSAGES} 条时
 * 简单截断保留最近 N 条；上下文窗口压缩策略属后续特性。
 */
@Component
public class PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);

    static final int MAX_HISTORY_MESSAGES = 50;

    /** 组装初始消息序列：system + 历史（含刚追加的用户消息）。 */
    public List<Message> build(String systemPrompt, List<Message> history) {
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        if (history.size() > MAX_HISTORY_MESSAGES) {
            log.warn("会话历史 {} 条超过上限 {}，按最近 {} 条截断（已知限制）",
                    history.size(), MAX_HISTORY_MESSAGES, MAX_HISTORY_MESSAGES);
            history = history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size());
        }
        messages.addAll(history);
        return messages;
    }

    /**
     * 构造工具调用 options。
     *
     * <p><b>宪法原则四双保险</b>：除"直接调 ChatModel 不自动执行"外，显式
     * internalToolExecutionEnabled(false)，确保任何版本行为下 tool call
     * 都只能由 OryxOS ToolExecutor 执行。
     */
    public ToolCallingChatOptions toolOptions(List<OryxTool> tools) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (OryxTool tool : tools) {
            callbacks.add(new OryxToolSchemaAdapter(tool));
        }
        return ToolCallingChatOptions.builder()
                .toolCallbacks(callbacks)
                .internalToolExecutionEnabled(false)
                .build();
    }
}
