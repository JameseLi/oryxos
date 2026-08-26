package com.oryxos.cli.command;

import picocli.CommandLine.Command;

/**
 * {@code oryxos serve}（第 18 节桩命令）：启动 Web Service 的运行模式。
 *
 * <p>Web Service（REST API + 管理台）是第 26 节交付物；本命令先立位不干活--诚实提示后正常退出，不起 Spring、不假装成功。
 */
@Command(mixinStandardHelpOptions = true, name = "serve", description = "启动 Web Service（第 26 节交付）")
public class ServeCommand implements Runnable {

  @Override
  public void run() {
    System.out.println("Web Service 将于第 26 节交付（oryxos serve：启动 REST API 服务，供业务系统调）。");
    System.out.println("当前可用入口: oryxos chat");
  }
}
