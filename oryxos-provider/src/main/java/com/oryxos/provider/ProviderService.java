package com.oryxos.provider;

import com.oryxos.core.agent.ChatModelResolver;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * Provider 服务（core ChatModelResolver 的实现）：Agent 按 frontmatter 里的
 * provider.name 显式绑定解析，多 Provider 并存时路由无歧义（FR-014）。
 */
@Service
public class ProviderService implements ChatModelResolver {

    private final ProviderRegistry registry;

    public ProviderService(ProviderRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ChatModel resolve(String providerName) {
        return registry.resolve(providerName);
    }

    @Override
    public String modelOf(String providerName) {
        return registry.modelOf(providerName);
    }
}
