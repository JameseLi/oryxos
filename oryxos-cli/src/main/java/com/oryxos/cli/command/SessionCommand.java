package com.oryxos.cli.command;

import picocli.CommandLine.Command;

/** {@code oryxos session}（第 18 节重命令组）：会话查看的分组入口。 */
@Command(
    mixinStandardHelpOptions = true,
    name = "session",
    description = "查看会话",
    subcommands = SessionListCommand.class)
public class SessionCommand implements Runnable {

  @Override
  public void run() {
    System.out.println("用法: oryxos session list（--help 看详情）");
  }
}
