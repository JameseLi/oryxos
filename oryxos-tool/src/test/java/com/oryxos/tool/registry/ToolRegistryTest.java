package com.oryxos.tool.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.oryxos.core.agent.ContextLoader;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.prompt.Prompt;
import com.oryxos.core.react.PromptBuilder;
import com.oryxos.core.session.Session;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.mcp.McpToolAdapter;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.annotation.Tool;

/**
 * {@link DefaultToolRegistry} 验收 harness（课件「验收 harness」第二批）：三种来源的工具都以 OryxTool 身份注册进来；按 Profile 的
 * tools 字段过滤后子集<b>恰好等于</b>声明列表、不多不少。
 */
class ToolRegistryTest {

  @TempDir Path workspace;

  private DefaultToolRegistry registry;

  @BeforeEach
  void setUp() throws IOException {
    registry = new DefaultToolRegistry();
    Files.writeString(workspace.resolve("AGENTS.md"), "全局协作守则（Bootstrap 内容）。");
  }

  @Test
  @DisplayName("三种来源的工具都以 OryxTool 身份注册进同一注册表：直注册/注解 Bean/MCP 适配")
  void threeSourcesRegisteredUnderOneRegistry() {
    registry.register(plainTool("plain_tool"));
    registry.registerAnnotated(new SampleTools());
    McpSchema.Tool spec =
        new McpSchema.Tool(
            "mcp_tool",
            null,
            "来自 MCP server",
            new McpSchema.JsonSchema("object", null, null, null, null, null),
            null,
            null,
            null);
    registry.register(new McpToolAdapter(mock(McpSyncClient.class), spec));

    assertThat(registry.find("plain_tool")).isPresent();
    assertThat(registry.find("sample_tool")).get().isInstanceOf(AnnotatedToolAdapter.class);
    assertThat(registry.find("mcp_tool")).isPresent();
    assertThat(registry.all()).hasSize(3);
  }

  @Test
  @DisplayName("重名注册被拒绝：不允许来源之间静默覆盖")
  void duplicateRegistrationIsRejected() {
    registry.register(plainTool("dup_tool"));

    assertThatThrownBy(() -> registry.register(plainTool("dup_tool")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("重名");
  }

  @Test
  @DisplayName("registerAnnotated 没有 @Tool 方法的 Bean 直接报错（装配错位尽早暴露）")
  void registerAnnotatedRejectsBeanWithoutToolMethods() {
    assertThatThrownBy(() -> registry.registerAnnotated(new Object()))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("按 Profile tools 过滤后子集恰好等于声明列表：多一个（没过滤干净）和少一个（过滤过头）都是错")
  void profileFilteringExactlyMatchesDeclaredList() {
    registry.register(plainTool("tool_a"));
    registry.register(plainTool("tool_b"));
    registry.register(plainTool("tool_c"));
    PromptBuilder builder = new PromptBuilder(new ContextLoader(workspace), registry, null);

    Prompt prompt =
        builder.build(new Session("s-1", "weather"), profile(List.of("tool_a", "tool_c")));

    assertThat(prompt.availableTools())
        .extracting(OryxTool::name)
        .containsExactly("tool_a", "tool_c");
  }

  @Test
  @DisplayName("未声明 tools 即空集（17 节白名单 fail-closed 语义，真注册表上验证）")
  void undeclaredToolsMeansEmptySet() {
    registry.register(plainTool("tool_a"));
    PromptBuilder builder = new PromptBuilder(new ContextLoader(workspace), registry, null);

    Prompt prompt = builder.build(new Session("s-1", "weather"), profile(List.of()));

    assertThat(prompt.availableTools()).isEmpty();
  }

  @Test
  @DisplayName("声明列表里的未知工具名被过滤掉，不让幽灵工具进 prompt")
  void unknownDeclaredToolIsFilteredOut() {
    registry.register(plainTool("tool_a"));
    PromptBuilder builder = new PromptBuilder(new ContextLoader(workspace), registry, null);

    Prompt prompt = builder.build(new Session("s-1", "weather"), profile(List.of("tool_a", "不存在")));

    assertThat(prompt.availableTools()).extracting(OryxTool::name).containsExactly("tool_a");
  }

  @Test
  @DisplayName("all() 返回按名排序的不可变快照")
  void allReturnsSortedImmutableSnapshot() {
    registry.register(plainTool("bbb_tool"));
    registry.register(plainTool("aaa_tool"));

    List<OryxTool> snapshot = registry.all();

    assertThat(snapshot).extracting(OryxTool::name).containsExactly("aaa_tool", "bbb_tool");
    assertThatThrownBy(() -> snapshot.add(plainTool("ccc_tool")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private OryxTool plainTool(String name) {
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
      public ToolResult execute(String input) {
        return ToolResult.ok("ok");
      }
    };
  }

  /** 17 节 PromptBuilderTest 同款构造手法（零改动的消费端契约验证）。 */
  private Profile profile(List<String> tools) {
    return new Profile(
        "weather",
        null,
        new Profile.Identity("小欧", "你是一个天气助手"),
        null,
        tools,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        new Profile.Bootstrap(List.of("AGENTS.md")),
        null);
  }

  /** 注解来源的测试夹具（方式三形态：@Tool 方法 + 显式工具名）。 */
  static class SampleTools {

    @Tool(name = "sample_tool", description = "注解来源的示例工具")
    public String sample(
        @org.springframework.ai.tool.annotation.ToolParam(description = "输入") String input) {
      return "echo: " + input;
    }
  }
}
