package com.oryxos.core.react;

import java.util.ArrayList;
import java.util.List;

import com.oryxos.core.audit.AuditRecorder;
import com.oryxos.core.support.FakeToolCatalog;
import com.oryxos.core.support.RecordingAuditStore;
import com.oryxos.core.support.ScriptedChatModel;
import com.oryxos.core.tool.ToolExecutor;
import com.oryxos.core.tool.ToolProgressListener;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ReActLoop 单测（FR-005/006/007/009）：脚本化 stub ChatModel 驱动。
 */
class ReActLoopTest {

    private final RecordingAuditStore auditStore = new RecordingAuditStore();
    private final FakeToolCatalog catalog = new FakeToolCatalog().register(FakeToolCatalog.echoTool("echo"));
    private final ToolExecutor executor = new ToolExecutor(catalog, new AuditRecorder(auditStore));
    private final ReActLoop loop = new ReActLoop(executor, new AuditRecorder(auditStore));

    private ReActLoop.LlmAuditContext audit() {
        return new ReActLoop.LlmAuditContext("sess-1", "deepseek", "deepseek-chat");
    }

    private List<Message> sinkMessages() {
        return new ArrayList<>();
    }

    @Test
    void 无工具调用时直接返回最终文本_FR006() {
        ScriptedChatModel model = new ScriptedChatModel(ScriptedChatModel.textResponse("最终回答"));
        List<Message> sink = sinkMessages();

        String result = loop.run(model, new ArrayList<>(List.of(new UserMessage("hi"))),
                ToolCallingChatOptions.builder().build(), 10, audit(), null, sink::add);

        assertThat(result).isEqualTo("最终回答");
        assertThat(model.receivedPrompts()).hasSize(1);
        assertThat(auditStore.llmCalls).hasSize(1);
        assertThat(auditStore.llmCalls.get(0).success()).isTrue();
        assertThat(auditStore.toolInvocations).isEmpty();
    }

    @Test
    void 一轮工具调用后回填并输出最终回答_FR005() {
        ScriptedChatModel model = new ScriptedChatModel(
                ScriptedChatModel.toolCallResponse(ScriptedChatModel.toolCall("c1", "echo", "{\"text\":\"a\"}")),
                ScriptedChatModel.textResponse("基于工具结果的回答"));
        List<Message> sink = sinkMessages();

        String result = loop.run(model, new ArrayList<>(List.of(new UserMessage("查一下"))),
                ToolCallingChatOptions.builder().build(), 10, audit(), ToolProgressListener.noop(), sink::add);

        assertThat(result).isEqualTo("基于工具结果的回答");
        assertThat(model.receivedPrompts()).hasSize(2);
        // 第二轮 Prompt 中应包含 ToolResponseMessage（回填证据）
        boolean hasToolResponse = model.receivedPrompts().get(1).getInstructions().stream()
                .anyMatch(m -> m instanceof ToolResponseMessage);
        assertThat(hasToolResponse).isTrue();
        assertThat(auditStore.llmCalls).hasSize(2);
        assertThat(auditStore.toolInvocations).hasSize(1);
        assertThat(auditStore.toolInvocations.get(0).toolName()).isEqualTo("echo");
        assertThat(auditStore.toolInvocations.get(0).success()).isTrue();
    }

    @Test
    void 单次响应多个工具调用全部执行并一次性回填() {
        ScriptedChatModel model = new ScriptedChatModel(
                ScriptedChatModel.toolCallResponse(
                        ScriptedChatModel.toolCall("c1", "echo", "{\"text\":\"a\"}"),
                        ScriptedChatModel.toolCall("c2", "echo", "{\"text\":\"b\"}")),
                ScriptedChatModel.textResponse("done"));
        List<Message> sink = sinkMessages();

        String result = loop.run(model, new ArrayList<>(List.of(new UserMessage("hi"))),
                ToolCallingChatOptions.builder().build(), 10, audit(), null, sink::add);

        assertThat(result).isEqualTo("done");
        assertThat(auditStore.toolInvocations).hasSize(2);
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) model.receivedPrompts().get(1)
                .getInstructions().stream().filter(m -> m instanceof ToolResponseMessage).findFirst().orElseThrow();
        assertThat(toolResponseMessage.getResponses()).hasSize(2);
    }

    @Test
    void 无法解析的工具调用反馈纠正且计一次迭代_FR009() {
        ScriptedChatModel model = new ScriptedChatModel(
                ScriptedChatModel.toolCallResponse(ScriptedChatModel.toolCall("c1", "", "{}")),
                ScriptedChatModel.textResponse("纠正后的回答"));
        List<Message> sink = sinkMessages();

        String result = loop.run(model, new ArrayList<>(List.of(new UserMessage("hi"))),
                ToolCallingChatOptions.builder().build(), 10, audit(), null, sink::add);

        assertThat(result).isEqualTo("纠正后的回答");
        assertThat(model.receivedPrompts()).hasSize(2); // malformed 也算一轮
        assertThat(auditStore.toolInvocations).isEmpty(); // 未真正执行任何工具
        // 反馈消息已外抛且进入下一轮 Prompt
        boolean feedbackSent = model.receivedPrompts().get(1).getInstructions().stream()
                .anyMatch(m -> m instanceof UserMessage um && um.getText().contains("无法解析"));
        assertThat(feedbackSent).isTrue();
    }

    @Test
    void 达到迭代上限返回上限说明_FR007() {
        ScriptedChatModel model = new ScriptedChatModel(
                ScriptedChatModel.toolCallResponse(ScriptedChatModel.toolCall("c1", "echo", "{}")),
                ScriptedChatModel.toolCallResponse(ScriptedChatModel.toolCall("c2", "echo", "{}")),
                ScriptedChatModel.toolCallResponse(ScriptedChatModel.toolCall("c3", "echo", "{}")));
        List<Message> sink = sinkMessages();

        String result = loop.run(model, new ArrayList<>(List.of(new UserMessage("hi"))),
                ToolCallingChatOptions.builder().build(), 3, audit(), null, sink::add);

        assertThat(result).contains("推理步数上限");
        assertThat(model.receivedPrompts()).hasSize(3);
    }

    @Test
    void 模型异常落失败审计后原样上抛_FR016_FR018() {
        ScriptedChatModel model = new ScriptedChatModel(); // 空脚本 → 调用即抛

        assertThatThrownBy(() -> loop.run(model, new ArrayList<>(List.of(new UserMessage("hi"))),
                ToolCallingChatOptions.builder().build(), 10, audit(), null, m -> { }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(auditStore.llmCalls).hasSize(1);
        assertThat(auditStore.llmCalls.get(0).success()).isFalse();
        assertThat(auditStore.llmCalls.get(0).errorMessage()).isNotBlank();
    }
}
