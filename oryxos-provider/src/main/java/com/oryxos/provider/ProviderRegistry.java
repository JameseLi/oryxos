package com.oryxos.provider;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * Provider name → {@link ChatModel} 显式映射（课件「坑一」：禁止靠「扫描容器里所有 ChatModel」区分——Bean 类型 相同、name 未必对得上）。
 *
 * <p>启动时读 {@link ProviderProperties} 逐条经 {@link ChatModelFactory} 构造 {@link ChatModel}，存进内存 {@code
 * Map<name, ChatModel>}：「谁对谁必须写死、看得见」。未知名抛 {@link ProviderNotFoundException}。
 */
@Component
public class ProviderRegistry {

  private static final Logger log = LoggerFactory.getLogger(ProviderRegistry.class);

  private final Map<String, ChatModel> chatModels = new LinkedHashMap<>();
  private final Map<String, String> models = new LinkedHashMap<>();

  public ProviderRegistry(ProviderProperties properties, ChatModelFactory factory) {
    for (ProviderProperties.ProviderEntry entry : properties.getProviders()) {
      if (entry.getName() == null || entry.getName().isBlank()) {
        log.warn("跳过无 name 的 provider 条目: {}", entry);
        continue;
      }
      try {
        ChatModel model = factory.build(entry);
        chatModels.put(entry.getName(), model);
        models.put(entry.getName(), entry.getModel());
        log.info("已注册 provider: {}", entry.getName());
      } catch (RuntimeException e) {
        // 构造失败不阻断启动——Entry 的 ${ENV} 未提供或 type 不支持时给清晰日志；ProfileLoader 校验会另行报错
        log.error("构造 provider 失败，跳过: {} - {}", entry.getName(), e.getMessage());
      }
    }
  }

  /** 按 name 取 {@link ChatModel}；取不到直接抛 {@link ProviderNotFoundException}。 */
  public ChatModel resolve(String providerName) {
    ChatModel model = chatModels.get(providerName);
    if (model == null) {
      throw new ProviderNotFoundException(providerName);
    }
    return model;
  }

  /** 全局层该 provider 声明的默认 model（Profile 可覆盖）。 */
  public String modelOf(String providerName) {
    return models.get(providerName);
  }

  /** 已注册的 provider name 集合（诊断 / 启动日志用）。 */
  public java.util.Set<String> registeredNames() {
    return chatModels.keySet();
  }
}
