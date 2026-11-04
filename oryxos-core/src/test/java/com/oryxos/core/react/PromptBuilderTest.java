package com.oryxos.core.react;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oryxos.core.agent.ContextLoader;
import com.oryxos.core.memory.MemoryScope;
import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.prompt.Prompt;
import com.oryxos.core.session.Session;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolRegistry;
import com.oryxos.core.tool.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

/**
 * {@link PromptBuilder} 验收 harness（课件「验收 harness」第二个单测类）。
 *
 * <p>覆盖验收点：四部分顺序正确；历史超 N 轮被截断（坑二回归）；system prompt 末尾含当前日期时间。
 */
class PromptBuilderTest {

  @TempDir Path workspace;

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-08-26T02:15:30Z"), ZoneId.of("Asia/Shanghai"));

  private ContextLoader contextLoader;
  private ToolRegistry toolRegistry;

  @BeforeEach
  void setUp() throws IOException {
    Files.writeString(workspace.resolve("AGENTS.md"), "全局协作守则（Bootstrap 内容）。");
    contextLoader = new ContextLoader(workspace);
    toolRegistry = new FakeToolRegistry(List.of(fakeTool("http_get"), fakeTool("shell")));
  }

  @Test
  @DisplayName("四部分顺序正确：system（含角色+Bootstrap+日期时间）-> 长期记忆 -> 会话历史，工具列表随 Prompt 交付")
  void buildsFourPartsInOrder() {
    MemoryService memory =
        new MemoryService() {
          @Override
          public String buildContext(Session session) {
            return "长期记忆：用户在北京";
          }

          @Override
          public void remember(String content, MemoryScope scope) {}

          @Override
          public List<String> recall(String keyword) {
            return List.of();
          }
        };
    PromptBuilder builder = new PromptBuilder(contextLoader, toolRegistry, memory, FIXED_CLOCK);
    Session session = new Session("s-1", "weather");
    session.append("昨天北京天气怎样");

    Prompt prompt = builder.build(session, weatherProfile(List.of("http_get")));

    List<Message> messages = prompt.messages();
    assertEquals(3, messages.size(), "system + memory + 一条历史");

    SystemMessage system = assertInstanceOf(SystemMessage.class, messages.get(0));
    String systemText = system.getText();
    assertTrue(systemText.contains("你是 小欧"), "system 首部分是角色设定: " + systemText);
    assertTrue(systemText.contains("你是一个天气助手"), "system 含 identity.prompt");
    assertTrue(systemText.contains("全局协作守则"), "system 含 Bootstrap 启动信息");
    assertTrue(systemText.endsWith("当前时间：2026-08-26 10:15:30"), "system 末尾是当前日期时间");

    SystemMessage memoryMsg = assertInstanceOf(SystemMessage.class, messages.get(1));
    assertEquals("长期记忆：用户在北京", memoryMsg.getText());
    assertEquals("昨天北京天气怎样", messages.get(2).getText());

    assertEquals(1, prompt.availableTools().size(), "工具列表按 Profile.tools 过滤");
    assertEquals("http_get", prompt.availableTools().get(0).name());
  }

  @Test
  @DisplayName("历史超 N 轮被截断：默认 20 轮，只保留最近 20 条消息（坑二回归）")
  void truncatesHistoryBeyondMaxTurns() {
    PromptBuilder builder = new PromptBuilder(contextLoader, toolRegistry, null, FIXED_CLOCK);
    Session session = new Session("s-1", "weather");
    for (int i = 1; i <= 25; i++) {
      session.append("消息" + i);
    }

    Prompt prompt = builder.build(session, weatherProfile(List.of()));

    List<Message> messages = prompt.messages();
    assertEquals(21, messages.size(), "1 条 system + 截断后的 20 条历史");
    assertEquals("消息6", messages.get(1).getText(), "最早的 5 条被丢弃，第一条保留的是第 6 条");
    assertEquals("消息25", messages.get(20).getText());
  }

