package com.oryxos.cli.command;

import picocli.CommandLine.Command;

/**
 * {@code oryxos profile}（第 18 节轻命令组）：Profile 管理的分组入口，四个叶子命令见 §8.7。
 *
 * <p>轻命令不启动 Spring：直接读写 {@code .oryxos/profiles/*.yaml}（第 16 节裁定的 Profile 全局层）。
 */
@Command(
    mixinStandardHelpOptions = true,
    name = "profile",
    description = "管理 Agent Profile（list / create / show / delete）",
    subcommands = {
      ProfileListCommand.class,
      ProfileCreateCommand.class,
      ProfileShowCommand.class,
      ProfileDeleteCommand.class
    })
public class ProfileCommand implements Runnable {

  @Override
  public void run() {
    // 无子命令时给出用法提示（叶子命令才是干活的人）
    System.out.println("用法: oryxos profile <list|create|show|delete> ...（--help 看详情）");
  }
}
