package com.oryxos.core.chat;

import com.oryxos.core.profile.Profile;
import com.oryxos.core.prompt.Prompt;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * LLM 调用端口（第 17 节，spec R2：DIP 破 provider -> core 的 Maven 依赖方向）。
 *
 * <p>ReAct 循环（core）每转一圈都要调一次大模型，而 {@code ProviderService} 在 provider 模块（依赖方向 provider ->
 * core，不可反转）--故 core 立本端口，provider 侧实现之。签名与第 16 节 {@code ProviderService.chat} 逐字同构： {@code
 * sessionId} 供 {@code llm_calls} 审计按会话关联，不传这一列就没法写。
 */
public interface ChatInvoker {

  /**
   * 发起一次 LLM 调用（可能带回工具调用请求，执行不在此层）。
   *
   * @param sessionId 所属会话（审计按会话关联；{@code null} 表示无会话绑定）
   * @param profile 本次 Agent 的 Profile（provider/model/temperature）
   * @param prompt 本次要发给模型的消息 + 本轮可用工具清单
   */
  ChatResponse chat(String sessionId, Profile profile, Prompt prompt);
}
