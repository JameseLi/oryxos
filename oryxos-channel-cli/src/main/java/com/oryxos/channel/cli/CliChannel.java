package com.oryxos.channel.cli;

import com.oryxos.core.agent.AgentService;
import com.oryxos.core.tool.ToolProgressListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * CLI 对话渠道：REPL 读 stdin、写 stdout（FR-011 一行式工具进度渲染）。 三个触发源之一（"人推"），与 Web/Scheduler 共用同一个
 * AgentService.process。
 */
@Component
public class CliChannel {

  private final AgentService agentService;

  public CliChannel(AgentService agentService) {
    this.agentService = agentService;
  }

  /**
   * 运行 REPL 直到用户退出。
   *
   * @return 会话 id（供提示与 --session 续聊）
   */
  public String runRepl(String agentName, String sessionId) {
    PrintStream out = System.out;
    PrintStream err = System.err;
    // FR-013 启动预检：Agent/Provider/密钥问题在进 REPL 前暴露
    try {
      agentService.preflight(agentName);
    } catch (IllegalArgumentException | IllegalStateException e) {
      err.println("⚠️ " + e.getMessage());
      throw e;
    }
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    ToolProgressListener consoleProgress = consoleProgress(out);

    out.println(
        "OryxOS chat — agent: "
            + agentName
            + (sessionId == null ? "（新会话）" : "（续聊 " + sessionId + "）"));
    out.println("输入消息开始对话，exit/quit 或 Ctrl-D 退出。");
    String currentSessionId = sessionId;
    try {
      while (true) {
        out.print("你> ");
        out.flush();
        String line = reader.readLine();
        if (line == null) {
          break;
        }
        // 显式 Locale.ROOT 做大小写折叠（findsecbugs IMPROPER_UNICODE）
        String command = line.strip().toLowerCase(Locale.ROOT);
        if ("exit".equals(command) || "quit".equals(command)) {
          break;
        }
        if (line.isBlank()) {
          out.println("（空消息已忽略，请输入内容）");
          continue;
        }
        try {
          AgentService.AgentReply reply =
              agentService.process(agentName, currentSessionId, line, "cli", consoleProgress);
          currentSessionId = reply.sessionId();
          out.println("🤖 " + reply.text());
        } catch (AgentService.AgentInvocationException e) {
          err.println("⚠️ " + e.getMessage() + "（会话保留，可继续提问）");
        } catch (IllegalArgumentException | IllegalStateException e) {
          // 启动级错误（Agent 不存在/provider 未配置/密钥缺失）：提示后终止
          err.println("⚠️ " + e.getMessage());
          throw e;
        }
      }
    } catch (IOException e) {
      err.println("⚠️ 读取输入失败: " + e.getMessage());
    }
    out.println(
        "会话已结束"
            + (currentSessionId == null
                ? ""
                : "（session: " + currentSessionId + "，可用 --session 续聊）"));
    return currentSessionId;
  }

  /** FR-011 一行式进度：🔧 调用工具 / ✅ 完成(x.xs) / ❌ 失败。 */
  static ToolProgressListener consoleProgress(PrintStream out) {
    return new ToolProgressListener() {
      @Override
      public void onToolStart(String toolName, String argumentsJson) {
        out.println("🔧 调用工具: " + toolName + " ...");
      }

      @Override
      public void onToolEnd(
          String toolName, boolean success, long durationMs, String errorMessage) {
        if (success) {
          out.printf("✅ 完成 (%.1fs)%n", durationMs / 1000.0);
        } else {
          out.println("❌ 失败: " + errorMessage);
        }
      }
    };
  }
}
