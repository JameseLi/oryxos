package com.oryxos.provider;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ProviderRegistry 单测（宪法 IV 显式映射）：
 * 两家同类型 provider 并存互不歧义、按名解析、密钥缺失 fail-fast（FR-013/014）。
 */
class ProviderRegistryTest {

    private ProviderRegistry newRegistry(Map<String, String> fakeEnv, ProviderProperties props) {
        ProviderRegistry registry = new ProviderRegistry(props, new ChatModelFactory());
        registry.setEnvReader(fakeEnv::get);
        return registry;
    }

    private static ProviderProperties twoCompatibleProviders() {
        ProviderProperties props = new ProviderProperties();
        ProviderProperties.ProviderSpec kimi = new ProviderProperties.ProviderSpec();
        kimi.setType("openai-compatible");
        kimi.setModel("moonshot-v1-8k");
        kimi.setBaseUrl("https://api.moonshot.cn/v1");
        kimi.setApiKeyEnv("KIMI_KEY");
        ProviderProperties.ProviderSpec doubao = new ProviderProperties.ProviderSpec();
        doubao.setType("openai-compatible");
        doubao.setModel("doubao-pro-32k");
        doubao.setBaseUrl("https://ark.cn-beijing.volces.com/api/v3");
        doubao.setApiKeyEnv("DOUBAO_KEY");
        props.setProviders(Map.of("kimi", kimi, "doubao", doubao));
        return props;
    }

    @Test
    void 两家同类型provider并存且按名解析不同模型_FR014() {
        ProviderRegistry registry = newRegistry(
                Map.of("KIMI_KEY", "k1", "DOUBAO_KEY", "k2"), twoCompatibleProviders());

        ChatModel kimiModel = registry.resolve("kimi");
        ChatModel doubaoModel = registry.resolve("doubao");

        assertThat(kimiModel).isNotSameAs(doubaoModel);
        assertThat(registry.modelOf("kimi")).isEqualTo("moonshot-v1-8k");
        assertThat(registry.modelOf("doubao")).isEqualTo("doubao-pro-32k");
        // 缓存：同名重复解析返回同一实例
        assertThat(registry.resolve("kimi")).isSameAs(kimiModel);
    }

    @Test
    void 未配置的provider报错并列出可用_US3_AS3() {
        ProviderRegistry registry = newRegistry(Map.of("KIMI_KEY", "k1", "DOUBAO_KEY", "k2"),
                twoCompatibleProviders());

        assertThatThrownBy(() -> registry.resolve("ghost"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ghost")
                .hasMessageContaining("kimi");
    }

    @Test
    void 密钥环境变量缺失启动即报_FR013() {
        ProviderRegistry registry = newRegistry(Map.of("KIMI_KEY", "k1"), twoCompatibleProviders());

        assertThatThrownBy(() -> registry.resolve("doubao"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOUBAO_KEY");
    }

    @Test
    void deepseek型构造() {
        ProviderProperties props = new ProviderProperties();
        ProviderProperties.ProviderSpec ds = new ProviderProperties.ProviderSpec();
        ds.setType("deepseek");
        ds.setModel("deepseek-chat");
        ds.setApiKeyEnv("DS_KEY");
        props.setProviders(Map.of("deepseek", ds));
        ProviderRegistry registry = newRegistry(Map.of("DS_KEY", "x"), props);

        assertThat(registry.resolve("deepseek")).isNotNull();
        assertThat(registry.modelOf("deepseek")).isEqualTo("deepseek-chat");
    }

    @Test
    void 未知type报错() {
        ProviderProperties props = new ProviderProperties();
        ProviderProperties.ProviderSpec bad = new ProviderProperties.ProviderSpec();
        bad.setType("magic");
        bad.setModel("m1");
        bad.setApiKeyEnv("K");
        props.setProviders(Map.of("bad", bad));
        ProviderRegistry registry = newRegistry(Map.of("K", "v"), props);

        assertThatThrownBy(() -> registry.resolve("bad"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未知的 provider type");
    }
}
