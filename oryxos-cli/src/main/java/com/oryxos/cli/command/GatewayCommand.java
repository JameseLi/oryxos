package com.oryxos.cli.command;

import picocli.CommandLine.Command;

/**
 * {@code oryxos gateway}（第 18 节桩命令）：守护进程运行模式（同时挂多个 Channel）。
 *
 * <p>多 Channel 常驻接入（含扩展阶段 IM Channel）依赖后续节的 Channel 体系交付；本命令先立位不干活-- 诚实提示后正常退出。
 */
@Command(mixinStandardHelpOptions = true, name = "gateway", description = "启动守护进程，同时挂多个通道（后续节交付）")
public class GatewayCommand implements Runnable {

  @Override
  public void run() {
    System.out.println("多通道守护进程将于后续节交付（oryxos gateway：同时挂多个 Channel 的常驻模式）。");
    System.out.println("当前可用入口: oryxos chat");
  }
}
