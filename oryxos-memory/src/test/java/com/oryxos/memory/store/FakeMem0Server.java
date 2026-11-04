package com.oryxos.memory.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * 本地假 Mem0 服务（第 22 节 test-only helper）：内存 Map/List 实现 add / get_all / search 三个端点， 供契约测试与 {@link
 * Mem0MemoryStoreTest} 使用——不碰真 server、不走外网。
 */
final class FakeMem0Server implements AutoCloseable {

  private final MockWebServer server = new MockWebServer();
  private final ObjectMapper mapper = new ObjectMapper();
  private final List<MemoryItem> memories = new ArrayList<>();
  private final AtomicLong ids = new AtomicLong();
  private volatile boolean failNext;
  private volatile String lastRequestPath;
  private volatile String lastRequestBody;

  void start() throws IOException {
    server.setDispatcher(
        new Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) {
            lastRequestPath = request.getPath();
            if (failNext) {
              failNext = false;
              return new MockResponse().setResponseCode(500);
            }
            String path = request.getPath();
            String method = request.getMethod();
            try {
              if ("POST".equals(method) && path.startsWith("/v1/memories/search/")) {
                return handleSearch(request);
              }
              if ("GET".equals(method) && path.startsWith("/v1/memories/")) {
                return json(results(memories));
              }
              if ("POST".equals(method) && path.startsWith("/v1/memories/")) {
                return handleAdd(request);
              }
            } catch (IOException e) {
              return new MockResponse().setResponseCode(500).setBody("bad json");
            }
            return new MockResponse().setResponseCode(404);
          }
        });
    server.start();
  }

  String url() {
    String value = server.url("/").toString();
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  void seed(String content, String scope) {
    memories.add(new MemoryItem(ids.incrementAndGet(), content, scope));
  }

  void reset() {
    memories.clear();
    ids.set(0);
    failNext = false;
    lastRequestPath = null;
    lastRequestBody = null;
    drainRequests();
  }

  void failNextRequest() {
    failNext = true;
  }

  /** 最近一次请求的路径（MockWebServer 的 RecordedRequest body 只能读一次，测试侧改读这里）。 */
  String lastRequestPath() {
    return lastRequestPath;
  }

  /** 最近一次 POST 的请求体原文。 */
  String lastRequestBody() {
    return lastRequestBody;
  }

  private MockResponse handleAdd(RecordedRequest request) throws IOException {
    lastRequestBody = request.getBody().readUtf8();
    JsonNode body = mapper.readTree(lastRequestBody);
    String content = body.path("messages").path(0).path("content").asText("");
    String scope = body.path("metadata").path("scope").asText("ARCHIVAL");
    memories.add(new MemoryItem(ids.incrementAndGet(), content, scope));
    ObjectNode response = mapper.createObjectNode();
    response.put("message", "ok");
    response.put("id", ids.get());
    return json(response);
  }

  private MockResponse handleSearch(RecordedRequest request) throws IOException {
    lastRequestBody = request.getBody().readUtf8();
    JsonNode body = mapper.readTree(lastRequestBody);
    String query = body.path("query").asText("");
    List<MemoryItem> hits =
        memories.stream().filter(item -> item.content().contains(query)).toList();
    return json(results(hits));
  }

  private ObjectNode results(List<MemoryItem> items) {
    ObjectNode response = mapper.createObjectNode();
    ArrayNode array = response.putArray("results");
    for (MemoryItem item : items) {
      ObjectNode node = array.addObject();
      node.put("id", item.id());
      node.put("memory", item.content());
      node.putObject("metadata").put("scope", item.scope());
    }
    return response;
  }

  private MockResponse json(JsonNode body) {
    return new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body.toString());
  }

  private void drainRequests() {
    while (true) {
      try {
        if (server.takeRequest(0, TimeUnit.MILLISECONDS) == null) {
          return;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  @Override
  public void close() throws IOException {
    server.shutdown();
  }

  private record MemoryItem(long id, String content, String scope) {}
}
