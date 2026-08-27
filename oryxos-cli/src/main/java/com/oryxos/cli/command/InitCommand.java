package com.oryxos.cli.command;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Command;

/**
 * {@code oryxos init}（第 18 节轻命令）：初始化一个 OryxOS 工程，生成 {@code .oryxos/} 工作区（技术方案 8.1）。
 *
 * <p>幂等：已存在的文件/目录跳过不覆盖（用户手改过的内容不被模板冲掉）。{@code oryxos.db} 不在此创建-- 轻命令不碰 JDBC，库文件由首次重命令启动时的数据源自动建立。
 */
@Command(
    mixinStandardHelpOptions = true,
    name = "init",
    description = "初始化一个 OryxOS 工程（生成 .oryxos/ 工作区）")
public class InitCommand implements Runnable {

  /** 工作区子目录（技术方案 8.1）：agents/memory/sessions/logs + 16 节裁定的 profiles 全局层。 */
  private static final List<String> WORKSPACE_DIRS =
      List.of("agents", "memory", "sessions", "logs", "profiles");

  @Override
  public void run() {
    Path root = CliWorkspace.root();
    try {
      List<String> created = new ArrayList<>();
      created.addAll(createDirs(root));
      created.addAll(writeIfAbsent(root.resolve("AGENTS.md"), AGENTS_MD));
      created.addAll(writeIfAbsent(root.resolve("SOUL.md"), SOUL_MD));
      created.addAll(writeIfAbsent(root.resolve("USER.md"), USER_MD));
      created.addAll(writeIfAbsent(root.resolve("memory/MEMORY.md"), MEMORY_MD));
      created.addAll(writeIfAbsent(root.resolve("mcp_servers.yaml"), MCP_SERVERS_YAML));
      created.addAll(writeIfAbsent(root.resolve("profiles/default.yaml"), DEFAULT_PROFILE_YAML));
      System.out.println("OryxOS 工作区已就绪: " + root.toAbsolutePath());
      for (String item : created) {
        System.out.println("  创建 " + item);
      }
      System.out.println("下一步: 编辑 " + root.resolve("profiles/default.yaml") + " 后执行 oryxos chat");
    } catch (IOException e) {
      throw new UncheckedIOException("初始化工作区失败: " + root, e);
    }
  }

  private static List<String> createDirs(Path root) throws IOException {
    List<String> created = new ArrayList<>();
    for (String dir : WORKSPACE_DIRS) {
      Path path = root.resolve(dir);
      if (Files.notExists(path)) {
        Files.createDirectories(path);
        created.add(dir + "/");
      }
    }
    return created;
  }

  private static List<String> writeIfAbsent(Path path, String content) throws IOException {
    if (Files.exists(path)) {
      return List.of();
    }
    Path parent = path.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(path, content);
    return List.of(rootRelative(path));
  }

  private static String rootRelative(Path path) {
    return CliWorkspace.root().relativize(path).toString().replace('\\', '/');
  }

  // ---- 默认模板（简明占位，用户可自行编辑；Bootstrap 内容每次组装 prompt 都重读不缓存） ----

  private static final String AGENTS_MD =
      "# AGENTS.md\n\nOryxOS 工作区说明（Bootstrap，注入 system prompt）。\n" + "在这里写本实例上运行的所有 Agent 的公共约定。\n";

  private static final String SOUL_MD =
      "# SOUL.md\n\nOryxOS 灵魂文件（Bootstrap，注入 system prompt）。\n" + "在这里写所有 Agent 共有的价值观/语气/底线。\n";

  private static final String USER_MD =
      "# USER.md\n\n用户画像（Bootstrap，注入 system prompt）。\n" + "在这里写默认用户的称呼、偏好、常用语言。\n";

  private static final String MEMORY_MD =
      "# MEMORY.md\n\n# 核心记忆区（永不截断，全量注入）\n\n（暂无）\n\n" + "# 归档记忆区（参与检索）\n\n（暂无）\n";

  private static final String MCP_SERVERS_YAML =
      "# MCP server 配置（零代码 Plugin Tool 档，第 20 节交付后生效）。\n"
          + "# 形态示例：\n"
          + "# servers:\n"
          + "#   - name: weather\n"
          + "#     command: npx\n"
          + "#     args: [\"-y\", \"@some/mcp-weather\"]\n";

  /** 默认 Profile：引用 boot application.yaml 全局层默认声明的 deepseek 示例（用户可改）。 */
  private static final String DEFAULT_PROFILE_YAML =
      "# 默认 Profile（oryxos init 生成，可自行编辑）。\n"
          + "# provider.name 必须能在全局层 oryxos.providers（application.yaml）里找到同名项。\n"
          + "name: default\n"
          + "description: 默认助手\n"
          + "identity:\n"
          + "  agent_name: OryxOS Assistant\n"
          + "  prompt: 你是 OryxOS 的默认助手，认真、简洁地回答用户问题。\n"
          + "provider:\n"
          + "  name: deepseek\n"
          + "  model: deepseek-chat\n"
          + "tools: []\n";
}
