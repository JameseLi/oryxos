package com.oryxos.channel.cli;

import com.oryxos.core.agent.AgentService;
import com.oryxos.core.session.Session;
import com.oryxos.core.session.SessionManager;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.springframework.stereotype.Component;

/**
 * CLI Channel（第 18 节，技术方案 8.4）：{@code oryxos chat} 命令的实现。
 *
 * <p>读 stdin 写 stdout 实现交互式对话，维护当前 Session，每次输入调 {@link AgentService#process}（三源归一的 统一入口，里面跑的就是
 * ReAct 循环），支持 {@code /quit} 退出。
 *
 * <p>CLI 只管进出不管干活（课件「第一」）：不调模型、不执行工具、不想--「/quit 是它唯一自己判断的事」。 会话按三元组取用：channel 固定 {@code "cli"}、user
 * 取当前系统用户、profile 由命令行传入；id 拼接在 SessionManager 内部，这里不感知。
 */
@Component
public class CliChannel {

  private final AgentService agentService;
  private final SessionManager sessionManager;

  public CliChannel(AgentService agentService, SessionManager sessionManager) {
    this.agentService = agentService;
    this.sessionManager = sessionManager;
  }

  /** 进入交互循环，直到用户输入 /quit 或标准输入关闭（EOF）。 */
  public void chat(String profileName) {
    Session session = sessionManager.getOrCreate("cli", currentUser(), profileName);
    try (Scanner in = new Scanner(System.in, StandardCharsets.UTF_8)) {
      while (in.hasNextLine()) { // EOF（管道/Ctrl+D）时正常结束，不抛栈
        System.out.print("> ");
        String line = in.nextLine();
        if ("/quit".equals(line.trim())) {
          break; // CLI 唯一"自己判断"的逻辑
        }
        if (line.isBlank()) {
          continue; // 空行不发给引擎，省一次无意义的 LLM 调用
        }
        String reply;
        try {
          reply = agentService.process(session, line); // 交给引擎，CLI 到这儿就撒手
        } catch (RuntimeException e) {
          // 不吞：失败对用户可见（stdout），但不打断交互循环
          System.out.println("处理失败: " + e.getMessage());
          continue;
        }
        System.out.println(reply);
      }
    }
    System.out.println("会话已结束（session_id: " + session.id() + "）");
  }

  private static String currentUser() {
    return System.getProperty("user.name", "cli-user");
  }
}
