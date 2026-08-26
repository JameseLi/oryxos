package com.oryxos.cli.command;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code oryxos profile create}（第 18 节轻命令）：从模板生成一份新 Profile YAML。
 *
 * <p>目标已存在时报错拒绝覆盖（不静默丢用户已有 Profile--Edge Case 裁定）。
 */
@Command(
    mixinStandardHelpOptions = true,
    name = "create",
    description = "创建一个新 Profile（从模板生成 YAML）")
public class ProfileCreateCommand implements Runnable {

  @Option(names = "--name", required = true, description = "Profile 名称（同名 YAML 文件名）")
  private String name;

  @Override
  public void run() {
    Path file = CliWorkspace.profilesDir().resolve(name + ".yaml");
    if (Files.exists(file)) {
      System.out.println("Profile 已存在，拒绝覆盖: " + file);
      return;
    }
    try {
      Files.createDirectories(CliWorkspace.profilesDir());
      Files.writeString(file, template(name));
      System.out.println("已创建 Profile: " + file);
      System.out.println(
          "编辑 provider 段（需与全局层 oryxos.providers 对齐）后即可 oryxos chat --profile " + name);
    } catch (IOException e) {
      throw new UncheckedIOException("创建 Profile 失败: " + file, e);
    }
  }

  private static String template(String name) {
    return "# Profile: "
        + name
        + "（oryxos profile create 生成，可自行编辑）。\n"
        + "# provider.name 必须能在全局层 oryxos.providers（application.yaml）里找到同名项。\n"
        + "name: "
        + name
        + "\n"
        + "description: "
        + name
        + " Agent\n"
        + "identity:\n"
        + "  agent_name: "
        + name
        + "\n"
        + "  prompt: 你是 "
        + name
        + "，请完成你的职责。\n"
        + "provider:\n"
        + "  name: deepseek\n"
        + "  model: deepseek-chat\n"
        + "tools: []\n";
  }
}
