package com.oryxos.core.workspace;

import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 运行时工作区布局（.oryxos/ 目录结构单一入口）。 所有模块解析工作区路径都经过本类，禁止各自拼路径。 */
@Component
public class WorkspaceLayout {

  private final Path root;

  public WorkspaceLayout(@Value("${oryxos.workspace-dir:.oryxos}") String workspaceDir) {
    this.root = Path.of(workspaceDir);
  }

  /** 测试专用：直接指定根目录。 */
  public static WorkspaceLayout of(Path root) {
    return new WorkspaceLayout(root.toString());
  }

  public Path root() {
    return root;
  }

  public Path agentsDir() {
    return root.resolve("agents");
  }

  public Path agentDir(String agentName) {
    return agentsDir().resolve(agentName);
  }

  public Path agentFile(String agentName) {
    return agentDir(agentName).resolve("AGENT.md");
  }

  public Path memoryFile() {
    return root.resolve("memory").resolve("MEMORY.md");
  }

  /** Bootstrap 文件（注入 system prompt，每次热读）。 */
  public List<Path> bootstrapFiles() {
    return List.of(root.resolve("AGENTS.md"), root.resolve("SOUL.md"), root.resolve("USER.md"));
  }

  /** init 命令需要确保存在的目录。 */
  public List<Path> requiredDirs() {
    return List.of(
        root, agentsDir(), root.resolve("memory"), root.resolve("sessions"), root.resolve("logs"));
  }
}
