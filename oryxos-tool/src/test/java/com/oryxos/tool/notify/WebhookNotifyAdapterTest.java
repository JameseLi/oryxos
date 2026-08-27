package com.oryxos.tool.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link WebhookNotifyAdapter} 验收 harness（课件「四」第一批，本节立即可跑）。
 *
 * <p>用 MockWebServer 在本地起一个假 webhook--不算外网依赖，仍是单测层（课件原话）。 覆盖验收点：发送后断言收到的 POST 请求 body 里带 {@code
 * content}、URL 来自 {@code NotifyTarget.config} 而不是硬编码； webhook 返回 5xx 时异常向上抛、不静默吞掉。
 */
class WebhookNotifyAdapterTest {

  private final MockWebServer server = new MockWebServer();
  private final WebhookNotifyAdapter adapter = new WebhookNotifyAdapter(RestClient.create());

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  @DisplayName("发送后收到的 POST 请求 body 里带 content")
  void sentPostBodyCarriesContent() throws Exception {
    server.start();
    server.enqueue(new MockResponse().setResponseCode(200));
    NotifyTarget target =
        new NotifyTarget("webhook", Map.of("url", server.url("/hook").toString()));

    adapter.send(target, "今日天气：晴，26 度");

    RecordedRequest recorded = server.takeRequest();
    assertThat(recorded.getMethod()).isEqualTo("POST");
    assertThat(JsonPath.<String>read(recorded.getBody().readUtf8(), "$.content"))
        .isEqualTo("今日天气：晴，26 度");
  }

  @Test
  @DisplayName("URL 来自 NotifyTarget.config 而不是硬编码：换 Target 即换地址")
  void requestUrlFollowsTargetConfigNotHardcoded() throws Exception {
    server.start();
    server.enqueue(new MockResponse().setResponseCode(200));
    server.enqueue(new MockResponse().setResponseCode(200));

    // 同一实现、两个不同 path 的 Target：各自送达各自 path，证明地址由 Target 决定
    adapter.send(
        new NotifyTarget("webhook", Map.of("url", server.url("/team-a").toString())), "给 A 群");
    adapter.send(
        new NotifyTarget("webhook", Map.of("url", server.url("/team-b").toString())), "给 B 群");

    assertThat(server.takeRequest().getPath()).isEqualTo("/team-a");
    assertThat(server.takeRequest().getPath()).isEqualTo("/team-b");
  }

  @Test
  @DisplayName("webhook 返回 5xx 时异常向上抛、不静默吞掉")
  void serverErrorPropagatesAsException() throws Exception {
    server.start();
    server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
    NotifyTarget target =
        new NotifyTarget("webhook", Map.of("url", server.url("/hook").toString()));

    // 不捕获不包装：上层（ToolExecutor 统一路径）负责转失败结果并落审计
    assertThatThrownBy(() -> adapter.send(target, "hello"))
        .isInstanceOf(RestClientResponseException.class);
  }

  @Test
  @DisplayName("Target 缺 url 配置时明确报错，不是静默失败")
  void missingUrlConfigFailsFast() throws Exception {
    server.start();

    assertThatThrownBy(() -> adapter.send(new NotifyTarget("webhook", Map.of()), "hello"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("url");
  }
}
