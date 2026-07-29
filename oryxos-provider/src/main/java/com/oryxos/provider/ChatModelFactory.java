package com.oryxos.provider;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

/**
 * ChatModel 三型工厂（research.md R4）：
 * <ul>
 *   <li>{@code deepseek} — 官方连接器 DeepSeekChatModel
 *   <li>{@code dashscope} — Spring AI Alibaba 通义连接器
 *   <li>{@code openai-compatible} — OpenAiChatModel + base-url 覆盖
 *       （Kimi/智谱/混元/豆包/OpenAI 本家一律走此型）
 * </ul>
 * 同一类型可实例化多家（如两家 OpenAI 兼容商），这正是自动配置做不到、
 * 必须程序化构造的原因（宪法高发错误清单 #2）。
 */
@Component
public class ChatModelFactory {

    public ChatModel create(String providerName, ProviderProperties.ProviderSpec spec, String apiKey) {
        return switch (spec.getType()) {
            case "deepseek" -> buildDeepSeek(spec, apiKey);
            case "dashscope" -> buildDashScope(spec, apiKey);
            case "openai-compatible" -> buildOpenAiCompatible(providerName, spec, apiKey);
            default -> throw new IllegalStateException(
                    "未知的 provider type: '" + spec.getType() + "'（provider: " + providerName
                            + "），支持: deepseek / dashscope / openai-compatible");
        };
    }

    private ChatModel buildDeepSeek(ProviderProperties.ProviderSpec spec, String apiKey) {
        DeepSeekApi.Builder apiBuilder = DeepSeekApi.builder().apiKey(apiKey);
        if (spec.getBaseUrl() != null && !spec.getBaseUrl().isBlank()) {
            apiBuilder.baseUrl(spec.getBaseUrl());
        }
        return DeepSeekChatModel.builder()
                .deepSeekApi(apiBuilder.build())
                .defaultOptions(DeepSeekChatOptions.builder().model(spec.getModel()).build())
                .build();
    }

    private ChatModel buildDashScope(ProviderProperties.ProviderSpec spec, String apiKey) {
        DashScopeApi.Builder apiBuilder = DashScopeApi.builder().apiKey(apiKey);
        if (spec.getBaseUrl() != null && !spec.getBaseUrl().isBlank()) {
            apiBuilder.baseUrl(spec.getBaseUrl());
        }
        return DashScopeChatModel.builder()
                .dashScopeApi(apiBuilder.build())
                .defaultOptions(DashScopeChatOptions.builder().withModel(spec.getModel()).build())
                .build();
    }

    private ChatModel buildOpenAiCompatible(String providerName, ProviderProperties.ProviderSpec spec, String apiKey) {
        if (spec.getBaseUrl() == null || spec.getBaseUrl().isBlank()) {
            throw new IllegalStateException(
                    "openai-compatible 型 provider 必须配置 base-url（provider: " + providerName + "）");
        }
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder().apiKey(apiKey).baseUrl(spec.getBaseUrl()).build())
                .defaultOptions(OpenAiChatOptions.builder().model(spec.getModel()).build())
                .build();
    }
}
