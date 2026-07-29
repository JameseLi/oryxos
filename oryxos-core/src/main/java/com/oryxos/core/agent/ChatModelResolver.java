package com.oryxos.core.agent;

import org.springframework.ai.chat.model.ChatModel;

/**
 * Provider 解析接口（DIP：core 定义，oryxos-provider 的 ProviderService 实现）。
 * 宪法原则四：按 provider name 显式解析，禁止容器类型扫描。
 */
public interface ChatModelResolver {

    /**
     * 按 provider 名解析 ChatModel。
     *
     * @throws IllegalStateException provider 未配置 / 密钥环境变量缺失（附可用清单与缺失项）
     */
    ChatModel resolve(String providerName);

    /** 返回 provider 对应的模型标识（审计用）。 */
    String modelOf(String providerName);
}
