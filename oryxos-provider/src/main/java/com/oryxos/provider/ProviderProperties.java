package com.oryxos.provider;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Provider 全局层配置（课件「第一步」：{@code application.yaml} 的 {@code oryxos.providers} 列表）。
 *
 * <p>list 形态（与课程示例对齐）：
 *
 * <pre>{@code
 * oryxos:
 *   providers:
 *     - name: deepseek
 *       api-key: ${DEEPSEEK_API_KEY}
 *     - name: kimi
 *       api-key: ${KIMI_API_KEY}
 * }</pre>
 *
 * <p>这里只管「连不连得上」——provider 名、凭证占位、（可选）base URL、默认模型、type（deepseek / dashscope /
 * openai-compatible）；Profile 层管「这个 Agent 怎么用」（用哪个 model、什么温度）。两层职责不混 （课件 「第一步」明确要求）。Spring 自动把
 * {@code ${ENV}} 占位在绑定时从 {@link System#getenv} 解析，所以 {@code apiKey} 字段持的是解析后的真实值——不在配置文件里出现真
 * key（宪法工程约束）。
 */
@Component
@ConfigurationProperties(prefix = "oryxos")
public class ProviderProperties {

  /** 已声明的 provider 列表（按声明顺序）。name 即 Agent 绑定的路由键。 */
  private List<ProviderEntry> providers = new ArrayList<>();

  public List<ProviderEntry> getProviders() {
    return providers;
  }

  public void setProviders(List<ProviderEntry> providers) {
    this.providers = providers == null ? new ArrayList<>() : providers;
  }

  /** 单个 provider 的全局层声明。 */
  public static class ProviderEntry {
    private String name;
    private String type;
    private String apiKey;
    private String baseUrl;
    private String model;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }
  }
}
