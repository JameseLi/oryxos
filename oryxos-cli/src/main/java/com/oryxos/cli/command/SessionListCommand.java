package com.oryxos.cli.command;

import com.oryxos.cli.SpringLauncher;
import com.oryxos.storage.entity.SessionEntity;
import com.oryxos.storage.repository.SessionRepository;
import java.util.Comparator;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;

/**
 * {@code oryxos session list}（第 18 节重命令）：列出 {@code sessions} 表中的会话。
 *
 * <p>重命令：数据在 SQLite（经 JPA），要起上下文才能查。三种运行模式（chat/serve/gateway）共享同一套 Session 存储， 这里看到的就是全部入口的会话。
 */
@Command(mixinStandardHelpOptions = true, name = "list", description = "列出所有会话（sessions 表）")
public class SessionListCommand implements Runnable {

  @Override
  public void run() {
    try (ConfigurableApplicationContext context = SpringLauncher.start()) {
      var sessions =
          context.getBean(SessionRepository.class).findAll().stream()
              .sorted(
                  Comparator.comparing(SessionEntity::getLastActiveAt, Comparator.reverseOrder()))
              .toList();
      if (sessions.isEmpty()) {
        System.out.println("暂无会话（oryxos chat 聊一轮就会有了）");
        return;
      }
      System.out.printf(
          "%-32s %-12s %-10s %-10s %s%n",
          "SESSION_ID", "PROFILE", "CHANNEL", "USER", "LAST_ACTIVE");
      for (SessionEntity session : sessions) {
        System.out.printf(
            "%-32s %-12s %-10s %-10s %s%n",
            session.getSessionId(),
            session.getProfileName(),
            nullSafe(session.getChannel()),
            nullSafe(session.getUserId()),
            session.getLastActiveAt());
      }
    }
  }

  private static String nullSafe(String value) {
    return value == null ? "-" : value;
  }
}
