package com.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code sessions} 会话表实体（第 18 节，技术方案 9.2）。
 *
 * <p>{@code session_id} 主键由存储层的 SessionManager 实现按 channel+user+profile 三元组拼接（应用层赋值，
 * 不走自增）。对话历史整体序列化进 {@code messages_json} 一列（核心阶段不做按条拆表）。表结构由 {@code scripts/sql/001-init.sql}
 * 手工建表脚本维护， 不依赖 Hibernate 自动迁移（宪法工程约束）。
 *
 * <p>时间戳口径照抄 {@link LlmCallEntity}：SQLite 以 TEXT 存 ISO-8601 字符串、由实体层在 new 时写入 {@code
 * created_at}（不交给 DB 默认值，规避 community-dialect 的解析坑）；{@code last_active_at} 每次更新时由管理器刷新。
 */
@Entity
@Table(name = "sessions")
public class SessionEntity {

  @Id
  @Column(name = "session_id")
  private String sessionId;

  @Column(name = "profile_name", nullable = false)
  private String profileName;

  @Column(name = "channel")
  private String channel;

  @Column(name = "user_id")
  private String userId;

  @Column(name = "messages_json")
  private String messagesJson;

  @Column(name = "status", nullable = false)
  private String status = "active";

  @Column(name = "created_at", nullable = false, updatable = false)
  private String createdAt = java.time.Instant.now().toString();

  @Column(name = "last_active_at", nullable = false)
  private String lastActiveAt = createdAt;

  @Column(name = "archived_at")
  private String archivedAt;

  public SessionEntity() {}

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getProfileName() {
    return profileName;
  }

  public void setProfileName(String profileName) {
    this.profileName = profileName;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getMessagesJson() {
    return messagesJson;
  }

  public void setMessagesJson(String messagesJson) {
    this.messagesJson = messagesJson;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public String getLastActiveAt() {
    return lastActiveAt;
  }

  public void setLastActiveAt(String lastActiveAt) {
    this.lastActiveAt = lastActiveAt;
  }

  public String getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(String archivedAt) {
    this.archivedAt = archivedAt;
  }
}
