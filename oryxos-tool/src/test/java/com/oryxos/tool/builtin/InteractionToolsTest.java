package com.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oryxos.tool.interaction.ConsoleUserInteraction;
import com.oryxos.tool.interaction.UnsupportedUserInteraction;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link InteractionTools} 验收 harness（课件 6.3）：human-in-the-loop 的落点——有人在场能问能答， 无人值守明确报错、绝不静默卡住。 */
class InteractionToolsTest {

  private final InputStream originalStdin = System.in;

  @AfterEach
  void restoreStdin() {
    System.setIn(originalStdin);
  }

  @Test
  @DisplayName("ask_user 有人在场：经 ConsoleUserInteraction 读回一行回答")
  void askUserReturnsConsoleAnswer() {
    System.setIn(new ByteArrayInputStream("去公园\n".getBytes(StandardCharsets.UTF_8)));
    InteractionTools tools = new InteractionTools(new ConsoleUserInteraction());

    String answer = tools.askUser("下午去哪散步？");

    assertThat(answer).isEqualTo("去公园");
  }

  @Test
  @DisplayName("stdin 已关闭：明确报错，绝不把 null 当回答静默返回")
  void consoleAskThrowsWhenStdinClosed() {
    System.setIn(new ByteArrayInputStream(new byte[0]));
    InteractionTools tools = new InteractionTools(new ConsoleUserInteraction());

    assertThatThrownBy(() -> tools.askUser("还有人吗？"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("stdin");
  }

  @Test
  @DisplayName("ask_user 无人值守渠道：明确报错不支持交互，绝不静默卡住")
  void askUserFailsOnUnsupportedChannel() {
    InteractionTools tools = new InteractionTools(new UnsupportedUserInteraction());

    assertThatThrownBy(() -> tools.askUser("要执行危险操作吗？"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("不支持交互");
  }

  @Test
  @DisplayName("空问题明确报错")
  void askUserRejectsBlankQuestion() {
    InteractionTools tools = new InteractionTools(new UnsupportedUserInteraction());

    assertThatThrownBy(() -> tools.askUser("  ")).isInstanceOf(IllegalArgumentException.class);
  }
}
