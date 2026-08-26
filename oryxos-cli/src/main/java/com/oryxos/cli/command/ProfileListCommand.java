package com.oryxos.cli.command;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;

/**
 * {@code oryxos profile list}（第 18 节轻命令，课件骨架示例）：列出 {@code .oryxos/profiles/} 下的 Profile。
 *
 * <p>它只是列个目录，没必要为这点事等 Spring 启动几秒（课件「第二」：轻命令不启动 Spring，秒回）。
 */
@Command(mixinStandardHelpOptions = true, name = "list", description = "列出所有 Profile")
public class ProfileListCommand implements Runnable {

  @Override
  public void run() {
    if (Files.notExists(CliWorkspace.profilesDir())) {
      System.out.println("profiles 目录不存在，请先执行: oryxos init");
      return;
    }
    try (var stream = Files.list(CliWorkspace.profilesDir())) {
      var names =
          stream
              .filter(p -> p.toString().endsWith(".yaml"))
              .map(ProfileListCommand::fileName)
              .sorted()
              .toList();
      if (names.isEmpty()) {
        System.out.println("暂无 Profile（可执行 oryxos profile create --name <名称>）");
        return;
      }
      names.forEach(System.out::println);
    } catch (IOException | UncheckedIOException e) {
      System.out.println("读取 profiles 目录失败: " + e.getMessage());
    }
  }

  /** 文件名（根路径的 getFileName 为 null 时退回全路径--SpotBugs 判空口径）。 */
  private static String fileName(Path path) {
    Path name = path.getFileName();
    return name == null ? path.toString() : name.toString();
  }
}
