package com.oryxos.core.agent;

import com.oryxos.core.workspace.WorkspaceLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** 上下文加载器：组装 system prompt 所需的 Agent 任务指令与 Bootstrap 文件。 宪法工程约束：每次组装都重新读文件，不缓存——用户改完即时生效。 */
@Component
public class ContextLoader {

  private final WorkspaceLayout workspace;

  public ContextLoader(WorkspaceLayout workspace) {
    this.workspace = workspace;
  }

  /** 组装 system prompt：Bootstrap 文件 + AGENT.md 正文（去除 frontmatter）。 */
  public String buildSystemPrompt(AgentDefinition agent) {
    StringBuilder sb = new StringBuilder();
    for (Path bootstrap : workspace.bootstrapFiles()) {
      String content = readIfExists(bootstrap);
      if (content != null && !content.isBlank()) {
        sb.append("# ")
            .append(bootstrap.getFileName())
            .append('\n')
            .append(content.strip())
            .append("\n\n");
      }
    }
    String body = agentBody(agent.agentFile());
    if (!body.isBlank()) {
      sb.append("# 任务指令\n").append(body.strip());
    }
    return sb.toString().strip();
  }

  /** 读取 AGENT.md 正文（frontmatter 之后的部分），每次热读。 */
  public String agentBody(Path agentFile) {
    String text = AgentLoader.readText(agentFile).replace("\r\n", "\n");
    if (!text.startsWith(AgentLoader.FRONTMATTER_START)) {
      return text;
    }
    int end = text.indexOf(AgentLoader.FRONTMATTER_END, AgentLoader.FRONTMATTER_START.length());
    if (end < 0) {
      return text;
    }
    int bodyStart = text.indexOf('\n', end + AgentLoader.FRONTMATTER_END.length());
    return bodyStart < 0 ? "" : text.substring(bodyStart + 1);
  }

  private String readIfExists(Path path) {
    return Files.isRegularFile(path) ? AgentLoader.readText(path) : null;
  }
}
