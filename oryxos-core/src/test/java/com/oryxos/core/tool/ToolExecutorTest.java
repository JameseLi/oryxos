package com.oryxos.core.tool;

import java.util.ArrayList;
import java.util.List;

import com.oryxos.core.audit.AuditRecorder;
import com.oryxos.core.support.FakeToolCatalog;
import com.oryxos.core.support.RecordingAuditStore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import static org.assertj.core.api.Assertions.assertThat;

/** ToolExecutor 单测：成功派发、未知工具、异常捕获、进度事件、审计（FR-008/011/017）。 */
class ToolExecutorTest {

    private final RecordingAuditStore auditStore = new RecordingAuditStore();
    private final FakeToolCatalog catalog = new FakeToolCatalog()
            .register(FakeToolCatalog.echoTool("echo"))
            .register(FakeToolCatalog.failingTool("boom_tool"));
    private final ToolExecutor executor = new ToolExecutor(catalog, new AuditRecorder(auditStore));

    private static AssistantMessage.ToolCall call(String name) {
        return new AssistantMessage.ToolCall("id-1", "function", name, "{\"text\":\"hi\"}");
    }

    @Test
    void 成功执行返回结果文本并落审计() {
        List<String> events = new ArrayList<>();
        ToolProgressListener listener = new ToolProgressListener() {
            @Override
            public void onToolStart(String toolName, String argumentsJson) {
                events.add("start:" + toolName);
            }

            @Override
            public void onToolEnd(String toolName, boolean success, long durationMs, String errorMessage) {
                events.add("end:" + toolName + ":" + success);
            }
        };

        ToolResponseMessage.ToolResponse response = executor.execute("s1", call("echo"), listener);

        assertThat(response.responseData()).startsWith("echo:");
        assertThat(events).containsExactly("start:echo", "end:echo:true");
        assertThat(auditStore.toolInvocations).hasSize(1);
        assertThat(auditStore.toolInvocations.get(0).success()).isTrue();
    }

    @Test
    void 未知工具返回错误结果不抛出_FR008() {
        ToolResponseMessage.ToolResponse response = executor.execute("s1", call("ghost"), null);

        assertThat(response.responseData()).startsWith("ERROR:");
        assertThat(auditStore.toolInvocations.get(0).success()).isFalse();
        assertThat(auditStore.toolInvocations.get(0).errorMessage()).contains("未知工具");
    }

    @Test
    void 工具抛异常被捕获转为失败结果_FR008() {
        ToolResponseMessage.ToolResponse response = executor.execute("s1", call("boom_tool"), null);

        assertThat(response.responseData()).isEqualTo("ERROR: boom");
        assertThat(auditStore.toolInvocations.get(0).success()).isFalse();
    }
}
