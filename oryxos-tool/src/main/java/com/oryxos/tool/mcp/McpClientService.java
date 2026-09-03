package com.oryxos.tool.mcp;

import com.oryxos.tool.registry.DefaultToolRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * MCP server 连接维护与工具注册（本节交付物，课件「三」骨架逐字同构）。
 *
 * <p>启动时连接所有配置的 MCP server（{@code .oryxos/mcp_servers.yaml}），调 {@code tools/list} 拿工具列表， 把每个 MCP
 * 工具包装成 {@link McpToolAdapter} 注册进统一注册表。关键语义（课件骨架注释逐字）： <b>「外部依赖失联不拖垮自身启动——只 WARN，OryxOS 照常起」</b>：单
 * server 连接/初始化/listTools 失败， 记 WARN 跳过它的全部工具，其余 server 照常注册。
 *
 * <p>{@code transport} 两档（课件 6.2「JSON-RPC over stdio 或 SSE」）：{@code stdio} 经 {@link
 * StdioClientTransport}（{@code command} 首个 token 为命令、余为参数，{@code env} 附加进程环境）， {@code sse} 经 {@link
 * HttpClientSseClientTransport}（{@code command} 字段放 base URL）。
 */
@Component
public class McpClientService {

  private static final Logger log = LoggerFactory.getLogger(McpClientService.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
  private static final McpJsonMapper JSON_MAPPER =
      new JacksonMcpJsonMapper(new com.fasterxml.jackson.databind.ObjectMapper());

  private final DefaultToolRegistry toolRegistry;
  private final Path configPath;
  private final List<McpSyncClient> connected = new ArrayList<>();

  public McpClientService(
      DefaultToolRegistry toolRegistry,
      @Value("${oryxos.workspace-dir:.oryxos}") String workspaceDir) {
    this.toolRegistry = toolRegistry;
    this.configPath = Path.of(workspaceDir, "mcp_servers.yaml");
  }

  /** 连接全部配置的 MCP server 并注册其工具；单个失败只 WARN、不抛（课件骨架 @PostConstruct 语义）。 */
  @PostConstruct
  public void connectAll() {
    List<McpServerConfig> configs = loadConfigs();
    for (McpServerConfig cfg : configs) {
      try {
        McpSyncClient client = connect(cfg);
        try {
          client.initialize();
          List<McpSchema.Tool> specs = client.listTools().tools();
          for (McpSchema.Tool spec : specs) {
            toolRegistry.register(new McpToolAdapter(client, spec));
          }
          connected.add(client);
          log.info("MCP server {} 已连接并注册 {} 个工具", cfg.name(), specs.size());
        } catch (RuntimeException e) {
          client.close();
          throw e;
        }
      } catch (RuntimeException e) {
        // 外部依赖失联不拖垮自身启动——只 WARN，OryxOS 照常起（课件骨架注释逐字）
        log.warn("MCP server {} 连接失败，跳过它的工具", cfg.name(), e);
      }
    }
  }

  /** 退出时关闭全部连接（stdio 实现持有子进程，必须显式回收防泄漏）。 */
  @PreDestroy
  public void closeAll() {
    for (McpSyncClient client : connected) {
      try {
        client.closeGracefully();
      } catch (RuntimeException e) {
        log.warn("MCP client 关闭失败（忽略，不影响停机）: {}", e.getMessage());
      }
    }
    connected.clear();
  }

  /** 建立传输与同步客户端（protected 供测试覆写注入 mock，不碰网）。 */
  protected McpSyncClient connect(McpServerConfig cfg) {
    if (cfg.isSse()) {
      return McpClient.sync(HttpClientSseClientTransport.builder(cfg.command()).build())
          .requestTimeout(REQUEST_TIMEOUT)
          .build();
    }
    String[] tokens = cfg.command().trim().split("\\s+");
    String command = tokens[0];
    List<String> args = new ArrayList<>();
    for (int i = 1; i < tokens.length; i++) {
      args.add(tokens[i]);
    }
    ServerParameters params = ServerParameters.builder(command).args(args).env(cfg.env()).build();
    return McpClient.sync(new StdioClientTransport(params, JSON_MAPPER))
        .requestTimeout(REQUEST_TIMEOUT)
        .build();
  }

  /** 读 {@code mcp_servers.yaml}（servers 列表）；文件缺失/为空返回空列表（零配置 = 零注册，不报错）。 */
  List<McpServerConfig> loadConfigs() {
    if (!Files.exists(configPath)) {
      return List.of();
    }
    try (InputStream in = Files.newInputStream(configPath)) {
      Map<String, Object> root = new Yaml().load(in);
      if (root == null) {
        return List.of();
      }
      Object servers = root.get("servers");
      if (!(servers instanceof List<?>)) {
        return List.of();
      }
      List<McpServerConfig> configs = new ArrayList<>();
      for (Object item : (List<?>) servers) {
        if (item instanceof Map<?, ?>) {
          Map<?, ?> map = (Map<?, ?>) item;
          try {
            configs.add(
                new McpServerConfig(
                    str(map.get("name")),
                    str(map.get("transport")),
                    str(map.get("command")),
                    stringMap(map.get("env"))));
          } catch (RuntimeException e) {
            // 单条配置畸形只 WARN 跳过该条，其余照常、启动不炸（与失联隔离同语义）
            log.warn("MCP server 配置无效，跳过该条目: {}", e.getMessage());
          }
        }
      }
      return configs;
    } catch (IOException e) {
      throw new UncheckedIOException("读取 mcp_servers.yaml 失败: " + configPath, e);
    }
  }

  private static String str(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static Map<String, String> stringMap(Object value) {
    if (!(value instanceof Map<?, ?>)) {
      return null;
    }
    Map<String, String> result = new java.util.LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
      if (entry.getValue() != null) {
        result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
      }
    }
    return result;
  }
}
