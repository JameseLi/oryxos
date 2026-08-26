package com.oryxos.cli.command;

import picocli.CommandLine.Command;

/** {@code oryxos tool}（第 18 节轻/重命令组）：工具注册表查看的分组入口。 */
@Command(
    mixinStandardHelpOptions = true,
    name = "tool",
    description = "查看工具注册表",
    subcommands = ToolListCommand.class)
public class ToolCommand implements Runnable {

  @Override
  public void run() {
    System.out.println("用法: oryxos tool list（--help 看详情）");
  }
}
