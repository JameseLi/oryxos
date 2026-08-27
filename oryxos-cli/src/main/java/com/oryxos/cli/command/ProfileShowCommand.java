package com.oryxos.cli.command;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** {@code oryxos profile show}（第 18 节轻命令）：打印一份 Profile 的 YAML 原文。目标不存在时报清晰错误。 */
@Command(mixinStandardHelpOptions = true, name = "show", description = "查看一个 Profile 的配置内容")
public class ProfileShowCommand implements Runnable {

  @Option(names = "--name", required = true, description = "Profile 名称")
  private String name;

  @Override
  public void run() {
    Path file = CliWorkspace.profilesDir().resolve(name + ".yaml");
    if (Files.notExists(file)) {
      System.out.println("Profile 不存在: " + file);
      return;
    }
    try {
      System.out.print(Files.readString(file));
    } catch (IOException | UncheckedIOException e) {
      System.out.println("读取 Profile 失败: " + e.getMessage());
    }
  }
}
