package com.oryxos.core.session;

/**
 * 会话管理器最小接口（第 17 节，spec R1）。
 *
 * <p>课件 AgentService 骨架引用的 {@code sessionManager.save(session)}--把累积完历史的会话持久化。 完整形态（按
 * channel+user+profile 拼接 {@code session_id}、查找或创建、{@code sessions} 表存储）是第 18 节交付。
 */
public interface SessionManager {

  /** 持久化会话（一次处理结束后由 AgentService 调用）。 */
  void save(Session session);
}
