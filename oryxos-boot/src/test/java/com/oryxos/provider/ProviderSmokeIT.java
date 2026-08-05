package com.oryxos.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oryxos.boot.OryxOsApplication;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.prompt.Prompt;
import com.oryxos.storage.repository.LlmCallRepository;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Provider 集成冒烟（课件「验收 harness」）：真调一次模型。
 *
 * <p>跑法（课件）：{@code DEEPSEEK_API_KEY=xxx mvn test -Dgroups=integration -pl oryxos-boot} （依赖真 key +
 * 真网络，{@code success=true} 写进 {@code llm_calls}）。日常 {@code mvn test} / CI 经 {@code
 * excludedGroups=integration} 跳过本类（{@link Tag}）。无真 key 时 {@link Assumptions#assumeTrue}
 * 中止（不算绿也不算红——标记为「跳过」）。
 *
 * <p>断言：拿到非空 {@link ChatResponse}（含至少一条 generation 或非空文本）且 {@code llm_calls} 多了一条 {@code
 * success=true} 的记录。
 */
@Tag("integration")
@SpringBootTest(classes = OryxOsApplication.class)
class ProviderSmokeIT {

  @Autowired ProviderService providerService;
  @Autowired LlmCallRepository llmCallRepository;

  @Test
  @DisplayName("真调一次 DeepSeek，断言拿到非空响应且 llm_calls 多一条 success=true 记录")
  void smokeRealDeepSeekCall() {
    String key = System.getenv("DEEPSEEK_API_KEY");
    Assumptions.assumeTrue(key != null && !key.isBlank(), "未注入 DEEPSEEK_API_KEY 环境变量，跳过真模型冒烟");

    long before = llmCallRepository.findBySessionId("smoke-1").size();
    Profile profile =
        new Profile(
            "weather-agent",
            "每日天气冒烟",
            null,
            new Profile.Provider("deepseek", "deepseek-chat", 0.7),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null);
    Prompt prompt = new Prompt(List.of(new UserMessage("用一句话回答：1 加 1 等于几？")), List.of());

    ChatResponse response = providerService.chat("smoke-1", profile, prompt);

    assertNotNull(response, "应拿到非空 ChatResponse");
    var results = response.getResults();
    assertTrue(
        results == null || !results.isEmpty() || response.getMetadata() != null,
        "响应应至少含一条 generation 或 metadata");

    var after = llmCallRepository.findBySessionId("smoke-1");
    assertEquals(before + 1, after.size(), "llm_calls 应多一条本次调用记录");
    var last = after.get(after.size() - 1);
    assertTrue(Boolean.TRUE.equals(last.getSuccess()), "这次调用 success 应为 true");
    assertFalse(last.getProvider() == null || last.getProvider().isBlank(), "provider 列必须落库");
  }
}
