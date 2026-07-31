package com.oryxos.provider;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * Provider 注册表：provider name → ChatModel 的<b>显式映射</b>（宪法原则四， 禁止"扫描容器里所有
 * ChatModel"按类型区分——同类型多家并存必有歧义）。
 *
 * <p>懒构造：首次 resolve 时才实例化并缓存——保证 `oryxos init` 等无密钥命令 也能正常启动；密钥环境变量缺失在 resolve 时
 * fail-fast（FR-013：对话启动即报， 而不是首次调用才报错）。
 */
@Component
public class ProviderRegistry {

  private static final Logger log = LoggerFactory.getLogger(ProviderRegistry.class);

  private final ProviderProperties properties;
  private final ChatModelFactory factory;
  private final Map<String, ChatModel> cache = new ConcurrentHashMap<>();

  /** 环境变量读取入口（测试可替换）。 */
  private Function<String, String> envReader = System::getenv;

  public ProviderRegistry(ProviderProperties properties, ChatModelFactory factory) {
    this.properties = properties;
    this.factory = factory;
  }

  /** 测试专用：注入假环境变量表。 */
  void setEnvReader(Function<String, String> envReader) {
    this.envReader = envReader;
  }

  public ChatModel resolve(String providerName) {
    ProviderProperties.ProviderSpec spec = specOf(providerName);
    return cache.computeIfAbsent(
        providerName,
        name -> {
          String apiKey = requireApiKey(name, spec);
          log.info(
              "构造 ChatModel: provider={} type={} model={}", name, spec.getType(), spec.getModel());
          return factory.create(name, spec, apiKey);
        });
  }

  public String modelOf(String providerName) {
    return specOf(providerName).getModel();
  }

  public Set<String> availableProviders() {
    return properties.getProviders().keySet();
  }

  private ProviderProperties.ProviderSpec specOf(String providerName) {
    ProviderProperties.ProviderSpec spec = properties.getProviders().get(providerName);
    if (spec == null) {
      throw new IllegalStateException(
          "Provider 未配置: '"
              + providerName
              + "'（可用: "
              + (availableProviders().isEmpty() ? "<无>" : availableProviders())
              + "）");
    }
    return spec;
  }

  private String requireApiKey(String providerName, ProviderProperties.ProviderSpec spec) {
    String envName = spec.getApiKeyEnv();
    if (envName == null || envName.isBlank()) {
      throw new IllegalStateException(
          "Provider '" + providerName + "' 缺少 api-key-env 配置（密钥只走环境变量，禁止明文）");
    }
    String apiKey = envReader.apply(envName);
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "环境变量 "
              + envName
              + " 缺失（provider: "
              + providerName
              + "）。请 export "
              + envName
              + "=<your-api-key> 后重试");
    }
    return apiKey;
  }
}
