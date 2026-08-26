package com.oryxos.core.react;

import com.oryxos.core.agent.ContextLoader;
import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.prompt.Prompt;
import com.oryxos.core.session.Session;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolRegistry;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

/**
 * Prompt 组装器（课件「配角一」）：每轮开始把四部分按顺序拼成一段 Prompt。
 *
 * <p>四部分：①system prompt（角色设定 + 启动信息 + Skill，末尾附当前日期时间--模型自己不知道今天几号， 定时场景的「今天」全靠这一行）②长期记忆（Memory
 * 模块提供，没装配就跳过） ③会话历史（只留最近 N 轮，默认 20，超了截断--坑二的解法） ④当前可用的工具列表（按 Profile 的 tools 字段过滤）。
 *
 * <p>长期记忆 ≠ 会话历史：前者跨会话都在，后者只是本次对话的往来记录，别混在一起。
 */
public class PromptBuilder {

  /** 历史截断默认值：最近 20 轮（课件「本节交付物」约定；Profile settings 可覆盖）。 */
  static final int DEFAULT_MAX_HISTORY_TURNS = 20;

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final ContextLoader contextLoader;
  private final ToolRegistry toolRegistry;
  private final MemoryService memoryService;
  private final Clock clock;

  public PromptBuilder(
      ContextLoader contextLoader,
      ToolRegistry toolRegistry,
      MemoryService memoryService,
      Clock clock) {
    this.contextLoader = contextLoader;
    this.toolRegistry = toolRegistry;
    this.memoryService = memoryService;
    this.clock = clock;
  }

  /** 便捷构造（Clock 缺省用系统时区；生产装配用，测试注入固定 Clock 断言日期时间行）。 */
  public PromptBuilder(
      ContextLoader contextLoader, ToolRegistry toolRegistry, MemoryService memoryService) {
    this(contextLoader, toolRegistry, memoryService, Clock.systemDefaultZone());
  }

  /** 组装一轮 Prompt：system -> [memory] -> history（截断后）+ 本轮可用工具清单。 */
  public Prompt build(Session session, Profile profile) {
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(buildSystemPrompt(profile)));
    appendMemory(session, messages);
    appendHistory(session, profile, messages);
    return new Prompt(messages, availableTools(profile));
  }

  private String buildSystemPrompt(Profile profile) {
    StringBuilder sb = new StringBuilder();
    if (profile.identity() != null) {
      String agentName = profile.identity().agentName();
      if (agentName != null && !agentName.isBlank()) {
        sb.append("你是 ").append(agentName).append("。\n");
      }
      String identityPrompt = profile.identity().prompt();
      if (identityPrompt != null && !identityPrompt.isBlank()) {
        sb.append(identityPrompt).append('\n');
      }
    }
    String context = contextLoader.load(profile);
    if (!context.isBlank()) {
      sb.append(context).append('\n');
    }
    // 末行日期时间：模型不知道今天几号，定时场景的「今天」全靠这一行（课件「配角一」）
    sb.append("当前时间：").append(DATE_TIME_FORMATTER.format(LocalDateTime.now(clock)));
    return sb.toString();
  }

  // 没装配（null）= 第二部分整体缺席；装配了但产出空白同样不产生空段
  private void appendMemory(Session session, List<Message> messages) {
    if (memoryService == null) {
      return;
    }
    String memoryContext = memoryService.buildContext(session);
    if (memoryContext != null && !memoryContext.isBlank()) {
      messages.add(new SystemMessage(memoryContext));
    }
  }

  private void appendHistory(Session session, Profile profile, List<Message> messages) {
    List<Message> history = session.messages();
    int maxTurns = maxHistoryTurns(profile);
    if (history.size() > maxTurns) {
      // 坑二解法：只留最近 N 轮（核心阶段简单截断，压缩/总结属扩展阶段）
      history = history.subList(history.size() - maxTurns, history.size());
    }
    messages.addAll(history);
  }

  private int maxHistoryTurns(Profile profile) {
    Integer configured = profile.settings() == null ? null : profile.settings().maxHistoryTurns();
    if (configured == null) {
      return DEFAULT_MAX_HISTORY_TURNS;
    }
    if (configured <= 0) {
      throw new IllegalArgumentException("settings.max_history_turns 必须为正数: " + configured);
    }
    // 显式 intValue：null 分支已在上处理，但拆箱交给编译器会被 P3C 判 NPE 风险
    return configured.intValue();
  }

  private List<OryxTool> availableTools(Profile profile) {
    List<String> allowed = profile.tools();
    return toolRegistry.all().stream().filter(t -> allowed.contains(t.name())).toList();
  }
}
