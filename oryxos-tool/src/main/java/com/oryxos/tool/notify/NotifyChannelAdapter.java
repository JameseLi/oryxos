package com.oryxos.tool.notify;

/**
 * 出站通知渠道适配器（课件「三」接口骨架逐字保真）。
 *
 * <p>与入站 ChannelAdapter 的关系：方向相反、分开建模。入站解决「什么触发 Agent 开始跑」，本接口解决「Agent 跑完把结果送到 哪」--同一个企业微信群可能同时是某
 * Agent 的入站 Channel、又是另一个 Agent 的出站通知目标，语义不同不合并成一个抽象。
 *
 * <p>接口中立性（验收人工项的思维练习）：换成任何渠道的专用实现（企业微信官方 SDK、飞书卡片）， {@code send(NotifyTarget, String)}
 * 这个签名一个字都不需要改--核心阶段只在接口后挂 {@link WebhookNotifyAdapter} 一档实现， 以后加新渠道只新增实现类，不改接口、不改调用方。
 */
public interface NotifyChannelAdapter {

  /** 把一条内容送到指定通知目标。HTTP 错误等发送失败以异常上抛，由调用方统一处置（不静默吞掉）。 */
  void send(NotifyTarget target, String content);
}
