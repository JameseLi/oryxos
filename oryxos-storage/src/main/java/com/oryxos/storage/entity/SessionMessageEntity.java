package com.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "session_messages")
public class SessionMessageEntity {

  @Id private String id;

  @Column(name = "session_id", nullable = false)
  private String sessionId;

  @Column(nullable = false)
  private Integer seq;

  @Column(nullable = false)
  private String role;

  @Column(columnDefinition = "TEXT")
  private String content;

  @Column(name = "tool_calls_json", columnDefinition = "TEXT")
  private String toolCallsJson;

  @Column(name = "tool_call_id")
  private String toolCallId;

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

  public Integer getSeq() {
    return seq;
  }

  public void setSeq(Integer seq) {
    this.seq = seq;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getToolCallsJson() {
    return toolCallsJson;
  }

  public void setToolCallsJson(String toolCallsJson) {
    this.toolCallsJson = toolCallsJson;
  }

  public String getToolCallId() {
    return toolCallId;
  }

  public void setToolCallId(String toolCallId) {
    this.toolCallId = toolCallId;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }
}
