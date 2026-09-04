package com.oryxos.tool.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具适配器（本节交付物，课件「三」骨架逐字同构）：把 MCP Tool 适配成 {@link OryxTool}。
 *
 * <p>三个契约方法直接映射 MCP 的 {@code tools/list} 返回（name/description/inputSchema）；执行时通过 MCP
 * 协议（JSON-RPC）<b>原样转发</b>给对应 MCP server（入参 JSON 原文解析后作为 {@code arguments} 传给 {@code
 * callTool}），结果包装成 {@link ToolResult} 返回。课件骨架语义： {@code
 * client.callTool(...).map(ToolResult::success).orElseGet(() -> ToolResult.failure("MCP 调用失败",
 * true))}-- 外部系统瞬时失败<b>可重试</b>（ToolResult 第四维度的第一处真实消费）。
 */
public class McpToolAdapter implements OryxTool {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final McpSyncClient client;
  private final McpSchema.Tool tool;

  public McpToolAdapter(McpSyncClient client, McpSchema.Tool tool) {
    this.client = client;
    this.tool = tool;
  }

  @Override
  public String name() {
    return tool.name();
  }

  @Override
  public String description() {
    return tool.description() == null ? "" : tool.description();
  }

  /** {@code tools/list} 返回的 inputSchema 序列化为 JSON 字符串（Provider 翻译 Function Calling 的数据源）。 */
  @Override
  public String getInputSchema() {
    try {
      return MAPPER.writeValueAsString(tool.inputSchema());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("MCP 工具 '" + tool.name() + "' 的 inputSchema 序列化失败", e);
    }
  }

  @Override
  public ToolResult execute(String input) {
    Map<String, Object> arguments = parseArguments(input);
    try {
      McpSchema.CallToolResult result =
          client.callTool(new McpSchema.CallToolRequest(tool.name(), arguments));
      String text = extractText(result);
      if (Boolean.TRUE.equals(result.isError())) {
        return ToolResult.failure("MCP 调用失败: " + text, true);
      }
      return ToolResult.ok(text);
    } catch (RuntimeException e) {
      // 外部 server 瞬时失败值得再调一次：可重试位 true（课件骨架 orElseGet 分支语义）
      return ToolResult.failure("MCP 调用失败: " + e.getMessage(), true);
    }
  }

  private Map<String, Object> parseArguments(String input) {
    if (input == null || input.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Object> parsed =
          MAPPER.readValue(input, new TypeReference<Map<String, Object>>() {});
      return parsed == null ? Map.of() : parsed;
    } catch (JsonProcessingException e) {
      // 入参不是合法 JSON：回填失败让模型自行纠正（不可重试--同样的入参重试也不会好）
      throw new IllegalArgumentException(
          "MCP 工具 '" + tool.name() + "' 的入参不是合法 JSON: " + e.getMessage(), e);
    }
  }

  /** 提取 {@code CallToolResult} 的文本内容（TextContent 为主，其余类型 toString 兜底）。 */
  private static String extractText(McpSchema.CallToolResult result) {
    List<McpSchema.Content> contents = result.content();
    if (contents == null || contents.isEmpty()) {
      return "";
    }
    StringBuilder text = new StringBuilder();
    for (McpSchema.Content content : contents) {
      if (content instanceof McpSchema.TextContent) {
        text.append(((McpSchema.TextContent) content).text());
      } else {
        text.append(content);
      }
      text.append('\n');
    }
    return text.toString().stripTrailing();
  }
}
