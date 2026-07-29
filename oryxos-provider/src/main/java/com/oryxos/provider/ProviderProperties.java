package com.oryxos.provider;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Provider 配置（oryxos.providers.&lt;name&gt;.*）。
 * 密钥本体永不进配置——apiKeyEnv 只存环境变量名（宪法工程约束 / FR-012）。
 */
@Component
@ConfigurationProperties(prefix = "oryxos")
public class ProviderProperties {

    /** provider name → 配置。name 即 Agent 绑定的路由键（FR-014）。 */
    private Map<String, ProviderSpec> providers = new LinkedHashMap<>();

    public Map<String, ProviderSpec> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderSpec> providers) {
        this.providers = providers;
    }

    /** 单个 provider 配置。type: deepseek / dashscope / openai-compatible。 */
    public static class ProviderSpec {
        private String type;
        private String model;
        private String baseUrl;
        private String apiKeyEnv;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKeyEnv() {
            return apiKeyEnv;
        }

        public void setApiKeyEnv(String apiKeyEnv) {
            this.apiKeyEnv = apiKeyEnv;
        }
    }
}
