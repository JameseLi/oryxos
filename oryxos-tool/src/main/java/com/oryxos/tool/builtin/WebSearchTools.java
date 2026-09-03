package com.oryxos.tool.builtin;

import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import com.oryxos.tool.search.DuckDuckGoSearchProvider;
import com.oryxos.tool.search.SearchProvider;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 搜索内置工具（课件 6.4）：{@code web_search} 用搜索引擎检索网页，日报/调研类 Agent「上网查」的第一入口。
 *
 * <p>硬规矩（课件 6.4）：走网络，所以跟 {@code http_get} 一样，<b>第一件事</b>过 {@code Sandbox} 的 {@code HTTP_REQUEST}
 * 白名单——不能因为它叫「搜索」就绕过治理。校验目标是实际要请求的搜索端点 URL， 由装配时随 {@link SearchProvider} 一起注入（工具本身不感知具体搜索源，换
 * Google/Bing/Tavily 只换 装配处实现与端点、不改本工具）。结果渲染成模型好读的文本（标题/链接/摘要行式）。
 *
 * <p>装配形态：非 {@code @Component}（第三个构造参是端点字符串，Spring 无法解析），由 {@code ToolRegistryConfiguration} 的
 * {@code @Bean} 供给，端点取 {@link DuckDuckGoSearchProvider#ENDPOINT}。
 */
public class WebSearchTools {

  private final Sandbox sandbox;
  private final SearchProvider searchProvider;
  private final String searchEndpoint;

  public WebSearchTools(Sandbox sandbox, SearchProvider searchProvider, String searchEndpoint) {
    this.sandbox = sandbox;
    this.searchProvider = searchProvider;
    this.searchEndpoint = searchEndpoint;
  }

  /** 搜索网页：结果渲染成「标题 / 链接 / 摘要」行式文本；搜索源异常上抛不吞。 */
  @Tool(name = "web_search", description = "用搜索引擎检索网页，返回标题、链接与摘要列表")
  public String webSearch(@ToolParam(description = "搜索查询词") String query) {
    sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, searchEndpoint));
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("web_search 的查询词不能为空");
    }
    List<SearchProvider.SearchResult> results = searchProvider.search(query);
    if (results.isEmpty()) {
      return "（没有找到与「" + query + "」相关的结果）";
    }
    StringBuilder text = new StringBuilder();
    for (SearchProvider.SearchResult result : results) {
      text.append("标题: ").append(result.title()).append('\n');
      text.append("链接: ").append(result.url()).append('\n');
      text.append("摘要: ").append(result.snippet()).append("\n\n");
    }
    return text.toString().stripTrailing();
  }
}
