package com.oryxos.tool.notify;

import java.util.Map;

/**
 * 通知目标（课件「三」）：渠道类型 + 一份渠道特定配置。
 *
 * <p>为什么只有这两样：具体是 webhook 地址还是别的认证信息，由实现类自己去解释--接口与目标形态里不出现 任何一档实现特有的词， 这是「接口表达意图，不表达实现」的第一个落点（第
 * 23/24 节 Sandbox 是同一套设计习惯的再次亮相）。
 *
 * <p>扩展阶段新增渠道（如飞书富文本）时，渠道特有字段（签名、token）都收在 {@code config} 里， 由各自的 {@link NotifyChannelAdapter}
 * 实现解释，本类型零改动。
 */
public record NotifyTarget(String channelType, Map<String, String> config) {

  /** 防御拷贝：config 不可变，避免外部修改穿透（口径照抄 Profile.ChannelRef，SpotBugs EI_EXPOSE_REP）。 */
  public NotifyTarget {
    config = config == null ? Map.of() : Map.copyOf(config);
  }
}
