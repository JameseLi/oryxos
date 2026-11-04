package com.oryxos.memory.store;

import com.oryxos.core.memory.MemoryScope;
import com.oryxos.storage.entity.MemoryEntryEntity;
import com.oryxos.storage.repository.MemoryEntryRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;

/**
 * SQLite 长期记忆后端（第 22 节结构化升级档，技术方案 §5.1/§9.2）。
 *
 * <p>记忆按条入库到 {@code memory_entries} 表：核心区用 {@code WHERE scope='CORE'} 全量取，归档区查询加 {@code LIMIT
 * 100}、检索用 SQL {@code LIKE}——同一套契约、完全不同的落地方式。每次直接查库，天然满足「不缓存」（契约一）；{@code LIMIT} 只加在归档查询上，SQL
 * 结构保证核心区不被截断（契约二）。
 */
public class SqliteMemoryStore implements LongTermMemoryStore {

  static final int MAX_ARCHIVE_ROWS = 100;

  private static final String CORE_HEADER = "## 核心记忆";
  private static final String ARCHIVE_HEADER = "## 归档记忆";

  private final MemoryEntryRepository repository;

  public SqliteMemoryStore(MemoryEntryRepository repository) {
    this.repository = repository;
  }

  @Override
  public void append(String content, MemoryScope scope) {
    MemoryScope effective = scope == null ? MemoryScope.ARCHIVAL : scope;
    MemoryEntryEntity entity = new MemoryEntryEntity();
    entity.setScope(effective.name());
    entity.setContent(content);
    entity.setCreatedAt(Instant.now().toString());
    repository.save(entity);
  }

  @Override
  public String load() {
    List<MemoryEntryEntity> core = repository.findByScopeOrderByIdAsc(MemoryScope.CORE.name());
    List<MemoryEntryEntity> archive =
        repository.findByScopeOrderByIdDesc(
            MemoryScope.ARCHIVAL.name(), PageRequest.of(0, MAX_ARCHIVE_ROWS));
    // SQL 已按 id 倒序取最近 N 条；渲染时翻回升序，读起来跟写入顺序一致。
    List<MemoryEntryEntity> recentArchive = new ArrayList<>(archive);
    Collections.reverse(recentArchive);
    if (core.isEmpty() && recentArchive.isEmpty()) {
      return "";
    }
    return render(CORE_HEADER, core) + render(ARCHIVE_HEADER, recentArchive);
  }

  @Override
  public List<String> recallByKeyword(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return List.of();
    }
    return repository
        .findByScopeAndContentContainingOrderByIdAsc(MemoryScope.ARCHIVAL.name(), keyword)
        .stream()
        .map(MemoryEntryEntity::getContent)
        .toList();
  }

  private static String render(String header, List<MemoryEntryEntity> entries) {
    StringBuilder text = new StringBuilder(header).append('\n');
    for (MemoryEntryEntity entry : entries) {
      text.append("- [").append(entry.getCreatedAt()).append("] ");
      text.append(entry.getContent()).append('\n');
    }
    return text.toString();
  }
}
