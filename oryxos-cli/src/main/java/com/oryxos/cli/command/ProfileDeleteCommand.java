package com.oryxos.cli.command;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** {@code oryxos profile delete}（第 18 节轻命令）：删除一份 Profile YAML。目标不存在时报清晰错误。 */
@Command(mixinStandardHelpOptions = true, name = "delete", description = "删除一个 Profile")
public class ProfileDeleteCommand implements Runnable {

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
      Files.delete(file);
      System.out.println("已删除 Profile: " + file);
    } catch (IOException | UncheckedIOException e) {
      System.out.println("删除 Profile 失败: " + e.getMessage());
    }
  }
}
