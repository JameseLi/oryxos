package com.oryxos.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.audit.LlmCallAudit;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.prompt.Prompt;
import com.oryxos.core.tool.OryxTool;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * {@link ProviderService} 验收 harness（课件「验收 harness」第二个单测类，含三个最值钱的关键回归测试）。
 *
 * <p>覆盖验收点：双 provider 按名路由不串台；未知名抛异常；成功/失败都落审计；自动执行关闭。
 *
 * <p>纯单测（mock {@link ChatModel} 与 {@link LlmCallAudit}，自建 {@link ProviderRegistry}）——不花一分钱、
 * 不依赖网络。三个关键回归方法名译英文 + {@code @DisplayName} 承载课件原文。
 */
@ExtendWith(MockitoExtension.class)
class ProviderServiceTest {

  @Mock private ChatModel chatModel;
  @Mock private LlmCallAudit audit;

  private final ToolSchemaAdapter adapter = new ToolSchemaAdapter();

  @Test
  @DisplayName("按名路由_两个provider不串台")
  void routesByProviderName_twoProvidersDoNotCrossover() {
    var deepseek = org.mockito.Mockito.mock(ChatModel.class);
    var kimi = org.mockito.Mockito.mock(ChatModel.class);
    when(kimi.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenReturn(emptyResponse());
    var registry =
        new ProviderRegistry(providerPropsWith("deepseek", "kimi"), new NoBuildChatModelFactory());
    // 手工塞已构造的 mock ChatModel 进 registry（绕过工厂真实构造——单测不连真实 API）
    var service =
        new ProviderService(
            injectMocks(registry, "deepseek", deepseek, "kimi", kimi), adapter, audit);

    service.chat("s-1", profileUsing("kimi"), barePrompt());

    verify(kimi, times(1)).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    verify(deepseek, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
  }

  @Test
  @DisplayName("调用失败_审计必须留下success为false的记录")
  void callFails_auditRecordsSuccessFalseThenRethrows() {
    when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenThrow(new RuntimeException("connect timeout"));
    var registry =
        new ProviderRegistry(providerPropsWith("deepseek"), new NoBuildChatModelFactory());
    var service = new ProviderService(injectMocks(registry, "deepseek", chatModel), adapter, audit);

    // 异常继续上抛
    assertThrows(
        RuntimeException.class, () -> service.chat("s-1", profileUsing("deepseek"), barePrompt()));

    // 但审计先落了：success=false + 原因
    verify(audit)
        .record(
            eq("s-1"),
            eq("deepseek"),
            any(),
            isNull(), // usage null（失败无用量）
            eq(false),
            contains("timeout"),
            anyLong());
  }

  @Test
  @DisplayName("带工具schema调用_请求里关闭了自动执行")
  void callWithToolSchema_disablesAutoExecution() {
    when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenReturn(emptyResponse());
    var registry =
        new ProviderRegistry(providerPropsWith("deepseek"), new NoBuildChatModelFactory());
    var service = new ProviderService(injectMocks(registry, "deepseek", chatModel), adapter, audit);
    OryxTool httpGet = newFakeTool("http_get");

    service.chat("s-1", profileUsing("deepseek"), promptWithTools(httpGet));

    var captor = ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
    verify(chatModel).call(captor.capture());
    ChatOptions opts = captor.getValue().getOptions();
    assertNotNull(opts, "请求里必须带上翻译过的 schema 选项");
    // 坑二回归：一旦有人改回自动执行，这里立刻红
    assertFalse(
        ToolCallingChatOptions.isInternalToolExecutionEnabled(opts),
        "internalToolExecutionEnabled 必须为 false（宪法原则 IV）");
    // 翻译过的 schema 必须带上：不是空 toolCallbacks
    assertFalse(
        !(opts instanceof ToolCallingChatOptions tc)
            || tc.getToolCallbacks() == null
            || tc.getToolCallbacks().isEmpty(),
        "toolCallbacks 必须带上翻译过的 schema，不能为空");
  }

  @Test
  @DisplayName("未知名 provider 抛清晰异常，不悄悄用错或留空跑过")
  void unknownProvider_throwsClearError() {
    var registry =
        new ProviderRegistry(providerPropsWith("deepseek"), new NoBuildChatModelFactory());
    var service = new ProviderService(injectMocks(registry, "deepseek", chatModel), adapter, audit);

    assertThrows(
        ProviderNotFoundException.class,
        () -> service.chat("s-1", profileUsing("ghost"), barePrompt()));
    verify(chatModel, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
  }

  // ---- 测试助手 ----

  private static Prompt barePrompt() {
    return new Prompt(List.of(new UserMessage("hello")), List.of());
  }

  private static Prompt promptWithTools(OryxTool... tools) {
    return new Prompt(List.of(new UserMessage("今天北京天气怎么样？")), List.of(tools));
  }

  private static Profile profileUsing(String providerName) {
    return new Profile(
        providerName + "-agent",
        "test",
        null,
        new Profile.Provider(providerName, "m-" + providerName, 0.5),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }

  private static OryxTool newFakeTool(String name) {
    return new OryxTool() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public String description() {
        return "fake";
      }

      @Override
      public String getInputSchema() {
        return "{}";
      }

      // 第 17 节 OryxTool 增补 execute 后的占位实现：本测试只关心 schema 翻译，不会真执行
      @Override
      public com.oryxos.core.tool.ToolResult execute(String input) {
        return com.oryxos.core.tool.ToolResult.ok("not executed in provider tests");
      }
    };
  }

  /** 一个最小 ChatResponse（无 metadata / 无结果）：仅供路由 / autoExec 回路径测试占位，不判内容。 */
  private static org.springframework.ai.chat.model.ChatResponse emptyResponse() {
    return new org.springframework.ai.chat.model.ChatResponse(java.util.List.of());
  }

  /** 一个跳过真实工厂构造的工厂：unit test 直接往 registry map 塞 mock ChatModel。 */
  private static final class NoBuildChatModelFactory extends ChatModelFactory {
    NoBuildChatModelFactory() {
      super();
    }

    @Override
    ChatModel build(ProviderProperties.ProviderEntry entry) {
      // 被调到即抛 RuntimeException，被 ProviderRegistry 构造期 catch 吞掉（该 entry 不进 map），
      // 随后单测用反射把 mock ChatModel 塞进 map——避免单测连真实 API。
      throw new IllegalStateException("NoBuildChatModelFactory 仅用于纯单测，不应真实构造 ChatModel");
    }
  }

  /** 构造一个仅含给定 names 的 ProviderProperties（全 ${ENV} 占位，不解析真实 key）。 */
  private static ProviderProperties providerPropsWith(String... names) {
    ProviderProperties props = new ProviderProperties();
    var entries = new java.util.ArrayList<ProviderProperties.ProviderEntry>();
    for (String n : names) {
      ProviderProperties.ProviderEntry e = new ProviderProperties.ProviderEntry();
      e.setName(n);
      e.setType(
          n.equals("deepseek")
              ? "deepseek"
              : (n.equals("dashscope") ? "dashscope" : "openai-compatible"));
      e.setApiKey("fake-key-not-used-in-mock");
      e.setModel("m-" + n);
      entries.add(e);
    }
    props.setProviders(entries);
    return props;
  }

  /**
   * 用反射把 mock ChatModel 直接塞进已构造的 registry 内部 map（绕过真实构造）。单测不连真实 API。 返回该 registry 自身，便于一行链式构造
   * service。
   */
  @SuppressWarnings("unchecked")
  private static ProviderRegistry injectMocks(ProviderRegistry registry, Object... nameThenModel) {
    try {
      var field = ProviderRegistry.class.getDeclaredField("chatModels");
      field.setAccessible(true);
      var map = (java.util.Map<String, ChatModel>) field.get(registry);
      for (int i = 0; i < nameThenModel.length; i += 2) {
        map.put((String) nameThenModel[i], (ChatModel) nameThenModel[i + 1]);
      }
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("反射塞 mock 失败", e);
    }
    return registry;
  }
}
