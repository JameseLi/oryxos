package com.oryxos.memory.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.memory.MemoryScope;
import com.oryxos.memory.MemoryTestApplication;
import com.oryxos.storage.entity.MemoryEntryEntity;
import com.oryxos.storage.repository.MemoryEntryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link SqliteMemoryStore} 专属 harness（第 22 节）：手工建表脚本能建能读、归档 LIMIT 生效、LIKE 检索。
 *
 * <p>测试库为内存 SQLite，建表走真实 {@code scripts/sql/001-init.sql}（{@code spring.sql.init}）， 不让 Hibernate
 * 自动建——否则测试绿、生产跑真脚本时列名对不上。
 */
@SpringBootTest(classes = MemoryTestApplication.class)
class SqliteMemoryStoreTest {

  @Autowired private MemoryEntryRepository repository;

  private SqliteMemoryStore store;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
    store = new SqliteMemoryStore(repository);
  }

  @Test
  @DisplayName("手工脚本建出的 memory_entries 能存能读，scope 列正确落 CORE/ARCHIVAL")
  void scriptBuiltTableRoundTripsCoreAndArchive() {
    store.append("用户叫小王", MemoryScope.CORE);
    store.append("项目用 Spring Boot", MemoryScope.ARCHIVAL);

    List<MemoryEntryEntity> rows = repository.findAll();
    assertThat(rows).hasSize(2);
    assertThat(rows)
        .extracting(MemoryEntryEntity::getScope)
        .containsExactlyInAnyOrder("CORE", "ARCHIVAL");
    assertThat(store.load()).contains("用户叫小王").contains("项目用 Spring Boot");
  }

  @Test
  @DisplayName("归档区只带最近 100 条（LIMIT），核心区仍全量")
  void archiveLimitKeepsOnlyRecentHundred() {
    store.append("核心记忆-常驻", MemoryScope.CORE);
    for (int i = 0; i < 150; i++) {
      store.append("归档流水 " + i, MemoryScope.ARCHIVAL);
    }

    String loaded = store.load();
    long archiveLines = loaded.lines().filter(line -> line.contains("归档流水 ")).count();

    assertThat(archiveLines).isEqualTo(100);
    assertThat(loaded).contains("核心记忆-常驻");
    assertThat(loaded).doesNotContain("归档流水 0");
    assertThat(loaded).contains("归档流水 149");
  }

  @Test
  @DisplayName("recall 用 SQL LIKE 只命中归档区，核心记忆不参与")
  void recallUsesLikeAndOnlySearchesArchive() {
    store.append("核心关键词-小王", MemoryScope.CORE);
    store.append("归档关键词-项目", MemoryScope.ARCHIVAL);

    assertThat(store.recallByKeyword("核心关键词")).isEmpty();
    assertThat(store.recallByKeyword("归档关键词")).containsExactly("归档关键词-项目");
  }
}
