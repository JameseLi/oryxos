package com.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 审计实体（只增不改，FR-017）。 */
@Entity
@Table(name = "tool_invocations")
public class ToolInvocationEntity {

  @Id private String id;

  @Column(name = "session_id")
  private String sessionId;

  @Column(name = "tool_name", nullable = false)
  private String toolName;

  @Column(name = "arguments_json", columnDefinition = "TEXT")
  private String argumentsJson;

  @Column(name = "result_preview", columnDefinition = "TEXT")
  private String resultPreview;

  @Column(name = "duration_ms", nullable = false)
  private Long durationMs;

  @Column(nullable = false)
  private Integer success;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private String createdAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
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

  public String getArgumentsJson() {
    return argumentsJson;
  }

  public void setArgumentsJson(String argumentsJson) {
    this.argumentsJson = argumentsJson;
  }

  public String getResultPreview() {
    return resultPreview;
  }

  public void setResultPreview(String resultPreview) {
    this.resultPreview = resultPreview;
  }

  public Long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(Long durationMs) {
    this.durationMs = durationMs;
  }

  public Integer getSuccess() {
    return success;
  }

  public void setSuccess(Integer success) {
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
