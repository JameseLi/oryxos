package com.oryxos.tool.builtin;

import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 命令内置工具（技术方案 §6.2）：{@code shell} 执行命令，带超时与命令白名单（调用位）。
 *
 * <p>硬规矩（课件「三」）：方法<b>第一行</b>过 {@code Sandbox.enforce(SHELL_COMMAND)}；本体命令白名单 24 节交付，当前为 {@code
 * PermissiveSandbox} 临时装配。
 *
 * <p>超时定 30 秒：课件只说「带超时」未定值，取业界 Agent shell 工具的常见上限（Claude Code/Cursor
 * 同量级），以常量落地、不立新配置键（避免越本节交付物创建配置概念）。实现序：先 {@code waitFor}
 * 限时等退出、再读输出（顺序反了会被读阻塞吞掉超时语义）；进程输出超过管道缓冲时会触发超时兜底 而非读全量输出，属核心阶段简化。非零退出码不是异常（失败是业务语义），输出里如实带退出码，
 * 交由模型自行判断；进程无法启动/超时才抛异常走失败审计。
 */
@Component
public class ShellTools {

  static final long TIMEOUT_SECONDS = 30;

  private final Sandbox sandbox;
  private final String workspaceDir;
  private final long timeoutSeconds;

  @Autowired
  public ShellTools(
      Sandbox sandbox, @Value("${oryxos.workspace-dir:.oryxos}") String workspaceDir) {
    this(sandbox, workspaceDir, TIMEOUT_SECONDS);
  }

  /** 测试注入缝：超时秒数可缩短（超时语义的真实验证不必等 30 秒）。 */
  ShellTools(Sandbox sandbox, String workspaceDir, long timeoutSeconds) {
    this.sandbox = sandbox;
    this.workspaceDir = workspaceDir;
    this.timeoutSeconds = timeoutSeconds;
  }

  /** 执行一条命令：合并 stdout/stderr 返回；非零退出码如实附在输出里。 */
  @Tool(name = "shell", description = "执行一条 shell 命令并返回输出（带超时；受命令白名单约束）")
  public String shell(@ToolParam(description = "要执行的完整命令行，如 git status") String command) {
    sandbox.enforce(new SandboxAction(ActionType.SHELL_COMMAND, command));
    if (command == null || command.isBlank()) {
      throw new IllegalArgumentException("shell 命令不能为空");
    }
    Process process = start(command);
    boolean finished;
    try {
      finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw new IllegalStateException("shell 命令执行被中断: " + command, e);
    }
    if (!finished) {
      process.destroyForcibly();
      try {
        // 短暂回收：避免 Windows 上残留句柄占住工作区目录（孤儿子进程自然退出）
        process.waitFor(2, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException("shell 命令执行超过 " + timeoutSeconds + " 秒已被强制终止: " + command);
    }
    StringBuilder output = new StringBuilder();
    Charset consoleCharset = Charset.forName(System.getProperty("native.encoding", "UTF-8"));
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream(), consoleCharset))) {
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append('\n');
      }
    } catch (IOException e) {
      throw new IllegalStateException("shell 命令输出读取失败: " + e.getMessage(), e);
    }
    int exitCode = process.exitValue();
    if (exitCode != 0) {
      return output.append("（退出码 ").append(exitCode).append("，非零表示命令失败）").toString();
    }
    return output.toString();
  }

  /** 平台 shell 包装：Windows 走 cmd /c，类 Unix 走 sh -c；工作目录为 OryxOS 工作区。 */
  private Process start(String command) {
    boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    ProcessBuilder builder =
        windows
            ? new ProcessBuilder("cmd.exe", "/c", command)
            : new ProcessBuilder("sh", "-c", command);
    builder.directory(new File(workspaceDir));
    builder.redirectErrorStream(true);
    try {
      return builder.start();
    } catch (IOException e) {
      throw new IllegalStateException("shell 命令无法启动: " + e.getMessage(), e);
    }
  }
}
