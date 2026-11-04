package com.oryxos.memory.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.oryxos.core.memory.MemoryScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Mem0 长期记忆后端（第 22 节外部集成档，技术方案 §5.1）。
 *
 * <p>接一个<b>自托管</b> Mem0 server（数据不出域），Java 侧只做协议转换：{@link #append} 翻译成 add（scope 落进
 * metadata）、{@link #load} 翻译成按 user 取回并按 scope 分组、{@link #recallByKeyword} 翻译成 search（语义检索，
 * 契约四的加强版）。提炼、冲突消解、语义检索都交给 Mem0 自己管。
 *
 * <p>地址/凭证走环境变量（{@code MEM0_BASE_URL} / {@code MEM0_USER_ID} / {@code MEM0_API_KEY}），不落明文。外部服务是
 * 可替换装配件，不改变上层任何代码；HTTP 异常原样上抛，由调用方审计，不静默吞掉。
 */
public class Mem0MemoryStore implements LongTermMemoryStore {

  static final int MAX_ARCHIVE_ROWS = 100;

  private static final String CORE_HEADER = "## 核心记忆";
  private static final String ARCHIVE_HEADER = "## 归档记忆";
  private static final String SLASH = "/";

  private final RestClient restClient;
  private final String baseUrl;
  private final String userId;
  private final String apiKey;

  public Mem0MemoryStore(RestClient restClient, String baseUrl, String userId) {
    this(restClient, baseUrl, userId, null);
  }

  public Mem0MemoryStore(RestClient restClient, String baseUrl, String userId, String apiKey) {
    this.restClient = restClient;
    this.baseUrl = trimTrailingSlash(baseUrl);
    this.userId = userId;
    this.apiKey = apiKey;
  }

  @Override
  public void append(String content, MemoryScope scope) {
    MemoryScope effective = scope == null ? MemoryScope.ARCHIVAL : scope;
    Map<String, Object> message = Map.of("role", "user", "content", content);
    Map<String, Object> body =
        Map.of(
            "messages", List.of(message),
            "user_id", userId,
            "metadata", Map.of("scope", effective.name()));
    RestClient.RequestBodySpec request =
        restClient.post().uri(baseUrl + "/v1/memories/").contentType(MediaType.APPLICATION_JSON);
    if (hasApiKey()) {
      request = request.header("Authorization", "Bearer " + apiKey);
    }
    request.body(body).retrieve().toBodilessEntity();
  }

  @Override
  public String load() {
    RestClient.RequestHeadersSpec<?> request =
        restClient.get().uri(baseUrl + "/v1/memories/?user_id={userId}", userId);
    if (hasApiKey()) {
      request = request.header("Authorization", "Bearer " + apiKey);
    }
    JsonNode response = request.retrieve().body(JsonNode.class);
    List<JsonNode> core = new ArrayList<>();
    List<JsonNode> archive = new ArrayList<>();
    for (JsonNode item : results(response)) {
      String scope = normalizeScope(item.path("metadata").path("scope").asText(""));
      if (MemoryScope.CORE.name().equals(scope)) {
        core.add(item);
      } else {
        archive.add(item);
      }
    }
    if (archive.size() > MAX_ARCHIVE_ROWS) {
      archive = new ArrayList<>(archive.subList(archive.size() - MAX_ARCHIVE_ROWS, archive.size()));
    }
    if (core.isEmpty() && archive.isEmpty()) {
      return "";
    }
    StringBuilder text = new StringBuilder(CORE_HEADER).append('\n');
    for (JsonNode item : core) {
      text.append("- ").append(textOf(item)).append('\n');
    }
    text.append(ARCHIVE_HEADER).append('\n');
    for (JsonNode item : archive) {
      text.append("- ").append(textOf(item)).append('\n');
    }
    return text.toString();
  }

  @Override
  public List<String> recallByKeyword(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return List.of();
    }
    Map<String, Object> body = Map.of("query", keyword, "user_id", userId);
    RestClient.RequestBodySpec request =
        restClient
            .post()
            .uri(baseUrl + "/v1/memories/search/")
            .contentType(MediaType.APPLICATION_JSON);
    if (hasApiKey()) {
      request = request.header("Authorization", "Bearer " + apiKey);
    }
    JsonNode response = request.body(body).retrieve().body(JsonNode.class);
    List<String> hits = new ArrayList<>();
    for (JsonNode item : results(response)) {
      // 契约四：recall 只搜归档区——即使 Mem0 的语义检索把核心区也召回了，这里也只返回归档条目。
      String scope = normalizeScope(item.path("metadata").path("scope").asText(""));
      if (!MemoryScope.CORE.name().equals(scope)) {
        hits.add(textOf(item));
      }
    }
    return hits;
  }

  private static String normalizeScope(String scope) {
    String trimmed = scope == null ? "" : scope.trim();
    return trimmed.isEmpty() ? MemoryScope.ARCHIVAL.name() : trimmed.toUpperCase(Locale.ROOT);
  }

  private boolean hasApiKey() {
    return apiKey != null && !apiKey.isBlank();
  }

  private static List<JsonNode> results(JsonNode response) {
    if (response == null) {
      return List.of();
    }
    JsonNode array = response.isArray() ? response : response.path("results");
    if (!array.isArray()) {
      return List.of();
    }
    List<JsonNode> items = new ArrayList<>();
    array.forEach(items::add);
    return items;
  }

  private static String textOf(JsonNode item) {
    String memory = item.path("memory").asText("");
    if (!memory.isBlank()) {
      return memory;
    }
    return item.path("content").asText("");
  }

  private static String trimTrailingSlash(String url) {
    String trimmed = url == null ? "" : url.trim();
    while (trimmed.endsWith(SLASH)) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }
}
