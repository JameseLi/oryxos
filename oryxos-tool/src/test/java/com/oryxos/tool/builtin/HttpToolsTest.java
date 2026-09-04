package com.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.sandbox.PermissiveSandbox;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxViolationException;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link HttpTools} 验收 harness（课件正文 http_get 两个用例即模板，断言逻辑逐条保真）： 正常能跑通取回响应体 + 命中白名单外域名被拦下 + POST
 * 正常发送。
 *
 * <p>时间裁定：「越界会被拦」以调用位语义承载（拒绝型 Sandbox 首行抛 {@link SandboxViolationException}、 请求根本发不出去）；真实域名白名单匹配与
 * InOrder 顺序断言归 24 节 {@code WhitelistSandbox}。 MockWebServer 为本地回环假 HTTP（19 节先例，不算外网依赖）。
 */
class HttpToolsTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private MockWebServer server;
  private HttpTools tools;
  private HttpTools deniedTools;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    tools = new HttpTools(new PermissiveSandbox(), RestClient.create());
    deniedTools = new HttpTools(denying(), RestClient.create());
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  @DisplayName("http_get 应能取回响应")
  void httpGetReturnsResponseBody() throws Exception {
    server.enqueue(new MockResponse().setBody("beijing sunny 25C"));
    OryxTool view = view(tools, "http_get");

    ToolResult result = view.execute(json(Map.url(server.url("/beijing").toString())));

    assertThat(result.success()).isTrue();
    assertThat(result.output()).isEqualTo("beijing sunny 25C");
  }

  @Test
  @DisplayName("http_get 命中白名单外域名应被拦下")
  void httpRequestOutsideWhitelistIsRejected() throws Exception {
    OryxTool view = view(deniedTools, "http_get");

    assertThatThrownBy(() -> view.execute(json(Map.url("https://evil.example.com/steal"))))
        .isInstanceOf(SandboxViolationException.class);
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  @DisplayName("http_post 正常发送：body 原样到达、返回响应体")
  void httpPostSendsBodyAndReturnsResponse() throws Exception {
    server.enqueue(new MockResponse().setBody("{\"ok\":true}"));
    OryxTool view = view(tools, "http_post");

    ToolResult result =
        view.execute(
            JSON.writeValueAsString(
                java.util.Map.of(
                    "url", server.url("/push").toString(), "body", "{\"msg\":\"hi\"}")));

    assertThat(result.success()).isTrue();
    assertThat(result.output()).isEqualTo("{\"ok\":true}");
    String received = server.takeRequest().getBody().readUtf8();
    assertThat(received).isEqualTo("{\"msg\":\"hi\"}");
  }

  @Test
  @DisplayName("HTTP 4xx/5xx 异常上抛不吞（19 节 webhook 同款口径，走失败审计路径）")
  void httpErrorResponsesPropagate() {
    server.enqueue(new MockResponse().setResponseCode(500));
    OryxTool view = view(tools, "http_get");

    assertThatThrownBy(() -> view.execute(json(Map.url(server.url("/boom").toString()))))
        .isInstanceOf(RestClientResponseException.class);
  }

  private static Sandbox denying() {
    return action -> {
      throw new SandboxViolationException("动作被白名单拒绝: " + action.target());
    };
  }

  private static String json(java.util.Map<String, String> args) throws Exception {
    return JSON.writeValueAsString(args);
  }

  private static OryxTool view(Object toolBean, String toolName) {
    for (com.oryxos.tool.registry.AnnotatedToolAdapter adapter :
        com.oryxos.tool.registry.AnnotatedToolAdapter.forToolBean(toolBean)) {
      if (adapter.name().equals(toolName)) {
        return adapter;
      }
    }
    throw new IllegalStateException("工具 Bean 中找不到 @Tool 方法: " + toolName);
  }

  /** 课件 harness json(...) 助手的可读替身。 */
  private static final class Map {
    private static java.util.Map<String, String> url(String url) {
      return java.util.Map.of("url", url);
    }
  }
}
