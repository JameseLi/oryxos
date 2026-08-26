package com.oryxos.cli.command;

import picocli.CommandLine.Command;

/** {@code oryxos provider}（第 18 节轻命令组）：Provider 全局层查看的分组入口。 */
@Command(
    mixinStandardHelpOptions = true,
    name = "provider",
    description = "查看 Provider 全局层",
    subcommands = ProviderListCommand.class)
public class ProviderCommand implements Runnable {

  @Override
  public void run() {
    System.out.println("用法: oryxos provider list（--help 看详情）");
  }
}
