package com.oryxos.storage.repository;

import com.oryxos.storage.entity.MemoryEntryEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code memory_entries} 表仓储（第 22 节）。
 *
 * <p>三个派生查询分别承载四条行为契约：核心区全量（{@code findByScopeOrderByIdAsc}）、归档区最近 N 条 （{@code
 * findByScopeOrderByIdDesc} + {@link Pageable} 的 {@code LIMIT}）、归档区关键词 {@code LIKE} 检索 （{@code
 * Containing}）。核心区查询不带分页，物理上保证「核心永不被截断」。
 */
public interface MemoryEntryRepository extends JpaRepository<MemoryEntryEntity, Long> {

  /** 按分区取全部条目（核心区用；升序保证渲染顺序稳定）。 */
  List<MemoryEntryEntity> findByScopeOrderByIdAsc(String scope);

  /** 按分区倒序分页取（归档区用；{@link Pageable} 限制条数，截断变成 SQL 的 LIMIT）。 */
  List<MemoryEntryEntity> findByScopeOrderByIdDesc(String scope, Pageable pageable);

  /** 归档区关键词包含匹配（SQL LIKE），升序返回。 */
  List<MemoryEntryEntity> findByScopeAndContentContainingOrderByIdAsc(String scope, String keyword);
}
