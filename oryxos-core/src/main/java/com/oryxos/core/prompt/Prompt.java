package com.oryxos.core.prompt;

import com.oryxos.core.tool.OryxTool;
import java.util.List;
import org.springframework.ai.chat.messages.Message;

/**
 * OryxOS 域 Prompt（最小形态，第 16 节交付）。
 *
 * <p>承载 {@code ProviderService.chat(sessionId, Profile, Prompt)} 调用所需的两样输入——本次要发给模型的 消息列表 +
 * 本轮可用的工具清单；不依赖 Spring AI {@code org.springframework.ai.chat.prompt.Prompt}，是 OryxOS 自己的域类型（spec
 * R1）。后续 §17 {@code PromptBuilder} 组装本类型后传入 Provider；§17/§20 扩展 本类型若需改公共成员由对应节课件明列「改造点」裁定。
 */
public record Prompt(List<Message> messages, List<OryxTool> availableTools) {

  /** 防御拷贝：消息与工具列表均不可变，避免外部修改穿透进 Prompt（EI_EXPOSE_REP2）。 */
  public Prompt {
    messages = messages == null ? List.of() : List.copyOf(messages);
    availableTools = availableTools == null ? List.of() : List.copyOf(availableTools);
  }
}
