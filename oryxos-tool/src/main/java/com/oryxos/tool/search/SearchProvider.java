package com.oryxos.tool.search;

import java.util.List;

/**
 * 搜索源抽象（课件 6.4，接口先行）：表达「用某个搜索引擎检索一个查询」的意图。
 *
 * <p>为什么要抽接口：换 Google / Bing / Tavily 只加实现类、不改 {@code web_search} 工具与调用方 （与 19 节 {@code
 * NotifyChannelAdapter}、本节 {@code Sandbox} 同一个「接口先行」套路）。 核心阶段挂一档免 key 实现 {@code
 * DuckDuckGoSearchProvider}（Instant Answer API）。
 */
public interface SearchProvider {

  /** 一条搜索结果：标题 + 链接 + 摘要（课件 6.4 逐字）。 */
  record SearchResult(String title, String url, String snippet) {

    public SearchResult {
      if (title == null) {
        title = "";
      }
      if (url == null) {
        url = "";
      }
      if (snippet == null) {
        snippet = "";
      }
    }
  }

  /**
   * 检索一个查询。
   *
   * @throws RuntimeException 搜索源不可达/返回异常时上抛（异常不吞，走 ToolExecutor 失败审计路径）
   */
  List<SearchResult> search(String query);
}
