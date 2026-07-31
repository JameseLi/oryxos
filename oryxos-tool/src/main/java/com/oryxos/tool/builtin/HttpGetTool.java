package com.oryxos.tool.builtin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.sandbox.Sandbox;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 内置工具 http_get（本特性的最小验收工具）：出站 GET 请求，受沙箱域名白名单约束。
 *
 * <p>注意（诚实标注的信任边界）：本工具自身受白名单约束；但 Agent 目录里的脚本 经 shell 子进程发网络请求会绕过本白名单——装带脚本的 Agent = 信任其作者 （shell
 * 工具属后续 Tool 体系特性）。
 */
@Component
public class HttpGetTool implements OryxTool {

  private static final Logger log = LoggerFactory.getLogger(HttpGetTool.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 响应体截断上限（字符），防止大响应撑爆上下文与审计库。 */
  private static final int MAX_BODY_CHARS = 50 * 1024;

  private final Sandbox sandbox;
  private final HttpClient httpClient;

  public HttpGetTool(
      Sandbox sandbox, @Value("${oryxos.tools.http-timeout-seconds:10}") int timeoutSeconds) {
    this.sandbox = sandbox;
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(timeoutSeconds))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
  }

  @Override
  public String name() {
    return "http_get";
  }

  @Override
  public String description() {
    return "向指定 URL 发起 HTTP GET 请求并返回响应文本（仅限白名单域名）。" + "适用于查询天气、汇率等公开 HTTP 接口。";
  }

  @Override
  public String inputSchema() {
    return """
                {
                  "type": "object",
                  "properties": {
                    "url": {
                      "type": "string",
                      "description": "要请求的完整 URL（含查询参数），域名必须在白名单内"
                    }
                  },
                  "required": ["url"]
                }
                """;
  }

  @Override
  public ToolResult execute(String argumentsJson) {
    String url;
    try {
      JsonNode args = MAPPER.readTree(argumentsJson);
      JsonNode urlNode = args.get("url");
      if (urlNode == null || !urlNode.isTextual() || urlNode.asText().isBlank()) {
        return ToolResult.failure("入参缺少非空字符串字段 url: " + argumentsJson);
      }
      url = urlNode.asText();
    } catch (JsonProcessingException e) {
      return ToolResult.failure("入参 JSON 无法解析: " + e.getMessage());
    }

    try {
      sandbox.checkHttpRequest(url);
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url))
              .header("User-Agent", "oryxos-http-get/1.0")
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      String body = response.body() == null ? "" : response.body();
      if (body.length() > MAX_BODY_CHARS) {
        body =
            body.substring(0, MAX_BODY_CHARS)
                + "...(truncated, original="
                + body.length()
                + " chars)";
      }
      return ToolResult.ok("HTTP " + response.statusCode() + "\n" + body);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ToolResult.failure("请求被中断");
    } catch (Exception e) {
      log.info("http_get 执行失败: url={} cause={}", url, e.toString());
      return ToolResult.failure("请求失败: " + e.getMessage());
    }
  }
}
