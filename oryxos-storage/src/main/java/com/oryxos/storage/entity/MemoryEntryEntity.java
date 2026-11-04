package com.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * {@code memory_entries} 长期记忆条目表实体（第 22 节，技术方案 §9.2）。
 *
 * <p>记忆按条入库，{@code scope} 列区分 {@code CORE} / {@code ARCHIVAL}：核心区查询全量、归档区查询加 {@code LIMIT 100}，检索用
 * SQL {@code LIKE}。表结构由 {@code scripts/sql/001-init.sql} 手工建表脚本维护，不依赖 Hibernate 自动迁移 （宪法工程约束）。
 *
 * <p>{@code created_at} 沿用项目口径：SQLite 以 TEXT 存 ISO-8601 字符串，由实体层在 new 时写入，不交给 DB 默认值 （见 {@link
 * LlmCallEntity} 注释）。
 */
@Entity
@Table(name = "memory_entries")
public class MemoryEntryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "scope", nullable = false, length = 16)
  private String scope;

  @Column(name = "content", nullable = false)
  private String content;

  @Column(name = "created_at", nullable = false, updatable = false)
  private String createdAt = Instant.now().toString();

  public MemoryEntryEntity() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }
}
