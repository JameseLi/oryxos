package com.oryxos.core.agent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.oryxos.core.profile.Profile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * {@link ContextLoader} 验收 harness（课件「验收 harness」第五个单测类）。
 *
 * <p>覆盖验收点：改文件后下一次 load 立即读到新内容（无缓存回归）；Skill 引用缺失报错；Bootstrap 缺失 WARN。
 */
class ContextLoaderTest {

  @TempDir Path workspace;

  @Test
  @DisplayName("改文件后下一次 build 立即读到新内容（无缓存回归）")
  void reloadAfterFileChange_readsNewContentImmediately() throws IOException {
    Path agents = Files.writeString(workspace.resolve("AGENTS.md"), "v1-人格底座");
    ContextLoader loader = new ContextLoader(workspace);

    String first = loader.load(profileWithBootstrap("AGENTS.md"));
    assertTrue(first.contains("v1-人格底座"));

    Files.writeString(agents, "v2-改过的人格");
    String second = loader.load(profileWithBootstrap("AGENTS.md"));
    assertTrue(second.contains("v2-改过的人格"), "不缓存：改完立即生效");
    assertFalse(second.contains("v1-人格底座"));
  }

  @Test
  @DisplayName("Skill 引用缺失报错：异常消息含文件路径与 Profile 名，绝不静默")
  void missingSkillReference_throwsWithError() {
    ContextLoader loader = new ContextLoader(workspace);

    IllegalStateException e =
        assertThrows(
            IllegalStateException.class, () -> loader.load(profileWithSkill("skills/missing.md")));

    assertTrue(e.getMessage().contains("missing.md"), "消息要指向缺失文件: " + e.getMessage());
    assertTrue(e.getMessage().contains("skills"), "消息要带上相对路径段: " + e.getMessage());
    assertTrue(e.getMessage().contains("weather"), "消息要指向 Profile 名: " + e.getMessage());
  }

  @Test
  @DisplayName("Bootstrap 缺失 WARN：不抛异常、不静默（WARN 事件被记录）")
  void missingBootstrapFile_logsWarnAndContinues() {
    ContextLoader loader = new ContextLoader(workspace);
    Logger logger = (Logger) LoggerFactory.getLogger(ContextLoader.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      String context =
          assertDoesNotThrow(() -> loader.load(profileWithBootstrap("missing-bootstrap.md")));
      assertFalse(context.contains("missing-bootstrap"), "缺失文件不产出任何内容");
    } finally {
      logger.detachAppender(appender);
    }
    assertTrue(
        appender.list.stream()
            .anyMatch(
                e ->
                    e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("missing-bootstrap.md")),
        "Bootstrap 缺失必须留下 WARN 痕迹，不静默跳过");
  }

  private static Profile profileWithBootstrap(String file) {
    return new Profile(
        "weather",
        null,
        null,
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        new Profile.Bootstrap(List.of(file)),
        null);
  }

  private static Profile profileWithSkill(String skill) {
    return new Profile(
        "weather",
        null,
        null,
        null,
        List.of(),
        List.of(skill),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }
}
