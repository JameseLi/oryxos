package com.oryxos.tool.interaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 终端实现（课件 6.3）：从标准输入读一行回答。
 *
 * <p>与 {@code chat} 循环读终端是同一个 stdin：ReAct 循环是同步执行的（原则七）， {@code ask_user} 那一步和对话读行不会真的并发争抢--Agent
 * 问、用户在同一个终端答，一问一答顺序发生。
 */
public class ConsoleUserInteraction implements UserInteraction {

  @Override
  public String ask(String question) {
    System.out.println(question);
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    try {
      String line = reader.readLine();
      if (line == null) {
        // stdin 已关闭（无人真正在场）：明确报错，绝不静默把 null 当回答
        throw new IllegalStateException(
            "stdin 已关闭，ask_user 拿不到回答（无人值守场景不应使用 ConsoleUserInteraction）");
      }
      return line;
    } catch (IOException e) {
      throw new IllegalStateException("读取用户回答失败: " + e.getMessage(), e);
    }
  }
}
