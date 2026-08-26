package com.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * {@code tool_invocations} 审计表实体（第 17 节，宪法原则 VI：审计 Day One 落库）。
 *
 * <p>每次工具调用都落一行：成功记 {@code success=true}、失败记 {@code success=false} + {@code error_message} 为原因
 * （课件「配角二」：成功要记、失败也要记--只记成功不记失败 = 一次事故在系统里没痕迹， 属易漏缺陷）。口径与 {@link LlmCallEntity} 逐列对齐：表结构由 {@code
 * scripts/sql/001-init.sql} 手工建表脚本维护； SQLite 无 BOOLEAN，{@code success} 用 INTEGER（0/1）存储、JPA 层以
 * {@link Boolean} 映射； {@code created_at} 以 TEXT 存 ISO-8601 字符串、由实体层在 new 时置入（不交给 DB 默认值，理由同 {@link
 * LlmCallEntity} 注释）。
 */
@Entity
@Table(name = "tool_invocations")
public class ToolInvocationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id")
  private String sessionId;

  @Column(name = "tool_name", nullable = false)
  private String toolName;

  @Column(name = "arguments")
  private String arguments;

  @Column(name = "result")
  private String result;

  @Column(name = "elapsed_ms", nullable = false)
  private Long elapsedMillis;

  @Column(name = "success", nullable = false)
  private Boolean success;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_at", nullable = false, updatable = false)
  private String createdAt = Instant.now().toString();

  public ToolInvocationEntity() {}

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

  public String getToolName() {
    return toolName;
  }

  public void setToolName(String toolName) {
    this.toolName = toolName;
  }

  public String getArguments() {
    return arguments;
  }

  public void setArguments(String arguments) {
    this.arguments = arguments;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
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
