package com.oryxos.memory;

import com.oryxos.core.memory.MemoryScope;
import com.oryxos.core.memory.MemoryService;
import com.oryxos.core.session.Session;
import com.oryxos.memory.store.LongTermMemoryStore;
import java.util.List;

/**
 * {@link MemoryService} 的实现（第 22 节）：门面很薄，真正可换的是 {@link LongTermMemoryStore} 这一层。
 *
 * <p>{@link #buildContext} 只返回长期记忆（{@code store.load()}），不重复拼会话历史——当前会话历史由 {@code PromptBuilder}
 * 的会话历史段独立负责（第 17 节既有四段结构）。门面本身不持缓存字段，严格遵守「不缓存」契约。
 */
public class MemoryServiceImpl implements MemoryService {

  private final LongTermMemoryStore longTermMemoryStore;

  public MemoryServiceImpl(LongTermMemoryStore longTermMemoryStore) {
    this.longTermMemoryStore = longTermMemoryStore;
  }

  @Override
  public String buildContext(Session session) {
    return longTermMemoryStore.load();
  }

  @Override
  public void remember(String content, MemoryScope scope) {
    longTermMemoryStore.append(content, scope == null ? MemoryScope.ARCHIVAL : scope);
  }

  @Override
  public List<String> recall(String keyword) {
    return longTermMemoryStore.recallByKeyword(keyword);
  }
}
