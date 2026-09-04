package com.oryxos.tool.builtin;

import com.oryxos.tool.interaction.UserInteraction;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 交互内置工具（课件 6.3）：{@code ask_user} 处理中途向用户提问/请求确认，human-in-the-loop 的落点。
 *
 * <p>它跟其他工具不一样的地方：依赖「当前渠道能不能跟人交互」。怎么问、能不能问，交给装配时注入的 {@link UserInteraction} 实现决定（CLI 读终端 /
 * 无人值守明确报错），工具本身只把问题递进去、把回答带回来； 拿不到回答就抛异常，绝不静默卡住（课件 6.3 逐字）。
 */
@Component
public class InteractionTools {

  private final UserInteraction userInteraction;

  public InteractionTools(UserInteraction userInteraction) {
    this.userInteraction = userInteraction;
  }

  /** 向用户提问并等待回答；无人值守渠道明确报错（UnsupportedUserInteraction）。 */
  @Tool(name = "ask_user", description = "向用户提问并等待回答（用于危险操作确认、信息补齐；无人值守渠道会明确报错）")
  public String askUser(@ToolParam(description = "要问用户的问题") String question) {
    if (question == null || question.isBlank()) {
      throw new IllegalArgumentException("ask_user 的问题不能为空");
    }
    return userInteraction.ask(question);
  }
}
