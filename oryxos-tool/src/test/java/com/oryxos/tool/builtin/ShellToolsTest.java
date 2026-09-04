package com.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oryxos.tool.sandbox.PermissiveSandbox;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxViolationException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link ShellTools} 验收 harness：正常能跑通（返回命令输出）+ 越界会被拦 + 超时语义。
 *
 * <p>时间裁定：越界守点以调用位语义承载（拒绝型 Sandbox 首行抛 {@link SandboxViolationException}）， 真实命令白名单归 24 节。
 */
class ShellToolsTest {

  @TempDir Path workspace;

  @Test
  @DisplayName("shell 正常能跑通：返回命令输出（echo 跨平台）")
  void shellReturnsCommandOutput() {
    ShellTools tools = new ShellTools(new PermissiveSandbox(), workspace.toString());

    String result = tools.shell("echo hello-oryxos");

    assertThat(result).contains("hello-oryxos");
  }

  @Test
  @DisplayName("命中白名单外的命令应被拦下：进程根本不启动")
  void shellOutsideSandboxIsRejected() {
    ShellTools tools = new ShellTools(denying(), workspace.toString());

    assertThatThrownBy(() -> tools.shell("rm -rf /")).isInstanceOf(SandboxViolationException.class);
  }

  @Test
  @DisplayName("空命令明确报错")
  void shellRejectsBlankCommand() {
    ShellTools tools = new ShellTools(new PermissiveSandbox(), workspace.toString());

    assertThatThrownBy(() -> tools.shell("   ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("非零退出码是业务语义：输出如实带退出码，不抛异常")
  void shellReportsNonZeroExitCodeInOutput() {
    ShellTools tools = new ShellTools(new PermissiveSandbox(), workspace.toString());
    boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");

    String result = tools.shell(windows ? "cmd /c exit 3" : "exit 3");

    assertThat(result).contains("3");
  }

  @Test
  @DisplayName("超时语义：超过时限的命令被强制终止并明确报错")
  void shellTimeoutKillsLongRunningCommand() throws Exception {
    boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
    // 自管工作区：被强杀进程的孤儿子进程可能短时占住目录句柄，不用 @TempDir（Windows 删除会失败）
    Path selfManaged = Files.createTempDirectory("oryxos-shell-timeout-test");
    ShellTools tools = new ShellTools(new PermissiveSandbox(), selfManaged.toString(), 1);
    String slowCommand = windows ? "ping -n 5 127.0.0.1" : "sleep 5";

    assertThatThrownBy(() -> tools.shell(slowCommand))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("强制终止");
  }

  @Test
  @DisplayName("命令在 OryxOS 工作区内执行（工作目录即注入的工作区）")
  void shellRunsInsideWorkspaceDirectory() {
    ShellTools tools = new ShellTools(new PermissiveSandbox(), workspace.toString());

    tools.shell("echo wrote-from-shell > out.txt");

    assertThat(Files.exists(workspace.resolve("out.txt"))).isTrue();
  }

  private static Sandbox denying() {
    return action -> {
      throw new SandboxViolationException("动作被白名单拒绝: " + action.target());
    };
  }
}