  @Test
  @DisplayName("system prompt 末尾含当前日期时间（固定 Clock 断言精确后缀）")
  void systemPromptEndsWithCurrentDateTime() {
    PromptBuilder builder = new PromptBuilder(contextLoader, toolRegistry, null, FIXED_CLOCK);
    Session session = new Session("s-1", "weather");

    Prompt prompt = builder.build(session, weatherProfile(List.of()));

    String systemText = prompt.messages().get(0).getText();
    assertTrue(systemText.endsWith("当前时间：2026-08-26 10:15:30"), "末尾日期时间: " + systemText);
  }

  @Test
  @DisplayName("长期记忆未装配时第二部分整体缺席：不产生空段、不报错")
  void memoryNotAssembled_noMemorySegment() {
    PromptBuilder builder = new PromptBuilder(contextLoader, toolRegistry, null, FIXED_CLOCK);
    Session session = new Session("s-1", "weather");
    session.append("你好");

    Prompt prompt = builder.build(session, weatherProfile(List.of()));

    assertEquals(2, prompt.messages().size(), "只有 system + 历史，无 memory 空段");
    assertInstanceOf(SystemMessage.class, prompt.messages().get(0));
  }

  @Test
  @DisplayName("Profile.tools 过滤工具列表：引用不存在的工具名被忽略，空列表得空子集")
  void filtersToolsByProfileToolsField() {
    PromptBuilder builder = new PromptBuilder(contextLoader, toolRegistry, null, FIXED_CLOCK);
    Session session = new Session("s-1", "weather");

    Prompt withUnknownName = builder.build(session, weatherProfile(List.of("shell", "不存在")));
    assertEquals(1, withUnknownName.availableTools().size());
    assertEquals("shell", withUnknownName.availableTools().get(0).name());

    Prompt withEmpty = builder.build(session, weatherProfile(List.of()));
    assertTrue(withEmpty.availableTools().isEmpty(), "空 tools 字段 = 空子集，不默认放开全部");
  }

  @Test
  @DisplayName("settings.max_history_turns 可覆盖默认截断值；非法值（<=0）报清晰错误")
  void maxHistoryTurnsOverrideAndValidation() {
    PromptBuilder builder = new PromptBuilder(contextLoader, toolRegistry, null, FIXED_CLOCK);
    Session session = new Session("s-1", "weather");
    for (int i = 1; i <= 10; i++) {
      session.append("消息" + i);
    }

    Prompt narrowed = builder.build(session, profileWithSettings(null, 3));
    assertEquals(4, narrowed.messages().size(), "1 system + 最近 3 条");
    assertEquals("消息8", narrowed.messages().get(1).getText());

    assertThrows(
        IllegalArgumentException.class, () -> builder.build(session, profileWithSettings(null, 0)));
  }

  private Profile weatherProfile(List<String> tools) {
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

  private Profile profileWithSettings(Integer maxIterations, Integer maxHistoryTurns) {
    return new Profile(
        "weather",
        null,
        new Profile.Identity("小欧", "你是一个天气助手"),
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        new Profile.Bootstrap(List.of("AGENTS.md")),
        new Profile.Settings(maxIterations, maxHistoryTurns));
  }

  private static OryxTool fakeTool(String name) {
    return new OryxTool() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public String description() {
        return "测试工具 " + name;
      }

      @Override
      public String getInputSchema() {
        return "{}";
      }

      @Override
      public ToolResult execute(String input) {
        return ToolResult.ok(name);
      }
    };
  }

  /** 测试用注册表：固定一组工具，按名查找。 */
  private record FakeToolRegistry(List<OryxTool> tools) implements ToolRegistry {

    @Override
    public Optional<OryxTool> find(String name) {
      return tools.stream().filter(t -> t.name().equals(name)).findFirst();
    }

    @Override
    public List<OryxTool> all() {
      return tools;
    }
  }
}
