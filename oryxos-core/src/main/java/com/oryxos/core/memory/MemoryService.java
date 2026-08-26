package com.oryxos.core.memory;

import com.oryxos.core.session.Session;

/**
 * Memory 统一门面的最小接口（第 17 节，spec R1）。
 *
 * <p>课件「配角一」四部分的第二部分：长期记忆（跨会话记忆），没开就跳过。本签名对齐第 22 节课件已定死的 门面方法 {@code buildContext(Session)}；{@code
 * remember}/{@code recall}（供 {@code MemoryTools} 用）与实现 归第 22 节在 oryxos-memory 模块补齐。
 *
 * <p>{@code PromptBuilder} 构造时该依赖可空：未装配（22 节前）= 第二部分整体缺席，不产生空段。
 */
public interface MemoryService {

  /** 拼进 Prompt 的记忆内容（核心记忆 + 会话记忆上下文，形态由 22 节定义）。 */
  String buildContext(Session session);
}
