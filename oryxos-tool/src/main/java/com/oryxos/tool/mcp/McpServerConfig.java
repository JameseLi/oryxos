package com.oryxos.tool.mcp;

import java.util.Map;

/**
 * MCP server 配置（课件「三」骨架点名的名字）：对应 {@code .oryxos/mcp_servers.yaml} 的 {@code servers} 列表项，四键 {@code
 * name}/{@code transport}/{@code command}/{@code env} （第 20 节交付物逐字）。
 *
 * <p>{@code transport} 取 {@code stdio}（默认，{@code command} 为完整命令行、首个 token 是命令） 或 {@code sse}（{@code
 * command} 字段放 base URL）。 {@code env} 为进程环境变量附加项（凭证走环境 变量占位，不落明文）。
 */
public record McpServerConfig(
    String name, String transport, String command, Map<String, String> env) {

  public McpServerConfig {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("MCP server 配置缺少 name");
    }
    if (transport == null || transport.isBlank()) {
      throw new IllegalArgumentException("MCP server '" + name + "' 缺少 transport（stdio/sse）");
    }
    if (command == null || command.isBlank()) {
      throw new IllegalArgumentException(
          "MCP server '" + name + "' 缺少 command（stdio 为命令行、sse 为 base URL）");
    }
    env = env == null ? Map.of() : Map.copyOf(env);
  }

  /** 是否 SSE 传输（其余一律按 stdio 处理）。 */
  public boolean isSse() {
    return "sse".equalsIgnoreCase(transport);
  }
}
