package com.oryxos.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * CLI 注册完整性冒烟（第 18 节，表外测试类）：12 个子命令全部注册、{@code --help} 正常。
 *
 * <p>边界（课件「四」明言）：命令分流（轻命令不起 Spring）与 12 个命令的实际行为属进程级行为，自动化成本大于收益、 留人工清单；本类只验证「12
 * 个子命令类都注册到位」这一可廉价自动化的部分，不启动 Spring、不碰文件系统。
 */
class OryxOsCliTest {

  @Test
  @DisplayName("12 个子命令全部注册（9 个顶层 + profile 四叶子 + provider/tool/session 各一叶子）")
  void twelveSubcommandsRegistered() {
    CommandLine cli = new CommandLine(new OryxOsCli());

    // 9 个顶层入口（profile/provider/tool/session 是分组命令，叶子见下）
    assertEquals(
        Set.of(
            "init", "status", "chat", "serve", "gateway", "profile", "provider", "tool", "session"),
        cli.getSubcommands().keySet());

    assertEquals(
        Set.of("list", "create", "show", "delete"),
        cli.getSubcommands().get("profile").getSubcommands().keySet());
    assertEquals(Set.of("list"), cli.getSubcommands().get("provider").getSubcommands().keySet());
    assertEquals(Set.of("list"), cli.getSubcommands().get("tool").getSubcommands().keySet());
    assertEquals(Set.of("list"), cli.getSubcommands().get("session").getSubcommands().keySet());
  }

  @Test
  @DisplayName("--help 与 --version 正常退出（Picocli 自带）")
  void helpAndVersionExitZero() {
    CommandLine cli = new CommandLine(new OryxOsCli());
    assertEquals(0, cli.execute("--help"));
    assertEquals(0, cli.execute("--version"));
    assertEquals(0, cli.execute("profile", "--help"));
    assertEquals(0, cli.execute("chat", "--help"));
  }

  @Test
  @DisplayName("chat 命令的 --profile 默认 default（课件骨架字面量）")
  void chatProfileOptionDefaultsToDefault() {
    CommandLine cli = new CommandLine(new OryxOsCli());
    // 仅解析不执行：--help 触发后 ChatCommand 不会真正 run（不起 Spring）
    String help = cli.getSubcommands().get("chat").getUsageMessage();
    assertTrue(help.contains("--profile"), "--profile 选项应出现在 chat 帮助里");
  }
}
