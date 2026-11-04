package com.oryxos.tool.registry;

import com.oryxos.memory.tool.MemoryTools;
import com.oryxos.tool.builtin.FileTools;
import com.oryxos.tool.builtin.HttpTools;
import com.oryxos.tool.builtin.InteractionTools;
import com.oryxos.tool.builtin.NotifyTools;
import com.oryxos.tool.builtin.ShellTools;
import com.oryxos.tool.builtin.WebSearchTools;
import com.oryxos.tool.interaction.ConsoleUserInteraction;
import com.oryxos.tool.interaction.UserInteraction;
import com.oryxos.tool.sandbox.PermissiveSandbox;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.search.DuckDuckGoSearchProvider;
import com.oryxos.tool.search.SearchProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Tool 体系装配（第 20 节）：统一注册表真实现顶替 18 节 {@code EmptyToolRegistry} 过渡 Bean （18 节
 * javadoc「届时本类删除此内嵌类」的兑现）；Sandbox/搜索源/交互入口按「接口先行、装配挂实现」 各挂一档（24 节换 {@code
 * WhitelistSandbox}、扩展阶段加搜索源/渠道，调用方零改动）。
 *
 * <p>注册动作（课件 6.5「注册：在装配处多 registerAnnotated 两行」）：内置工具 Bean 显式注入、逐个 {@code registerAnnotated}——19 节
 * {@code NotifyTools} 在此完成收编注册（19 节 Resolve R1 预告兑现），22 节 {@code MemoryTools}（{@code
 * save_memory}/{@code recall_memory}）在此追加注册（20 节 spec「22 节交付后注册」兑现）。 MCP 工具由 {@code
 * McpClientService} 的 {@code @PostConstruct connectAll} 在注册表就绪后追加注册。
 */
@Configuration
public class ToolRegistryConfiguration {

  @Bean
  public DefaultToolRegistry toolRegistry(
      FileTools fileTools,
      ShellTools shellTools,
      HttpTools httpTools,
      InteractionTools interactionTools,
      WebSearchTools webSearchTools,
      NotifyTools notifyTools,
      MemoryTools memoryTools) {
    DefaultToolRegistry registry = new DefaultToolRegistry();
    // 课件 6.5：装配处 registerAnnotated 两行——12 个内置工具 + 22 节 MemoryTools 两个
    registry.registerAnnotated(
        fileTools,
        shellTools,
        httpTools,
        interactionTools,
        webSearchTools,
        notifyTools,
        memoryTools);
    return registry;
  }

  /** 核心阶段临时装配：默认放行；24 节由 WhitelistSandbox 替换本 Bean、调用方零改动。 */
  @Bean
  public Sandbox sandbox() {
    return new PermissiveSandbox();
  }

  /** CLI 渠道的交互实现（读终端一行）；无人值守场景装配 UnsupportedUserInteraction（22/25 节按需调整）。 */
  @Bean
  public UserInteraction userInteraction() {
    return new ConsoleUserInteraction();
  }

  /** 免 key 搜索源一档（课件 6.4）；换 Google/Bing/Tavily 只换本 Bean 与 webSearchTools 的端点参数。 */
  @Bean
  public SearchProvider searchProvider(RestClient restClient) {
    return new DuckDuckGoSearchProvider(restClient);
  }

  /** web_search 工具：端点与搜索源在装配处成对注入（工具本身不感知具体搜索源，课件 6.4）。 */
  @Bean
  public WebSearchTools webSearchTools(Sandbox sandbox, SearchProvider searchProvider) {
    return new WebSearchTools(sandbox, searchProvider, DuckDuckGoSearchProvider.ENDPOINT);
  }
}
