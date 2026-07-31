package com.oryxos.core.support;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolCatalog;
import com.oryxos.core.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 手工注册的工具目录（单测用）。 */
public class FakeToolCatalog implements ToolCatalog {

  private final List<OryxTool> tools = new ArrayList<>();

  public FakeToolCatalog register(OryxTool tool) {
    tools.add(tool);
    return this;
  }

  public static OryxTool echoTool(String name) {
    return new OryxTool() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public String description() {
        return "echo 测试工具";
      }

      @Override
      public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}";
      }

      @Override
      public ToolResult execute(String argumentsJson) {
        return ToolResult.ok("echo:" + argumentsJson);
      }
    };
  }

  public static OryxTool failingTool(String name) {
    return new OryxTool() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public String description() {
        return "总是失败的测试工具";
      }

      @Override
      public String inputSchema() {
        return "{\"type\":\"object\"}";
      }

      @Override
      public ToolResult execute(String argumentsJson) {
        throw new RuntimeException("boom");
      }
    };
  }

  @Override
  public Optional<OryxTool> find(String toolName) {
    return tools.stream().filter(t -> t.name().equals(toolName)).findFirst();
  }

  @Override
  public List<OryxTool> findAll(List<String> toolNames) {
    return toolNames.stream().map(n -> find(n).orElseThrow()).toList();
  }

  @Override
  public List<String> registeredNames() {
    return tools.stream().map(OryxTool::name).sorted().toList();
  }
}
