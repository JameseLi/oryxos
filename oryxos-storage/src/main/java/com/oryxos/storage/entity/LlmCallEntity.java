package com.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * {@code llm_calls} 审计表实体（宪法原则 VI：审计 Day One 落库）。
 *
 * <p>每次大模型调用都落一行：成功记 {@code success=true}、失败记 {@code success=false} + {@code error_message}
 * 为原因（课件「第三步/第四步」：只记成功不记失败 = 一次真实事故在系统里没痕迹， 属易漏缺陷）。表结构由 {@code scripts/sql/001-init.sql}
 * 手工建表脚本维护，不依赖 Hibernate 自动迁移 （宪法工程约束）。SQLite 无 BOOLEAN 类型，{@code success} 用 INTEGER（0/1）存储，JPA 层以
 * {@link Boolean} 映射。
 *
 * <p>{@code created_at} 在 SQLite 里以 TEXT 存 ISO-8601 字符串（Java 层写入 {@link Instant#toString()}、 读回由
 * {@code SQLiteDialect} 不介入解析）——SQLite 的 {@code CURRENT_TIMESTAMP} 默认值在 {@code
 * hibernate-community-dialects} 的 Instant 映射下会报 {@code Error parsing time stamp}， 故默认值不交给 DB 而由实体层在
 * new 时置入，SQL 脚本不在该列设默认值。
 */
@Entity
@Table(name = "llm_calls")
public class LlmCallEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id")
  private String sessionId;

  @Column(name = "provider", nullable = false)
  private String provider;

  @Column(name = "model")
  private String model;

  @Column(name = "prompt_tokens")
  private Integer promptTokens;

  @Column(name = "completion_tokens")
  private Integer completionTokens;

  @Column(name = "total_tokens")
  private Integer totalTokens;

  @Column(name = "elapsed_ms", nullable = false)
  private Long elapsedMillis;

  @Column(name = "success", nullable = false)
  private Boolean success;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_at", nullable = false, updatable = false)
  private String createdAt = Instant.now().toString();

  public LlmCallEntity() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public Integer getPromptTokens() {
    return promptTokens;
  }

  public void setPromptTokens(Integer promptTokens) {
    this.promptTokens = promptTokens;
  }

  public Integer getCompletionTokens() {
    return completionTokens;
  }

  public void setCompletionTokens(Integer completionTokens) {
    this.completionTokens = completionTokens;
  }

  public Integer getTotalTokens() {
    return totalTokens;
  }

  public void setTotalTokens(Integer totalTokens) {
    this.totalTokens = totalTokens;
  }

  public Long getElapsedMillis() {
    return elapsedMillis;
  }

  public void setElapsedMillis(Long elapsedMillis) {
    this.elapsedMillis = elapsedMillis;
  }

  public Boolean getSuccess() {
    return success;
  }

  public void setSuccess(Boolean success) {
    this.success = success;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }
}
