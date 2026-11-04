package com.oryxos.core.memory;

/**
 * 长期记忆分区（第 22 节，契约三）。
 *
 * <p>借 MemGPT 的 core memory 思路分两层：{@link #CORE} 是"始终在场"的一小块（用户是谁、项目背景、关键偏好）， 每次组装 Prompt
 * 完整注入、永不截断；{@link #ARCHIVAL} 是量大、按需检索的历史事实，截断与关键词检索只作用在这一层。
 *
 * <p>写核心还是写归档由 Agent 调用 {@code save_memory} 时显式指定，系统不猜；缺省为 {@link #ARCHIVAL}。
 */
public enum MemoryScope {
  CORE,
  ARCHIVAL
}
