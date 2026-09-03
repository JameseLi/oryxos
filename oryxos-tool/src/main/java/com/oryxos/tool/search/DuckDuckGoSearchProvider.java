package com.oryxos.tool.search;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.client.RestClient;

/**
 * 免 key 实现（课件 6.4）：DuckDuckGo Instant Answer API（{@code api.duckduckgo.com}）。
 *
 * <p>换 Google / Bing / Tavily 只加实现类、不改 {@code web_search} 工具与调用方（课件 6.4）。 Instant Answer API
 * 是免凭证端点：凭证走环境变量注入的宪法要求在这里天然满足（无 key 可泄）。 网络异常上抛不吞（走 ToolExecutor 失败审计路径）；真网调用属人工项，单测 mock 本接口。
 */
public class DuckDuckGoSearchProvider implements SearchProvider {

  /** 搜索端点根（web_search 工具装配时把域名白名单校验目标指到这里，课件 6.4）。 */
  public static final String ENDPOINT = "https://api.duckduckgo.com";

  private static final String INSTANT_ANSWER_URL = ENDPOINT + "/?q=%s&format=json&no_html=1";

  /** Instant Answer API 相关条目字段名（避免魔法值）。 */
  private static final String RELATED_TOPICS_FIELD = "Topics";

  private final RestClient restClient;

  public DuckDuckGoSearchProvider(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public List<SearchResult> search(String query) {
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("web_search 查询词不能为空");
    }
    String url =
        String.format(INSTANT_ANSWER_URL, URLEncoder.encode(query, StandardCharsets.UTF_8));
    JsonNode root = restClient.get().uri(url).retrieve().body(JsonNode.class);
    List<SearchResult> results = new ArrayList<>();
    if (root == null) {
      return results;
    }
    // Instant Answer 形态：AbstractText 为主摘要，RelatedTopics 数组为相关条目（Text 里常带「标题 - 摘要」）
    collectAbstract(root, results);
    collectRelatedTopics(root, results);
    return results;
  }

  private void collectAbstract(JsonNode root, List<SearchResult> results) {
    String text = root.path("AbstractText").asText("");
    if (!text.isBlank()) {
      String source = root.path("AbstractURL").asText("");
      results.add(new SearchResult(root.path("Heading").asText(queryOrEmpty()), source, text));
    }
  }

  private String queryOrEmpty() {
    return "";
  }

  private void collectRelatedTopics(JsonNode root, List<SearchResult> results) {
    JsonNode topics = root.path("RelatedTopics");
    if (!topics.isArray()) {
      return;
    }
    for (JsonNode topic : topics) {
      // FirstURL + Text 是标准条目形态；嵌套 Topics 的条目展平读取
      if (topic.has(RELATED_TOPICS_FIELD)) {
        for (JsonNode nested : topic.path(RELATED_TOPICS_FIELD)) {
          addTopic(nested, results);
        }
      } else {
        addTopic(topic, results);
      }
    }
  }

  private void addTopic(JsonNode topic, List<SearchResult> results) {
    String text = topic.path("Text").asText("");
    if (text.isBlank()) {
      return;
    }
    int splitAt = text.indexOf(" - ");
    String title = splitAt > 0 ? text.substring(0, splitAt) : text;
    String snippet = splitAt > 0 ? text.substring(splitAt + 3) : text;
    results.add(new SearchResult(title, topic.path("FirstURL").asText(""), snippet));
  }
}
