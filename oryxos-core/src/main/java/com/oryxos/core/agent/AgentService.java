package com.oryxos.core.agent;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.oryxos.core.react.PromptBuilder;
import com.oryxos.core.react.ReActLoop;
import com.oryxos.core.session.SessionManager;
import com.oryxos.core.session.StoredSession;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolCatalog;
import com.oryxos.core.tool.ToolProgressListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Agent 服务：三个触发源（CLI / Web / Scheduler）的统一入口（本特性仅 CLI 接入）。
 *
 * <p>职责：入参校验（FR-003）→ 装载 Agent → 解析 Provider → 会话存取 →
 * 组装 Prompt → virtual thread 内跑 ReActLoop（60s 整体超时，FR-010）→ 返回最终回答。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    /** 单条消息大小上限（字符，FR-003 / 宪法工程约束 32KB）。 */
    static final int MAX_MESSAGE_CHARS = 32 * 1024;

    private final AgentLoader agentLoader;
    private final ContextLoader contextLoader;
    private final SessionManager sessionManager;
    private final PromptBuilder promptBuilder;
    private final ReActLoop reActLoop;
    private final ChatModelResolver chatModelResolver;
    private final ToolCatalog toolCatalog;
    private final int maxIterations;
    private final int invocationTimeoutSeconds;
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public AgentService(AgentLoader agentLoader,
                        ContextLoader contextLoader,
                        SessionManager sessionManager,
                        PromptBuilder promptBuilder,
                        ReActLoop reActLoop,
                        ChatModelResolver chatModelResolver,
                        ToolCatalog toolCatalog,
                        @Value("${oryxos.react.max-iterations:10}") int maxIterations,
                        @Value("${oryxos.react.invocation-timeout-seconds:60}") int invocationTimeoutSeconds) {
        this.agentLoader = agentLoader;
        this.contextLoader = contextLoader;
        this.sessionManager = sessionManager;
        this.promptBuilder = promptBuilder;
        this.reActLoop = reActLoop;
        this.chatModelResolver = chatModelResolver;
        this.toolCatalog = toolCatalog;
        this.maxIterations = maxIterations;
        this.invocationTimeoutSeconds = invocationTimeoutSeconds;
    }

    /**
     * 启动预检（FR-013）：对话开始前完成 Agent 装载与 Provider 解析，
     * 密钥缺失/配置错误/Agent 不存在在此 fail-fast，而不是首轮调用才报错。
     */
    public void preflight(String agentName) {
        AgentDefinition agent = agentLoader.load(agentName);
        chatModelResolver.resolve(agent.providerName());
    }

    /**
     * 处理一轮用户消息，返回最终回答。
     *
     * @param agentName  Agent 名（.oryxos/agents/&lt;name&gt;）
     * @param sessionId  会话 id（null 新建）
     * @param userMessage 用户输入
     * @param channel    渠道标识（cli / scheduler / web）
     * @param listener   工具进度监听（可空）
     * @return 处理结果（回答文本 + 会话 id）
     */
    public AgentReply process(String agentName, String sessionId, String userMessage,
                              String channel, ToolProgressListener listener) {
        validateMessage(userMessage);
        // 装载与启动校验（Agent 不存在/provider 未配置/密钥缺失都在此 fail-fast，FR-013）
        AgentDefinition agent = agentLoader.load(agentName);
        ChatModel chatModel = chatModelResolver.resolve(agent.providerName());
        String modelName = chatModelResolver.modelOf(agent.providerName());

        StoredSession session = sessionManager.getOrCreateSession(sessionId, agent.name(), channel, "local");
        UserMessage userMsg = new UserMessage(userMessage);
        sessionManager.append(session.id(), userMsg);

        List<Message> history = sessionManager.history(session.id());        String systemPrompt = contextLoader.buildSystemPrompt(agent);
        List<Message> messages = promptBuilder.build(systemPrompt, history);
        List<OryxTool> tools = toolCatalog.findAll(agent.tools());
        ToolCallingChatOptions options = promptBuilder.toolOptions(tools);
        int iterationCap = agent.effectiveMaxIterations(maxIterations);

        Future<String> future = virtualThreadExecutor.submit(() -> reActLoop.run(
                chatModel, messages, options, iterationCap,
                new ReActLoop.LlmAuditContext(session.id(), agent.providerName(), modelName),
                listener,
                message -> sessionManager.append(session.id(), message)));
        try {
            String reply = future.get(invocationTimeoutSeconds, TimeUnit.SECONDS);
            return new AgentReply(session.id(), reply);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Agent 调用超时（{}s）: session={} agent={}", invocationTimeoutSeconds, session.id(), agent.name());
            return new AgentReply(session.id(),
                    "（本次处理超过 " + invocationTimeoutSeconds + " 秒上限已终止；会话保留，可继续提问）");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentInvocationException("调用被中断", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("Agent 调用失败: session={} cause={}", session.id(), cause.toString());
            throw new AgentInvocationException("模型调用失败: " + cause.getMessage(), cause);
        }
    }

    private void validateMessage(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("消息不能为空");
        }
        if (userMessage.length() > MAX_MESSAGE_CHARS) {
            throw new IllegalArgumentException(
                    "单条消息超过 32KB 上限（当前 " + userMessage.length() + " 字符），请精简后重发");
        }
    }

    /** 一轮处理的结果。 */
    public record AgentReply(String sessionId, String text) {
    }

    /** 模型/执行侧失败（provider 不可用、密钥无效等），会话本身保持可用。 */
    public static class AgentInvocationException extends RuntimeException {
        public AgentInvocationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
