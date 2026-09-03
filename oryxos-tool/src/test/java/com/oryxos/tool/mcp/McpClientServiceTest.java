package com.oryxos.tool.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.tool.registry.DefaultToolRegistry;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link McpClientService} 验收 harness（课件「验收 harness」最值钱守点之一）： 某个 MCP server
 * 失联不能拖垮启动和其他工具；配置解析逐键；空配置零注册不报错。全 mock 不碰网。
 */
class McpClientServiceTest {

  @TempDir Path workspace;

  @Test
  @DisplayName("某个MCP server失联 不能拖垮启动和其他工具")
  void serverDisconnectDoesNotBreakStartupOrOtherTools() throws Exception {
    writeConfig(
        "servers:",
        "  - name: good",
        "    transport: stdio",
        "    command: echo good-server",
        "  - name: bad",
        "    transport: stdio",
        "    command: echo bad-server");
    DefaultToolRegistry registry = new DefaultToolRegistry();
    McpClientService service =
        new McpClientService(registry, workspace.toString()) {
          @Override
          protected McpSyncClient connect(McpServerConfig cfg) {
            if (cfg.name().equals("good")) {
              McpSyncClient good = mock(McpSyncClient.class);
              when(good.listTools())
                  .thenReturn(
                      new McpSchema.ListToolsResult(
                          List.of(spec("good_mcp_tool", "好 server 的工具")), null));
              return good;
            }
            McpSyncClient bad = mock(McpSyncClient.class);
            // 课件 harness 语义：listTools 抛 ConnectException("refused")（伪代码直接抛受检异常；
            // 真实 listTools 签名无受检异常，经运行时异常包装保持同语义）
            when(bad.listTools()).thenThrow(new RuntimeException(new ConnectException("refused")));
            return bad;
          }
        };

    assertThatCode(service::connectAll).doesNotThrowAnyException();

    assertThat(registry.find("good_mcp_tool")).isPresent();
    assertThat(registry.find("bad_mcp_tool")).isEmpty();
  }

  @Test
  @DisplayName("失联 server 的半开连接被关闭（stdio 子进程/连接资源回收）")
  void failedClientIsClosed() throws Exception {
    writeConfig(
        "servers:", "  - name: bad", "    transport: stdio", "    command: echo bad-server");
    DefaultToolRegistry registry = new DefaultToolRegistry();
    McpSyncClient bad = mock(McpSyncClient.class);
    when(bad.listTools()).thenThrow(new RuntimeException(new ConnectException("refused")));
    McpClientService service =
        new McpClientService(registry, workspace.toString()) {
          @Override
          protected McpSyncClient connect(McpServerConfig cfg) {
            return bad;
          }
        };

    service.connectAll();

    verify(bad).close();
  }

  @Test
  @DisplayName("配置解析逐键：name/transport/command/env")
  void loadConfigsParsesAllKeys() throws Exception {
    writeConfig(
        "servers:",
        "  - name: weather",
        "    transport: sse",
        "    command: https://weather.example.com",
        "    env:",
        "      API_KEY: ${WEATHER_API_KEY}",
        "  - name: github",
        "    transport: stdio",
        "    command: npx -y @modelcontextprotocol/server-github");

    List<McpServerConfig> configs =
        new McpClientService(new DefaultToolRegistry(), workspace.toString()).loadConfigs();

    assertThat(configs).hasSize(2);
    McpServerConfig weather = configs.get(0);
    assertThat(weather.name()).isEqualTo("weather");
    assertThat(weather.transport()).isEqualTo("sse");
    assertThat(weather.command()).isEqualTo("https://weather.example.com");
    assertThat(weather.env()).containsEntry("API_KEY", "${WEATHER_API_KEY}");
    assertThat(weather.isSse()).isTrue();
    McpServerConfig github = configs.get(1);
    assertThat(github.command()).isEqualTo("npx -y @modelcontextprotocol/server-github");
    assertThat(github.env()).isEmpty();
    assertThat(github.isSse()).isFalse();
  }

  @Test
  @DisplayName("空配置启动：零注册、不报错（外部依赖的可用性不是自己的可用性）")
  void connectAllWithMissingConfigRegistersNothing() {
    DefaultToolRegistry registry = new DefaultToolRegistry();
    McpClientService service = new McpClientService(registry, workspace.toString());

    assertThatCode(service::connectAll).doesNotThrowAnyException();
    assertThat(registry.all()).isEmpty();
  }

  private void writeConfig(String... lines) throws Exception {
    Files.writeString(workspace.resolve("mcp_servers.yaml"), String.join("\n", lines) + "\n");
  }

  private static McpSchema.Tool spec(String name, String description) {
    return new McpSchema.Tool(
        name,
        null,
        description,
        new McpSchema.JsonSchema("object", null, null, null, null, null),
        null,
        null,
        null);
  }
}
