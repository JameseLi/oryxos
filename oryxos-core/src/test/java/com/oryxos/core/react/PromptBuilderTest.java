package com.oryxos.core.react;

import java.util.List;

import com.oryxos.core.support.FakeToolCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import static org.assertj.core.api.Assertions.assertThat;

/** PromptBuilder 单测：组装、截断、宪法 IV 双保险（internalToolExecutionEnabled=false）。 */
class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    @DisplayName("system 与历史按序组装")
    void systemAndHistoryAssembledInOrder() {
        List<Message> messages = builder.build("系统提示", List.of(new UserMessage("你好")));

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
    }

    @Test
    @DisplayName("历史超过 50 条按最近截断")
    void historyOver50TruncatedToLatest() {
        List<Message> history = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) {
            history.add(new UserMessage("msg-" + i));
        }

        List<Message> messages = builder.build("sys", history);

        // system(1) + 最近50条
        assertThat(messages).hasSize(51);
        UserMessage last = (UserMessage) messages.get(messages.size() - 1);
        assertThat(last.getText()).isEqualTo("msg-59");
    }

    @Test
    @DisplayName("工具 options 禁用自动执行（宪法 IV）")
    void toolOptionsDisableAutoExecution_constitutionIV() {
        ToolCallingChatOptions options = builder.toolOptions(
                List.of(FakeToolCatalog.echoTool("echo")));

        assertThat(options.getInternalToolExecutionEnabled()).isFalse();
        assertThat(options.getToolCallbacks()).hasSize(1);
        assertThat(options.getToolCallbacks().get(0).getToolDefinition().name()).isEqualTo("echo");
    }

    @Test
    @DisplayName("schema 适配器的 call 永不执行")
    void schemaAdapterCallNeverExecutes() {
        ToolCallingChatOptions options = builder.toolOptions(List.of(FakeToolCatalog.echoTool("echo")));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> options.getToolCallbacks().get(0).call("{}"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ToolExecutor");
    }
}
