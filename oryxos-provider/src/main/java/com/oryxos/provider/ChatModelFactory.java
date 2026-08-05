package com.oryxos.provider;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

/**
 * ChatModel 程序化构造工厂（ruptcy方式：显式映射，不依赖 *-starter/autoconfigure；课件「坑一/坑三」）。
 *
 * <p>按 {@link ProviderProperties.ProviderEntry#getType()} 分发：
 *
 * <ul>
 *   <li>{@code deepseek} → {@link DeepSeekChatModel}（经 {@link DeepSeekApi#builder()}）。
 *   <li>{@code dashscope} → {@link DashScopeChatModel}（通义千问，经 {@link DashScopeApi#builder()}）。
 *   <li>{@code openai-compatible} → {@link OpenAiChatModel}（Kimi/智谱/混元/豆包等 OpenAI 兼容协议）。
 * </ul>
 *
 * <p>type 缺省时按 name 推断（兼容仅写 {@code name: deepseek} 的样例）。{@code apiKey} 由 {@link ProviderProperties}
 * 已从 {@code ${ENV}} 占位解析好后传入，本厂不再读 env。
 */
@Component
public class ChatModelFactory {

  /** 与 type 同名的 provider 走自名。 */
  private static final String TYPE_DEEPSEEK = "deepseek";

  /** 与 type 同名的 provider 走自名。 */
  private static final String TYPE_DASHSCOPE = "dashscope";

  /** OpenAI 兼容协议 providers（Kimi/智谱/混元/豆包等）。 */
  private static final String TYPE_OPENAI_COMPATIBLE = "openai-compatible";

  ChatModel build(ProviderProperties.ProviderEntry entry) {
    String type = entry.getType();
    if (type == null || type.isBlank()) {
      type = inferTypeByName(entry.getName());
    }
    switch (type) {
      case TYPE_DEEPSEEK:
        return buildDeepSeek(entry);
      case TYPE_DASHSCOPE:
        return buildDashScope(entry);
      case TYPE_OPENAI_COMPATIBLE:
        return buildOpenAiCompatible(entry);
      default:
        throw new IllegalArgumentException(
            "不支持的 provider type: " + type + " (name=" + entry.getName() + ")");
    }
  }

  private static String inferTypeByName(String name) {
    if (name == null) {
      throw new IllegalArgumentException("provider entry 缺 name 与 type");
    }
    // 与 type 同名的 provider 走自名。name 取应用配置里声明的字面值（课件示例均小写），精确匹配，规避 locale-sensitive 大小写折叠（FindSecBugs
    // IMPROPER_UNICODE）。
    if (TYPE_DEEPSEEK.equals(name)) {
      return TYPE_DEEPSEEK;
    }
    if (TYPE_DASHSCOPE.equals(name)) {
      return TYPE_DASHSCOPE;
    }
    // 其余默认走 OpenAI 兼容（Kimi/智谱/混元/豆包…）
    return TYPE_OPENAI_COMPATIBLE;
  }

  private static ChatModel buildDeepSeek(ProviderProperties.ProviderEntry entry) {
    var apiBuilder = DeepSeekApi.builder().apiKey(requireApiKey(entry));
    if (entry.getBaseUrl() != null && !entry.getBaseUrl().isBlank()) {
      apiBuilder.baseUrl(entry.getBaseUrl());
    }
    var modelBuilder = DeepSeekChatModel.builder().deepSeekApi(apiBuilder.build());
    if (entry.getModel() != null && !entry.getModel().isBlank()) {
      modelBuilder.defaultOptions(
          org.springframework.ai.deepseek.DeepSeekChatOptions.builder()
              .model(entry.getModel())
              .build());
    }
    return modelBuilder.build();
  }

  private static ChatModel buildDashScope(ProviderProperties.ProviderEntry entry) {
    var apiBuilder = DashScopeApi.builder().apiKey(requireApiKey(entry));
    if (entry.getBaseUrl() != null && !entry.getBaseUrl().isBlank()) {
      apiBuilder.baseUrl(entry.getBaseUrl());
    }
    var modelBuilder = DashScopeChatModel.builder().dashScopeApi(apiBuilder.build());
    if (entry.getModel() != null && !entry.getModel().isBlank()) {
      modelBuilder.defaultOptions(DashScopeChatOptions.builder().model(entry.getModel()).build());
    }
    return modelBuilder.build();
  }

  private static ChatModel buildOpenAiCompatible(ProviderProperties.ProviderEntry entry) {
    var apiBuilder = OpenAiApi.builder().apiKey(requireApiKey(entry));
    if (entry.getBaseUrl() != null && !entry.getBaseUrl().isBlank()) {
      apiBuilder.baseUrl(entry.getBaseUrl());
    }
    var modelBuilder = OpenAiChatModel.builder().openAiApi(apiBuilder.build());
    if (entry.getModel() != null && !entry.getModel().isBlank()) {
      modelBuilder.defaultOptions(
          org.springframework.ai.openai.OpenAiChatOptions.builder()
              .model(entry.getModel())
              .build());
    }
    return modelBuilder.build();
  }

  private static String requireApiKey(ProviderProperties.ProviderEntry entry) {
    if (entry.getApiKey() == null || entry.getApiKey().isBlank()) {
      throw new IllegalStateException(
          "Provider '" + entry.getName() + "' 的 api-key 未提供（检查 ${ENV} 环境变量是否已注入）");
    }
    return entry.getApiKey();
  }
}
