package com.oryxos.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 元信息。文档入口：{@code /v3/api-docs}（JSON）与 {@code /swagger-ui.html}（UI）。
 *
 * <p>REST 约定（AGENTS.md §9 + oryxos-init skill）：资源名词复数、{@code /api/v1} 前缀、 合理 HTTP 状态码、统一 {@code
 * ApiResponse} 信封。Controller 随 US-5 落地。
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI oryxOsOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("OryxOS API")
                .version("v1")
                .description("OryxOS Agent OS Web Service（统一 ApiResponse 信封，/api/v1 前缀）"));
  }
}
