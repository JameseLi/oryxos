package com.oryxos.cli.command;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine.Command;

/**
 * {@code oryxos status}（第 18 节轻命令）：看当前状态--工作区是否初始化、有几个 Profile、数据库文件是否存在。
 *
 * <p>不启动 Spring（不调模型不跑引擎，直接看文件系统）。
 */
@Command(mixinStandardHelpOptions = true, name = "status", description = "查看当前工作区状态")
public class StatusCommand implements Runnable {

  private static final String PROFILE_SUFFIX = ".yaml";

  @Override
  public void run() {
    Path root = CliWorkspace.root();
    System.out.println("工作区: " + root.toAbsolutePath());
    if (Files.notExists(root)) {
      System.out.println("尚未初始化，请先执行: oryxos init");
      return;
    }
    long profileCount = listProfiles().size();
    System.out.println("Profile 数: " + profileCount + listProfilesHint(profileCount));
    System.out.println(
        "数据库: "
            + (Files.exists(root.resolve("oryxos.db"))
                ? root.resolve("oryxos.db") + "（已存在）"
                : "尚无（首次重命令启动时自动创建）"));
  }

  private static String listProfilesHint(long count) {
    if (count == 0) {
      return "（暂无，可执行 oryxos profile create --name <名称>）";
    }
    return " -- " + String.join(", ", listProfiles());
  }

  private static List<String> listProfiles() {
    try (var stream = Files.list(CliWorkspace.profilesDir())) {
      return stream
          .filter(p -> p.toString().endsWith(PROFILE_SUFFIX))
          .map(StatusCommand::fileName)
          .map(
              name ->
                  name.endsWith(PROFILE_SUFFIX)
                      ? name.substring(0, name.length() - PROFILE_SUFFIX.length())
                      : name)
          .sorted()
          .toList();
    } catch (IOException | UncheckedIOException e) {
      return List.of();
    }
  }

  /** 文件名（根路径的 getFileName 为 null 时退回全路径--SpotBugs 判空口径）。 */
  private static String fileName(Path path) {
    Path name = path.getFileName();
    return name == null ? path.toString() : name.toString();
  }
}
