package com.oryxos.tool.builtin;

import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP 内置工具（技术方案 §6.2）：{@code http_get}/{@code http_post}，带域名白名单（调用位）。
 *
 * <p>课件「三」骨架逐字对齐：方法<b>第一行</b>过 {@code Sandbox.enforce(HTTP_REQUEST, url)}--
 * 「域名不在白名单里直接抛异常、请求根本发不出去；过了校验才真正去请求」。本体域名白名单 24 节交付， 当前为 {@code PermissiveSandbox} 临时装配。HTTP
 * 错误响应（4xx/5xx）异常上抛不吞（19 节 webhook 同款口径）， 沿 ToolExecutor 失败审计路径留痕。
 */
@Component
public class HttpTools {

  private final Sandbox sandbox;
  private final RestClient restClient;

  public HttpTools(Sandbox sandbox, RestClient restClient) {
    this.sandbox = sandbox;
    this.restClient = restClient;
  }

  /** 发起 GET 请求，返回响应体（课件「三」骨架逐字对齐）。 */
  @Tool(name = "http_get", description = "发起一个 HTTP GET 请求，返回响应体")
  public String httpGet(@ToolParam(description = "要请求的完整 URL") String url) {
    sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, url));
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("http_get 的 URL 不能为空");
    }
    return restClient.get().uri(url).retrieve().body(String.class);
  }

  /** 发起 POST 请求（body 按 JSON 发送），返回响应体。 */
  @Tool(name = "http_post", description = "发起一个 HTTP POST 请求（body 按 JSON 发送），返回响应体")
  public String httpPost(
      @ToolParam(description = "要请求的完整 URL") String url,
      @ToolParam(description = "要发送的请求体（JSON 文本）") String body) {
    sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, url));
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("http_post 的 URL 不能为空");
    }
    if (body == null || body.isBlank()) {
      throw new IllegalArgumentException("http_post 的 body 不能为空");
    }
    String response =
        restClient
            .post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class);
    return response == null ? "（空响应体）" : response;
  }
}
