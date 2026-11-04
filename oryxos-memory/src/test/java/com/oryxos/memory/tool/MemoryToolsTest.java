package com.oryxos.memory.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.memory.MemoryScope;
import com.oryxos.core.memory.MemoryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link MemoryTools} harness（第 22 节）：scope 缺省/映射、未命中返回约定文案、非法 scope 报错。 */
class MemoryToolsTest {

  private MemoryService memoryService;
  private MemoryTools tools;

  @BeforeEach
  void setUp() {
    memoryService = mock(MemoryService.class);
    tools = new MemoryTools(memoryService);
  }

  @Test
  @DisplayName("scope 缺省/空白写归档")
  void scopeDefaultsToArchival() {
    tools.saveMemory("缺省归档", null);
    tools.saveMemory("空白归档", "  ");

    verify(memoryService).remember("缺省归档", MemoryScope.ARCHIVAL);
    verify(memoryService).remember("空白归档", MemoryScope.ARCHIVAL);
  }

  @Test
  @DisplayName("core 大小写不敏感映射到核心区")
  void coreScopeMapsCaseInsensitive() {
    tools.saveMemory("核心记忆", "core");
    tools.saveMemory("核心记忆2", "CORE");

    verify(memoryService).remember("核心记忆", MemoryScope.CORE);
    verify(memoryService).remember("核心记忆2", MemoryScope.CORE);
  }

  @Test
  @DisplayName("关键词未命中返回「没有找到相关记忆」而不抛异常")
  void missReturnsMessageNotException() {
    when(memoryService.recall("不存在")).thenReturn(List.of());

    assertThat(tools.recallMemory("不存在")).isEqualTo("没有找到相关记忆");
  }

  @Test
  @DisplayName("命中结果按行拼接返回")
  void hitJoinsLines() {
    when(memoryService.recall("小王")).thenReturn(List.of("用户叫小王", "小王偏好 Java"));

    assertThat(tools.recallMemory("小王")).isEqualTo("用户叫小王\n小王偏好 Java");
  }

  @Test
  @DisplayName("非法 scope 抛 IllegalArgumentException（由 ToolExecutor 走失败审计）")
  void invalidScopeThrows() {
    assertThatThrownBy(() -> tools.saveMemory("内容", "unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("scope");
  }
}
