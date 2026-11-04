package com.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.memory.MemoryScope;
import com.oryxos.core.session.Session;
import com.oryxos.memory.store.LongTermMemoryStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link MemoryServiceImpl} harness（第 22 节）：门面只做转发，`buildContext` 只含长期记忆、不重复会话历史。 */
class MemoryServiceImplTest {

  private LongTermMemoryStore store;
  private MemoryServiceImpl service;

  @BeforeEach
  void setUp() {
    store = mock(LongTermMemoryStore.class);
    service = new MemoryServiceImpl(store);
  }

  @Test
  @DisplayName("buildContext 返回长期记忆（核心全量 + 归档截断后），不重复会话历史")
  void buildContextReturnsLongTermMemoryOnly() {
    when(store.load()).thenReturn("## 核心记忆\n- 用户叫小王\n## 归档记忆\n- 项目用 Spring");

    String context = service.buildContext(new Session("s-1", "default"));

    assertThat(context).contains("用户叫小王").contains("项目用 Spring");
    assertThat(context).doesNotContain("会话历史");
    verify(store).load();
  }

  @Test
  @DisplayName("remember 转发给 store；scope 为 null 时按归档兜底")
  void rememberDelegatesAndDefaultsToArchival() {
    service.remember("刚记的事", null);
    service.remember("核心的事", MemoryScope.CORE);

    verify(store).append("刚记的事", MemoryScope.ARCHIVAL);
    verify(store).append("核心的事", MemoryScope.CORE);
  }

  @Test
  @DisplayName("recall 转发给 store 的归档检索")
  void recallDelegates() {
    when(store.recallByKeyword("小王")).thenReturn(List.of("用户叫小王"));

    assertThat(service.recall("小王")).containsExactly("用户叫小王");
  }
}
