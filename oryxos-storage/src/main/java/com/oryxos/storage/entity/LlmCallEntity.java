package com.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 审计实体（只增不改，FR-016）。 */
@Entity
@Table(name = "llm_calls")
public class LlmCallEntity {

  @Id private String id;

  @Column(name = "session_id")
  private String sessionId;

  @Column(name = "provider_name", nullable = false)
  private String providerName;

  @Column(nullable = false)
  private String model;

  @Column(nullable = false)
  private Integer iteration;

  @Column(name = "request_json", columnDefinition = "TEXT")
  private String requestJson;

  @Column(name = "response_json", columnDefinition = "TEXT")
  private String responseJson;

  @Column(name = "request_bytes")
  private Integer requestBytes;

  @Column(name = "response_bytes")
  private Integer responseBytes;

  @Column(name = "prompt_tokens")
  private Integer promptTokens;

  @Column(name = "completion_tokens")
  private Integer completionTokens;

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

  public String getProviderName() {
    return providerName;
  }

  public void setProviderName(String providerName) {
    this.providerName = providerName;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public Integer getIteration() {
    return iteration;
  }

  public void setIteration(Integer iteration) {
    this.iteration = iteration;
  }

  public String getRequestJson() {
    return requestJson;
  }

  public void setRequestJson(String requestJson) {
    this.requestJson = requestJson;
  }

  public String getResponseJson() {
    return responseJson;
  }

  public void setResponseJson(String responseJson) {
    this.responseJson = responseJson;
  }

  public Integer getRequestBytes() {
    return requestBytes;
  }

  public void setRequestBytes(Integer requestBytes) {
    this.requestBytes = requestBytes;
  }

  public Integer getResponseBytes() {
    return responseBytes;
  }

  public void setResponseBytes(Integer responseBytes) {
    this.responseBytes = responseBytes;
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
