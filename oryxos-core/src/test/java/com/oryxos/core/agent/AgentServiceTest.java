package com.oryxos.core.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.oryxos.core.audit.AuditRecorder;
import com.oryxos.core.react.PromptBuilder;
import com.oryxos.core.react.ReActLoop;
import com.oryxos.core.session.SessionManager;
import com.oryxos.core.support.FakeToolCatalog;
import com.oryxos.core.support.InMemorySessionStore;
import com.oryxos.core.support.RecordingAuditStore;
import com.oryxos.core.support.ScriptedChatModel;
import com.oryxos.core.tool.ToolExecutor;
import com.oryxos.core.workspace.WorkspaceLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AgentService 单测：校验（FR-003）、主流程、工具流、超时（FR-010）。 */
class AgentServiceTest {

    @TempDir
    Path workspaceRoot;

    private InMemorySessionStore sessionStore;
    private RecordingAuditStore auditStore;
    private FakeToolCatalog catalog;
    private ScriptedChatModel scriptedModel;
    private AgentService service;

    @BeforeEach
    void setUp() throws IOException {
        Path agentDir = workspaceRoot.resolve("agents").resolve("weather");
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("AGENT.md"), """
                ---
                name: weather
                provider: { name: stub, model: stub-model }
                tools: [echo]
                ---
                你是测试助手。
                """);
        Files.writeString(workspaceRoot.resolve("SOUL.md"), "稳重可靠。");

        sessionStore = new InMemorySessionStore();
        auditStore = new RecordingAuditStore();
        catalog = new FakeToolCatalog().register(FakeToolCatalog.echoTool("echo"));
        AuditRecorder recorder = new AuditRecorder(auditStore);
        ToolExecutor executor = new ToolExecutor(catalog, recorder);
        ChatModelResolver resolver = new ChatModelResolver() {
            @Override
            public ChatModel resolve(String providerName) {
                return scriptedModel;
            }

            @Override
            public String modelOf(String providerName) {
                return "stub-model";
            }
        };
        service = new AgentService(
                new AgentLoader(WorkspaceLayout.of(workspaceRoot), catalog),
                new ContextLoader(WorkspaceLayout.of(workspaceRoot)),
                new SessionManager(sessionStore),
                new PromptBuilder(),
                new ReActLoop(executor, recorder),
                resolver, catalog,
                10, 1); // 测试超时设为 1 秒
    }

    @Test
    void 空消息拒绝_FR003() {
        assertThatThrownBy(() -> service.process("weather", null, "   ", "cli", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void 超过32KB拒绝_FR003() {
        String huge = "x".repeat(33 * 1024);
        assertThatThrownBy(() -> service.process("weather", null, huge, "cli", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32KB");
    }

    @Test
    void 正常问答并持久化会话() {
        scriptedModel = new ScriptedChatModel(ScriptedChatModel.textResponse("你好，世界"));

        AgentService.AgentReply reply = service.process("weather", null, "你好", "cli", null);

        assertThat(reply.text()).isEqualTo("你好，世界");
        assertThat(sessionStore.sessions).hasSize(1);
        assertThat(sessionStore.sessions.get(0).agentName()).isEqualTo("weather");
        assertThat(sessionStore.loadMessages(reply.sessionId())).hasSize(2); // user + assistant
        assertThat(auditStore.llmCalls).hasSize(1);
        assertThat(auditStore.llmCalls.get(0).providerName()).isEqualTo("stub");
    }

    @Test
    void 续聊复用同一会话历史() {
        scriptedModel = new ScriptedChatModel(
                ScriptedChatModel.textResponse("第一轮"),
                ScriptedChatModel.textResponse("第二轮"));

        AgentService.AgentReply first = service.process("weather", null, "q1", "cli", null);
        AgentService.AgentReply second = service.process("weather", first.sessionId(), "q2", "cli", null);

        assertThat(second.sessionId()).isEqualTo(first.sessionId());
        assertThat(second.text()).isEqualTo("第二轮");
        // 第二轮请求应携带完整历史（user,assistant,user）
        assertThat(scriptedModel.receivedPrompts().get(1).getInstructions()).hasSizeGreaterThanOrEqualTo(4); // system + 3
    }

    @Test
    void 工具调用闭环() {
        scriptedModel = new ScriptedChatModel(
                ScriptedChatModel.toolCallResponse(ScriptedChatModel.toolCall("c1", "echo", "{\"text\":\"w\"}")),
                ScriptedChatModel.textResponse("工具说：w"));

        AgentService.AgentReply reply = service.process("weather", null, "查一下", "cli", null);

        assertThat(reply.text()).isEqualTo("工具说：w");
        assertThat(auditStore.toolInvocations).hasSize(1);
        assertThat(auditStore.llmCalls).hasSize(2);
        // 会话中落有 assistant(toolCalls) + tool + assistant 三类消息
        assertThat(sessionStore.loadMessages(reply.sessionId()))
                .extracting(m -> m.role())
                .contains("assistant", "tool");
    }

    @Test
    void 超时返回兜底说明且会话保留_FR010() {
        scriptedModel = new ScriptedChatModel(ScriptedChatModel.textResponse("太慢")).sleep(3000);

        AgentService.AgentReply reply = service.process("weather", null, "你好", "cli", null);

        assertThat(reply.text()).contains("超过 1 秒上限");
        assertThat(sessionStore.sessions).hasSize(1); // 会话已创建且保留
    }
}
