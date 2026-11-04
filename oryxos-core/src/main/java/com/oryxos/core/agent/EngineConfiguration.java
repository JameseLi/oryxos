package com.oryxos.core.agent;

import com.oryxos.core.audit.ToolInvocationAudit;
import com.oryxos.core.chat.ChatInvoker;
import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.react.PromptBuilder;
import com.oryxos.core.react.ReActLoop;
import com.oryxos.core.session.SessionManager;
import com.oryxos.core.tool.ToolExecutor;
import com.oryxos.core.tool.ToolRegistry;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 引擎装配（第 18 节）：把第 17 节交付的纯 POJO 引擎链接成 Spring Bean，供 CLI（本节）与 Web/定时（后续节）取用-- 17 节 spec R5 预告的「装配留到
 * 18/20/22 节接线」，本节是 CLI 的接线点。
 *
 * <p>依赖一律按 core 立的端口接口注入（DIP）：{@link ChatInvoker} 由 16 节 {@code ProviderService}（provider 模块）提供、
 * {@link ToolInvocationAudit}/{@link SessionManager} 由 storage 模块的 JPA 实现提供--core 不感知实现模块。 17 节
 * POJO 本身零改动（不加注解，单测形态保留）。
 *
 * <p>{@link MemoryService} 由 oryxos-memory 模块的 {@code MemoryConfiguration} 提供（第 22 节：17 节 {@code
 * MemoryService=null} 过渡的兑现）；{@link ToolRegistry} 由 oryxos-tool 模块的 {@code
 * ToolRegistryConfiguration} 提供真实现（18 节的空注册表过渡 Bean 已按 20 节预告删除）。
 */
@Configuration
public class EngineConfiguration {

  /** 上下文供给者：Bootstrap/AGENT 正文每次组装重读不缓存（17 节铁律）。 */
  @Bean
  public ContextLoader contextLoader(
      @Value("${oryxos.workspace-dir:.oryxos}") String workspaceDir) {
    return new ContextLoader(Path.of(workspaceDir));
  }

  @Bean
  public PromptBuilder promptBuilder(
      ContextLoader contextLoader, ToolRegistry toolRegistry, MemoryService memoryService) {
    return new PromptBuilder(contextLoader, toolRegistry, memoryService);
  }

  @Bean
  public ToolExecutor toolExecutor(
      ToolRegistry toolRegistry, ToolInvocationAudit toolInvocationAudit) {
    return new ToolExecutor(toolRegistry, toolInvocationAudit);
  }

  @Bean
  public ReActLoop reActLoop(
      PromptBuilder promptBuilder, ChatInvoker chatInvoker, ToolExecutor toolExecutor) {
    return new ReActLoop(promptBuilder, chatInvoker, toolExecutor);
  }

  /** 三源归一的统一入口：CLI/Web/定时最终都调 AgentService.process。 */
  @Bean
  public AgentService agentService(
      ProfileRegistry profileRegistry, ReActLoop reActLoop, SessionManager sessionManager) {
    return new AgentService(profileRegistry, reActLoop, sessionManager);
  }
}
