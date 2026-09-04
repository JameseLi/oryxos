package com.oryxos.tool.interaction;

/**
 * 无人值守实现（课件 6.3）：定时任务、Web 无状态调用等没有人在另一端等着回答的渠道。
 *
 * <p>语义（课件 6.3 逐字）：直接报「不支持交互」，绝不静默卡住--静默卡死会让定时任务永远不返回， 明确报错则沿 ToolExecutor 失败路径回填模型/审计，行为可预期。
 */
public class UnsupportedUserInteraction implements UserInteraction {

  @Override
  public String ask(String question) {
    throw new IllegalStateException("当前渠道不支持交互（ask_user 需要有人在终端旁回答；无人值守渠道不应调用该工具）");
  }
}
