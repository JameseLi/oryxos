package com.oryxos.memory.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.memory.MemoryScope;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link Mem0MemoryStore} 专属 harness（第 22 节）：本地假 Mem0 服务验证协议——不碰真 server。
 *
 * <p>覆盖：append 请求体带 scope/user_id、load 按 scope 分组、recall 转发 search、归档截断最近 N 条、HTTP 错误上抛。
 */
class Mem0MemoryStoreTest {

  private FakeMem0Server server;
  private Mem0MemoryStore store;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws IOException {
    server = new FakeMem0Server();
    server.start();
    store = new Mem0MemoryStore(RestClient.create(), server.url(), "user-22");
  }

  @AfterEach
  void tearDown() throws IOException {
    server.close();
  }

  @Test
  @DisplayName("append 发出的请求体带 content、user_id 与 metadata.scope")
  void appendSendsScopeInMetadata() throws Exception {
    store.append("记住 Java 偏好", MemoryScope.CORE);

    JsonNode body = mapper.readTree(server.lastRequestBody());
    assertThat(server.lastRequestPath()).isEqualTo("/v1/memories/");
    assertThat(body.path("messages").path(0).path("content").asText()).isEqualTo("记住 Java 偏好");
    assertThat(body.path("user_id").asText()).isEqualTo("user-22");
    assertThat(body.path("metadata").path("scope").asText()).isEqualTo("CORE");
  }

  @Test
  @DisplayName("load 按 scope 分组：核心区全量、归档区只取最近 N 条")
  void loadGroupsByScope() {
    server.seed("核心记忆-用户叫小王", "CORE");
    server.seed("归档记忆-旧流水", "ARCHIVAL");

    String loaded = store.load();

    assertThat(loaded).contains("核心记忆-用户叫小王");
    assertThat(loaded).contains("归档记忆-旧流水");
    assertThat(loaded).contains("## 核心记忆");
    assertThat(loaded).contains("## 归档记忆");
  }

  @Test
  @DisplayName("recall 转发到 Mem0 search 端点，不碰核心区")
  void recallForwardsSearchRequest() throws Exception {
    server.seed("Spring Boot 项目", "ARCHIVAL");

    assertThat(store.recallByKeyword("Spring")).contains("Spring Boot 项目");

    JsonNode body = mapper.readTree(server.lastRequestBody());
    assertThat(server.lastRequestPath()).isEqualTo("/v1/memories/search/");
    assertThat(body.path("query").asText()).isEqualTo("Spring");
    assertThat(body.path("user_id").asText()).isEqualTo("user-22");
  }

  @Test
  @DisplayName("归档区超过 100 条只保留最近 100 条，核心区不受影响")
  void archiveTruncatesToRecentHundred() {
    server.seed("核心记忆-常驻", "CORE");
    for (int i = 0; i < 150; i++) {
      server.seed("归档流水 " + i, "ARCHIVAL");
    }

    String loaded = store.load();

    assertThat(loaded).contains("核心记忆-常驻");
    assertThat(loaded).doesNotContain("归档流水 0");
    assertThat(loaded).contains("归档流水 149");
  }

  @Test
  @DisplayName("Mem0 返回错误时异常上抛，不静默吞掉")
  void httpErrorPropagates() {
    server.failNextRequest();

    assertThatThrownBy(() -> store.append("会失败", MemoryScope.ARCHIVAL))
        .isInstanceOf(RestClientException.class);
  }
}
