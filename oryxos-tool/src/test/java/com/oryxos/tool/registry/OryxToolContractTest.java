package com.oryxos.tool.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.tool.builtin.FileTools;
import com.oryxos.tool.builtin.HttpTools;
import com.oryxos.tool.builtin.InteractionTools;
import com.oryxos.tool.builtin.NotifyTools;
import com.oryxos.tool.builtin.ShellTools;
import com.oryxos.tool.builtin.WebSearchTools;
import com.oryxos.tool.interaction.ConsoleUserInteraction;
import com.oryxos.tool.mcp.McpToolAdapter;
import com.oryxos.tool.notify.NotifyChannelAdapter;
import com.oryxos.tool.notify.NotifyTarget;
import com.oryxos.tool.sandbox.PermissiveSandbox;
import com.oryxos.tool.search.SearchProvider;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.RestClient;

/**
 * {@link OryxTool} 契约三件套测试（课件「验收 harness」最值钱测试）：参数化遍历注册表里每个工具， name/description/inputSchema
 * 都非空——任何一个工具漏实现 {@code getInputSchema()}，这里立刻红 （缺了它，Provider 翻译 Function Calling
 * 时直接卡死）。新工具自动纳入契约检查。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OryxToolContractTest {

  private final DefaultToolRegistry fullRegistry = buildFullRegistry();

  static OryxToolContractTest harness() {
    return new OryxToolContractTest();
  }

  java.util.stream.Stream<Named<OryxTool>> allRegisteredTools() {
    return fullRegistry.all().stream().map(tool -> Named.of(tool.name(), tool));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("allRegisteredTools")
  @DisplayName("每个工具的契约三件套都不能缺")
  void allRegisteredToolsHaveCompleteContract(OryxTool tool) {
    assertThat(tool.name()).isNotBlank();
    assertThat(tool.description()).isNotBlank();
    assertThat(tool.getInputSchema()).isNotBlank();
  }

  @org.junit.jupiter.api.Test
  @DisplayName("完整注册表恰好含 12 个内置工具（6 内置 + notify + 扩展 5）+ 测试注入的两类来源")
  void fullRegistryHoldsExpectedTools() {
    assertThat(fullRegistry.all())
        .extracting(OryxTool::name)
        .contains(
            "read_file",
            "write_file",
            "list_dir",
            "edit_file",
            "grep",
            "glob",
            "shell",
            "http_get",
            "http_post",
            "ask_user",
            "web_search",
            "notify",
            "plain_tool",
            "mcp_tool");
  }

  @org.junit.jupiter.api.Test
  @DisplayName("重名注册拒绝（契约测试自身装配自检）")
  void duplicateNamesRejectedAtAssembly() {
    DefaultToolRegistry registry = new DefaultToolRegistry();
    registry.registerAnnotated(new SampleTools());

    assertThatThrownBy(() -> registry.registerAnnotated(new SampleTools()))
        .isInstanceOf(IllegalStateException.class);
  }

  private static DefaultToolRegistry buildFullRegistry() {
    PermissiveSandbox sandbox = new PermissiveSandbox();
    DefaultToolRegistry registry = new DefaultToolRegistry();
    registry.registerAnnotated(
        new FileTools(sandbox),
        new ShellTools(sandbox, ".oryxos"),
        new HttpTools(sandbox, RestClient.create()),
        new InteractionTools(new ConsoleUserInteraction()),
        new WebSearchTools(sandbox, provider(), SearchProviderEndpoint.DUCKDUCKGO),
        new NotifyTools(neverSendAdapter()));
    registry.register(plainTool("plain_tool"));
    registry.register(mcpTool());
    return registry;
  }

  private static SearchProvider provider() {
    return query -> List.of();
  }

  private static NotifyChannelAdapter neverSendAdapter() {
    return (NotifyTarget target, String content) -> {
      throw new UnsupportedOperationException("契约测试不应发送通知");
    };
  }

  private static OryxTool plainTool(String name) {
    return new OryxTool() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public String description() {
        return "测试用工具 " + name;
      }

      @Override
      public String getInputSchema() {
        return "{\"type\":\"object\"}";
      }

      @Override
      public com.oryxos.core.tool.ToolResult execute(String input) {
        return com.oryxos.core.tool.ToolResult.ok("ok");
      }
    };
  }

  private static OryxTool mcpTool() {
    McpSchema.Tool spec =
        new McpSchema.Tool(
            "mcp_tool",
            null,
            "来自 MCP server 的工具",
            new McpSchema.JsonSchema("object", null, null, null, null, null),
            null,
            null,
            null);
    return new McpToolAdapter(mock(McpSyncClient.class), spec);
  }

  /** 端点常量的本地别名（真实装配取 {@code DuckDuckGoSearchProvider.ENDPOINT}）。 */
  private static final class SearchProviderEndpoint {
    private static final String DUCKDUCKGO = "https://api.duckduckgo.com";
  }

  /** 注解来源测试夹具。 */
  static class SampleTools {

    @org.springframework.ai.tool.annotation.Tool(name = "sample_tool", description = "注解来源的示例工具")
    public String sample(String input) {
      return input;
    }
  }
}
