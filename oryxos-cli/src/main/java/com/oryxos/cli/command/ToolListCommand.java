package com.oryxos.cli.command;

import com.oryxos.cli.SpringLauncher;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;

/**
 * {@code oryxos tool list}（第 18 节重命令）：列出当前注册表里的工具。
 *
 * <p>重命令：注册表是 Spring 管的 Bean（要起上下文才能拿到）。Tool 体系（内置 9 工具 + MCP + 注解档）是第 20 节交付， 当前注册表为空属预期，输出如实注明。
 */
@Command(mixinStandardHelpOptions = true, name = "list", description = "列出当前注册的工具")
public class ToolListCommand implements Runnable {

  @Override
  public void run() {
    try (ConfigurableApplicationContext context = SpringLauncher.start()) {
      var tools = context.getBean(ToolRegistry.class).all();
      if (tools.isEmpty()) {
        System.out.println("暂无注册工具（内置 Tool 体系第 20 节交付）");
        return;
      }
      for (OryxTool tool : tools) {
        System.out.printf("%-16s %s%n", tool.name(), tool.description());
      }
    }
  }
}
