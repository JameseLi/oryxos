package com.oryxos.tool.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.tool.ToolResult;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link McpToolAdapter} 验收 harness（课件「验收 harness」第四批）：listTools 返回的工具被包装、 execute 转发参数原样、结果包成
 * ToolResult（失败可重试）。mock MCP 客户端不碰网。
 */
class McpToolAdapterTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private McpSyncClient client;
  private McpSchema.Tool tool;
  private McpToolAdapter adapter;

  @BeforeEach
  void setUp() {
    client = mock(McpSyncClient.class);
    tool =
        new McpSchema.Tool(
            "weather_lookup",
            null,
            "查询城市天气",
            new McpSchema.JsonSchema(
                "object",
                Map.of("city", Map.of("type", "string")),
                List.of("city"),
                null,
                null,
                null),
            null,
            null,
            null);
    adapter = new McpToolAdapter(client, tool);
  }

  @Test
  @DisplayName("契约三件套直接映射 tools/list 返回：name/description/inputSchema")
  void mapsToolsListMetadata() throws Exception {
    assertThat(adapter.name()).isEqualTo("weather_lookup");
    assertThat(adapter.description()).isEqualTo("查询城市天气");

    JsonNode schema = JSON.readTree(adapter.getInputSchema());
    assertThat(schema.get("type").asText()).isEqualTo("object");
    assertThat(schema.get("properties").get("city").get("type").asText()).isEqualTo("string");
    assertThat(schema.get("required").get(0).asText()).isEqualTo("city");
  }

  @Test
  @DisplayName("execute 转发参数原样、成功结果包成 ToolResult.ok")
  void executeForwardsArgumentsAsIs() {
    when(client.callTool(any()))
        .thenReturn(
            new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("北京 25 度")), false));

    ToolResult result = adapter.execute("{\"city\":\"北京\"}");

    ArgumentCaptor<McpSchema.CallToolRequest> captor =
        ArgumentCaptor.forClass(McpSchema.CallToolRequest.class);
    verify(client).callTool(captor.capture());
    assertThat(captor.getValue().name()).isEqualTo("weather_lookup");
    assertThat(captor.getValue().arguments()).isEqualTo(Map.of("city", "北京"));
    assertThat(result.success()).isTrue();
    assertThat(result.output()).isEqualTo("北京 25 度");
  }

  @Test
  @DisplayName("server 返回 isError：包成可重试的失败 ToolResult（课件骨架 orElseGet 分支语义）")
  void executeWrapsServerErrorAsRetryableFailure() {
    when(client.callTool(any()))
        .thenReturn(new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("boom")), true));

    ToolResult result = adapter.execute("{\"city\":\"北京\"}");

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("MCP 调用失败").contains("boom");
    assertThat(result.retryable()).isTrue();
  }

  @Test
  @DisplayName("server 调用抛异常：包成可重试的失败 ToolResult，异常不吞")
  void executeWrapsThrownExceptionAsRetryableFailure() {
    when(client.callTool(any())).thenThrow(new RuntimeException("连接断开"));

    ToolResult result = adapter.execute("{\"city\":\"北京\"}");

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("连接断开");
    assertThat(result.retryable()).isTrue();
  }

  @Test
  @DisplayName("入参不是合法 JSON：明确失败（不可重试--同样的入参重试也不会好）")
  void executeRejectsInvalidJsonArguments() {
    assertThatThrownBy(() -> adapter.execute("not-json"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("合法 JSON");
  }
}
