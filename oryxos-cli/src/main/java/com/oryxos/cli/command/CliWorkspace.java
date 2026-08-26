package com.oryxos.cli.command;

import java.nio.file.Path;

/**
 * 轻命令的工作区定位辅助（第 18 节，包私有）。
 *
 * <p>轻命令不启动 Spring，拿不到 {@code oryxos.workspace-dir} 配置项，口径与重命令启动类/boot 主类一致： 先看系统属性，缺省 {@code
 * .oryxos}。重命令链路里该属性由 application.yaml 提供，轻命令靠系统属性覆盖（同一键名，行为对齐）。
 */
final class CliWorkspace {

  private CliWorkspace() {}

  /** 工作区根目录（默认 {@code .oryxos}，可用系统属性 oryxos.workspace-dir 覆盖）。 */
  static Path root() {
    return Path.of(System.getProperty("oryxos.workspace-dir", ".oryxos"));
  }

  /** Profile 全局层目录（第 16 节裁定：Profile 来自 {@code <workspace>/profiles/*.yaml}）。 */
  static Path profilesDir() {
    return root().resolve("profiles");
  }
}
