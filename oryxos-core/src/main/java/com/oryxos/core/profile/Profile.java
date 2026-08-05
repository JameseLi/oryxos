package com.oryxos.core.profile;

import java.util.List;
import java.util.Map;

/**
 * Profile — Agent 的运行时宿主配置（第 16 节首个消费 Profile 的能力）。
 *
 * <p>承载课件「第零步」点名的全部字段：{@code name/description/identity/provider(name/model/temperature)/
 * tools/skills/mcp_servers/channels/notify_channels/schedules/bootstrap/settings}。后续每节用到哪个字段就取哪个
 * 字段；类本身本节就建全。由 {@link ProfileLoader} 从 {@code .oryxos/profiles/*.yaml} 解析、逐个校验、 注册进 {@link
 * ProfileRegistry}。
 *
 * <p>本节只校验「{@code provider.name} 能在全局层 {@code oryxos.providers} 找到同名项」一条；其余字段
 * 的校验规则留给后续各节各自补（课件「第零步」）。
 */
public record Profile(
    String name,
    String description,
    Identity identity,
    Provider provider,
    List<String> tools,
    List<String> skills,
    List<String> mcpServers,
    List<ChannelRef> channels,
    List<ChannelRef> notifyChannels,
    List<Schedule> schedules,
    Bootstrap bootstrap,
    Settings settings) {

  public Profile {
    tools = tools == null ? List.of() : List.copyOf(tools);
    skills = skills == null ? List.of() : List.copyOf(skills);
    mcpServers = mcpServers == null ? List.of() : List.copyOf(mcpServers);
    channels = channels == null ? List.of() : List.copyOf(channels);
    notifyChannels = notifyChannels == null ? List.of() : List.copyOf(notifyChannels);
    schedules = schedules == null ? List.of() : List.copyOf(schedules);
  }

  /** Agent 身份：人格名 + 系统提示骨架。 */
  public record Identity(String agentName, String prompt) {}

  /** 接哪家 provider、用哪个 model、什么温度。 */
  public record Provider(String name, String model, Double temperature) {}

  /** 通道/通知通道引用：类型 + 渠道特定配置（如 webhook url）。 */
  public record ChannelRef(String type, Map<String, Object> config) {
    /** 防御拷贝：config 不可变，避免外部修改穿透（SpotBugs EI_EXPOSE_REP）。 */
    public ChannelRef {
      config = config == null ? Map.of() : Map.copyOf(config);
    }
  }

  /** 定时触发声明：cron + 时区 + 到点发给 Agent 的消息（§25 节消费）。 */
  public record Schedule(String cron, String zone, String message) {}

  /** Bootstrap 文件引用（§17 ContextLoader 消费）：AGENTS.md / SOUL.md / USER.md。 */
  public record Bootstrap(List<String> files) {

    public Bootstrap {
      files = files == null ? List.of() : List.copyOf(files);
    }
  }

  /** 运行时调参：迭代上限、历史轮数截断等（§17/§25 节消费）。 */
  public record Settings(Integer maxIterations, Integer maxHistoryTurns) {}
}
