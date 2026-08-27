package com.oryxos.cli.command;

import com.oryxos.channel.cli.CliChannel;
import com.oryxos.cli.SpringLauncher;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code oryxos chat}（第 18 节重命令，课件骨架）：在终端里和 Agent 交互式对话。
 *
 * <p>重命令：要跑引擎，所以启动 Spring 上下文（课件「第二」分流判断标准）；交互本体在 {@link CliChannel}（读输入 -> 交引擎 -> 打印结果，{@code
 * /quit} 退出）。CLI 到 process 这一步就撒手，不想、不调模型、不执行工具。
 */
@Command(mixinStandardHelpOptions = true, name = "chat", description = "在终端里和 Agent 交互式对话")
public class ChatCommand implements Runnable {

  @Option(names = "--profile", defaultValue = "default", description = "Agent Profile 名")
  String profileName;

  @Override
  public void run() {
    try (ConfigurableApplicationContext context = SpringLauncher.start()) {
      context.getBean(CliChannel.class).chat(profileName);
    }
  }
}
