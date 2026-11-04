package com.oryxos.memory.tool;

import com.oryxos.core.memory.MemoryScope;
import com.oryxos.core.memory.MemoryService;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 长期记忆内置 Tool（第 22 节）：把 {@link MemoryService} 暴露给 Agent 调用。
 *
 * <p>只认门面，所以对底下是哪档后端完全无感——换 Markdown/SQLite/Mem0 不碰工具、不碰 {@code PromptBuilder}、不碰测试里对工具的 断言（契约
 * 3：写核心还是写归档由 Agent 通过 {@code scope} 显式指定，缺省归档）。
 */
public class MemoryTools {

  private final MemoryService memoryService;

  public MemoryTools(MemoryService memoryService) {
    this.memoryService = memoryService;
  }

  /** 记住一件值得长期记住的事；{@code scope} 缺省/空白按归档处理。 */
  @Tool(name = "save_memory", description = "记住一件值得长期记住的事")
  public String saveMemory(
      @ToolParam(description = "要记住的内容") String content,
      @ToolParam(description = "core 或 archival，不确定就填 archival") String scope) {
    memoryService.remember(content, parseScope(scope));
    return "已记住";
  }

  /** 按关键词检索长期记忆；未命中返回约定文案，不抛异常打断对话。 */
  @Tool(name = "recall_memory", description = "按关键词检索长期记忆")
  public String recallMemory(@ToolParam(description = "检索关键词") String keyword) {
    List<String> hits = memoryService.recall(keyword);
    if (hits == null || hits.isEmpty()) {
      return "没有找到相关记忆";
    }
    return String.join("\n", hits);
  }

  /** 解析 Agent 传入的 scope：null/空白 → ARCHIVAL；大小写不敏感；无法识别直接报错（由 ToolExecutor 审计）。 */
  static MemoryScope parseScope(String scope) {
    if (scope == null || scope.isBlank()) {
      return MemoryScope.ARCHIVAL;
    }
    try {
      return MemoryScope.valueOf(scope.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("scope 只能是 core 或 archival: " + scope, e);
    }
  }
}
