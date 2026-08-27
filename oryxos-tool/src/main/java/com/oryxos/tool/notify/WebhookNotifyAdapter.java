package com.oryxos.tool.notify;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 通用 webhook 实现（核心阶段唯一一档，课件「三」骨架逐行保真）。
 *
 * <p>为什么只做通用档：企业微信、飞书、钉钉的群机器人都提供 webhook 地址，「给一个 URL 发一次 POST 就能送达」（课件 6.1
 * 判断标准）的场景一个实现全覆盖；逐家签名算法、AccessToken 刷新属认证细节，扩展阶段按 {@code channelType} 新增专用 Adapter（飞书/企业微信 payload
 * 差异见课件 6.2，加签/富文本见 6.4 两条路）。
 *
 * <p>异常语义（harness 第一批守点）：webhook 返回 5xx 等错误时 {@code RestClient} 抛出的 {@code
 * RestClientResponseException} 原样上抛、不捕获不包装--「异常向上抛、不静默吞掉」， 上层（17 节 ToolExecutor 的统一路径）负责转失败结果并落
 * tool_invocations 审计。
 */
@Component
public class WebhookNotifyAdapter implements NotifyChannelAdapter {

  private final RestClient restClient;

  public WebhookNotifyAdapter(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public void send(NotifyTarget target, String content) {
    // URL 是渠道特定配置，来自 NotifyTarget 而非硬编码（harness 守点 2：换 Target 即换地址）
    String url = target.config().get("url");
    if (url == null || url.isBlank()) {
      throw new IllegalStateException("webhook 通知渠道缺少 url 配置: channelType=" + target.channelType());
    }
    restClient
        .post()
        .uri(url)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("content", content))
        .retrieve()
        .toBodilessEntity();
  }
}
