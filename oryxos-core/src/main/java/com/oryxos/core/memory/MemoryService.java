package com.oryxos.core.memory;

import com.oryxos.core.session.Session;
import java.util.List;

/**
 * Memory 统一门面（第 17 节最小接口，第 22 节补齐）。
 *
 * <p>上层（{@code PromptBuilder} / {@code MemoryTools}）只认本接口，对底下是 Markdown 文件、SQLite 还是 Mem0 完全无感；
 * 换后端只换 oryxos-memory 模块注入的 {@code LongTermMemoryStore} 实现，本接口与所有调用方零改动。
 *
 * <p>{@link #buildContext} 只负责长期记忆（核心区全量 + 归档区截断后）；当前会话历史由 {@code PromptBuilder}
 * 的会话历史段独立负责，避免两处重复注入。实现位于 oryxos-memory 模块。
 */
public interface MemoryService {

  /**
   * 拼进 Prompt 的长期记忆内容（核心记忆 + 归档记忆，归档区截断后）。
   *
   * @param session 当前会话（门面签名保留；会话历史由 PromptBuilder 独立拼装，本方法不重复输出）
   */
  String buildContext(Session session);

  /**
   * 记住一条长期记忆（{@code save_memory} 调用）。
   *
   * @param content 要记住的内容
   * @param scope 核心/归档分区；由 Agent 显式指定，null 按 {@link MemoryScope#ARCHIVAL} 兜底
   */
  void remember(String content, MemoryScope scope);

  /**
   * 按关键词检索长期记忆（{@code recall_memory} 调用）；只搜归档区，核心区本来就会全量注入。
   *
   * @param keyword 检索关键词
   * @return 命中的记忆条目；无命中返回空列表
   */
  List<String> recall(String keyword);
}
