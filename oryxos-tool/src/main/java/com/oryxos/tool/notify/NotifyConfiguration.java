package com.oryxos.tool.notify;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * notify 装配（第 19 节）：提供 {@link RestClient} Bean。
 *
 * <p>为什么需要这个配置类：Boot 自动配置只提供 {@code RestClient.Builder} 不直接提供 {@code RestClient} Bean， 而 {@link
 * WebhookNotifyAdapter} 按课件骨架构造注入 {@code RestClient}（测试可注入定制实例）。{@code RestClient.create()}
 * 同步阻塞客户端即可--出站推送是单次请求，核心阶段不做连接池调优。
 *
 * <p>第 20 节 Tool 体系装配（AnnotatedToolAdapter 注册管道）如有更多装配需要，再在此类或新装配类上演进。
 */
@Configuration
public class NotifyConfiguration {

  @Bean
  public RestClient restClient() {
    return RestClient.create();
  }
}
