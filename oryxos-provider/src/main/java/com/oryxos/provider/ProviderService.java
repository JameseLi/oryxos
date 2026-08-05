package com.oryxos.provider;

import com.oryxos.core.audit.LlmCallAudit;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.prompt.Prompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.stereotype.Service;

/**
 * Provider 薄适配层（课件「第三步」核心）。
 *
 * <p>对外只露一个方法 {@link #chat(String, Profile, Prompt)}：按 {@link Profile#provider()} 里的 name 取对应
 * {@link ChatModel}（显式映射，坑一）；把 {@link Prompt#availableTools()} 翻译成 schema（只翻译不执行）； 关掉 Spring AI
 * 自动工具执行（坑二 / 宪法原则 IV）；调 {@link ChatModel#call}；无论成功失败都落 {@link LlmCallAudit}（课件「第三/四步」）；最后原样返回
 * {@link ChatResponse}——里头若带「想调某工具」的 请求，由上层循环/执行器接管，本层不解析也不执行。
 *
 * <p>本类不引入 fallback / hedge / 熔断 / 超时——课件「有几样先别做」。
 */
@Service
public class ProviderService {

  private static final Logger log = LoggerFactory.getLogger(ProviderService.class);

  private final ProviderRegistry registry;
  private final ToolSchemaAdapter adapter;
  private final LlmCallAudit audit;

  public ProviderService(ProviderRegistry registry, ToolSchemaAdapter adapter, LlmCallAudit audit) {
    this.registry = registry;
    this.adapter = adapter;
    this.audit = audit;
  }

  /**
   * 发起一次 LLM 调用。
   *
   * @param sessionId 所属会话（审计按会话关联；{@code null} 表示无会话绑定）
   * @param profile 本次 Agent 的 Profile（取 {@code provider.name}、{@code provider.model}、{@code
   *     temperature}）
   * @param prompt 本次要发给模型的消息 + 本轮可用工具清单
   * @return 模型回话（可能带 tool call 请求，执行不在此层）
   * @throws ProviderNotFoundException provider name 在显式映射表里找不到
   */
  public ChatResponse chat(String sessionId, Profile profile, Prompt prompt) {
    String providerName = profile.provider().name();
    ChatModel model = registry.resolve(providerName);
    String modelName = resolveModel(profile);

    var callbacks = adapter.toSpringAiTools(prompt.availableTools());
    // Build options：temperature 由 Builder.temperature(Double) 返回协变类型 ChatOptions$Builder，
    // 故把它放到链尾再 .build()——这样 build() 返回 ChatOptions 可直接喂 Prompt 构造器。
    ToolCallingChatOptions.Builder optionsBuilder =
        ToolCallingChatOptions.builder()
            // 坑二 / 宪法原则 IV：关掉 Spring AI 自动工具执行——执行由 OryxOS 自己掌控
            .internalToolExecutionEnabled(false)
            .toolCallbacks(callbacks)
            .model(modelName);
    Double temp = profile.provider().temperature();
    ChatOptions options =
        (temp == null) ? optionsBuilder.build() : optionsBuilder.temperature(temp).build();

    var springPrompt = new org.springframework.ai.chat.prompt.Prompt(prompt.messages(), options);

    long startedAt = System.currentTimeMillis();
    try {
      ChatResponse response = model.call(springPrompt);
      long elapsed = System.currentTimeMillis() - startedAt;
      // getMetadata 在 Spring AI 为 @NonNull；getUsage 可能为 null（成功调用也不总有用量）——audit.record 原样落 null
      Usage usage = response.getMetadata().getUsage();
      audit.record(sessionId, providerName, modelName, usage, true, null, elapsed);
      return response;
    } catch (RuntimeException e) {
      long elapsed = System.currentTimeMillis() - startedAt;
      // 失败也要留痕：只记成功不记失败 = 一次事故在系统里没痕迹（课件「第三/四步」点名易漏缺陷）
      audit.record(sessionId, providerName, modelName, null, false, e.getMessage(), elapsed);
      log.warn("LLM 调用失败 (sessionId={}, provider={}): {}", sessionId, providerName, e.getMessage());
      throw e; // 异常继续上抛给上层处理；本层不做 fallback/熔断
    }
  }

  /** Profile 显式写了 model 用 Profile 的；否则回退到全局层该 provider 声明的默认 model。 */
  private String resolveModel(Profile profile) {
    if (profile.provider().model() != null && !profile.provider().model().isBlank()) {
      return profile.provider().model();
    }
    return registry.modelOf(profile.provider().name());
  }
}
