package com.oryxos.cli;

import com.oryxos.cli.command.ChatCommand;
import com.oryxos.cli.command.GatewayCommand;
import com.oryxos.cli.command.InitCommand;
import com.oryxos.cli.command.ProfileCommand;
import com.oryxos.cli.command.ProviderCommand;
import com.oryxos.cli.command.ServeCommand;
import com.oryxos.cli.command.SessionCommand;
import com.oryxos.cli.command.StatusCommand;
import com.oryxos.cli.command.ToolCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * OryxOS 命令行入口（第 18 节，技术方案 8.7）：整个程序的 main 函数，注册 12 个子命令。
 *
 * <p>命令按轻重分流（课件「第二」）：轻命令（init、profile 四连、provider list、status）不启动 Spring、
 * 直接文件操作秒回；重命令（chat、serve、gateway、session list、tool list）才启动 Spring 上下文（{@link SpringLauncher}）。
 * fat JAR 的主类即本类（boot 模块 spring-boot-maven-plugin 的 mainClass）。
 */
@Command(
    name = "oryxos",
    version = "1.0.0-SNAPSHOT",
    mixinStandardHelpOptions = true,
    description = "OryxOS - 企业级 Agent OS 命令行入口",
    subcommands = {
      InitCommand.class,
      StatusCommand.class,
      ChatCommand.class,
      ServeCommand.class,
      GatewayCommand.class,
      ProfileCommand.class,
      ProviderCommand.class,
      ToolCommand.class,
      SessionCommand.class
    })
public class OryxOsCli implements Runnable {

  @Override
  public void run() {
    // 无子命令时给出用法帮助（Picocli 自带 usage 渲染）
    CommandLine.usage(this, System.out);
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new OryxOsCli()).execute(args);
    System.exit(exitCode);
  }
}
