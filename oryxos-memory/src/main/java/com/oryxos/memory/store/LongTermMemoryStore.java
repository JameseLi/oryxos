package com.oryxos.memory.store;

import com.oryxos.core.memory.MemoryScope;
import java.util.List;

/**
 * 长期记忆后端接口（第 22 节）：对下可插拔的实现点。
 *
 * <p>三档实现（Markdown / SQLite / Mem0）各写各的，但共同遵守四条行为契约：
 *
 * <ol>
 *   <li><b>不缓存</b>：每次 {@link #load()}/{@link #recallByKeyword(String)} 重新读文件、查库、调 API， 保证 Agent 调完
 *       {@code save_memory} 后下一轮立刻可见；
 *   <li><b>核心记忆永不截断</b>：{@link #load()} 返回核心区全量 + 归档区截断后，截断逻辑物理上碰不到核心区；
 *   <li><b>scope 显式指定</b>：写核心还是写归档由调用方传 {@link MemoryScope} 决定，系统不猜；
 *   <li><b>recall 只搜归档区</b>：核心区本来就会全量注入，不参与检索；只做关键词匹配，不上正则/分词。
 * </ol>
 *
 * <p>接口签名不出现 Markdown/SQLite/Mem0 等实现特有词汇（接口中立原则）。
 */
public interface LongTermMemoryStore {

  /** 追加一条记忆到指定分区（按 scope 路由；null 按调用方约定兜底）。 */
  void append(String content, MemoryScope scope);

  /** 返回核心记忆区全量 + 归档记忆区截断后的文本；没有记忆时返回空串。 */
  String load();

  /** 只在归档记忆区做关键词包含检索，返回命中的记忆行。 */
  List<String> recallByKeyword(String keyword);
}
