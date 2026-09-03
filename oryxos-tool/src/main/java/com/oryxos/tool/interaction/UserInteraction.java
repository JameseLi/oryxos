package com.oryxos.tool.interaction;

/**
 * 人机交互入口（课件 6.3，接口先行）：表达「向当前渠道的用户问一句话、拿回回答」的意图。
 *
 * <p>为什么要抽接口：{@code ask_user} 依赖「当前渠道能不能跟人交互」--CLI 场景可以读终端， 但定时任务、Web 无状态调用根本没有一个人在另一端等着回答。这是与 19 节
 * {@code NotifyChannelAdapter}、 本节 {@code Sandbox} 同一个「接口表达意图、实现按渠道挂」的套路。
 *
 * <p>契约（课件 6.3 逐字）：拿不到回答就抛异常，绝不静默卡住。核心阶段两档实现： {@link ConsoleUserInteraction}（读终端的一行）与 {@link
 * UnsupportedUserInteraction} （无人值守渠道直接报「不支持交互」）。
 */
public interface UserInteraction {

  /**
   * 问一个问题并等待回答。
   *
   * @return 用户给出的回答原文
   * @throws IllegalStateException 当前渠道无法交互（无人值守），或交互流被中断
   */
  String ask(String question);
}
