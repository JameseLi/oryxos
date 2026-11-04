package com.oryxos.memory.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.memory.MemoryScope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link MarkdownMemoryStore} 专属 harness（第 22 节）。
 *
 * <p>覆盖 Markdown 特有：区块解析/自建、4000 字符截断边界、recall 只搜归档区、写后立读。四条通用契约由 {@code MemoryStoreContractTest}
 * 参数化覆盖。
 */
class MarkdownMemoryStoreTest {

  @TempDir Path tempDir;

  private Path memoryFile;
  private MarkdownMemoryStore store;

  @BeforeEach
  void setUp() {
    memoryFile = tempDir.resolve("memory/MEMORY.md");
    store = new MarkdownMemoryStore(memoryFile);
  }

  @Test
  @DisplayName("首次 append 自动创建 MEMORY.md 两区块，条目带日期前缀")
  void createsCanonicalSectionsOnFirstAppend() throws IOException {
    store.append("用户叫小王", MemoryScope.CORE);

    String raw = Files.readString(memoryFile);
    assertThat(raw).contains("# MEMORY.md");
    assertThat(raw).contains(MarkdownMemoryStore.CORE_HEADER);
    assertThat(raw).contains(MarkdownMemoryStore.ARCHIVE_HEADER);
    assertThat(raw).contains("- [" + LocalDate.now() + "] 用户叫小王");
  }

  @Test
  @DisplayName("归档区超过 4000 字符只裁归档段，核心区一字不少")
  void truncationOnlyAppliesToArchiveSection() {
    store.append("用户叫小王，偏好用 Java", MemoryScope.CORE);
    for (int i = 0; i < 200; i++) {
      store.append("归档流水 " + i, MemoryScope.ARCHIVAL);
    }

    String loaded = store.load();

    assertThat(loaded).contains("用户叫小王，偏好用 Java");
    assertThat(loaded).doesNotContain("归档流水 0");
    assertThat(loaded).contains("归档流水 199");
  }

  @Test
  @DisplayName("recall 只搜归档区，核心记忆不参与检索")
  void recallOnlySearchesArchiveSection() {
    store.append("核心关键词-小王", MemoryScope.CORE);
    store.append("归档关键词-项目", MemoryScope.ARCHIVAL);

    assertThat(store.recallByKeyword("核心关键词")).isEmpty();
    assertThat(store.recallByKeyword("归档关键词")).anyMatch(line -> line.contains("归档关键词-项目"));
  }

  @Test
  @DisplayName("写入后立刻可读，不允许有缓存")
  void appendIsVisibleOnNextRead() {
    store.append("刚记的事", MemoryScope.ARCHIVAL);

    assertThat(store.load()).contains("刚记的事");
    assertThat(store.recallByKeyword("刚记的事")).isNotEmpty();
  }
}
