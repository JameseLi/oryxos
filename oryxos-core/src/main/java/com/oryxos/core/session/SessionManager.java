package com.oryxos.core.session;

import java.util.Optional;

/**
 * 会话管理器（第 18 节完整形态）。
 *
 * <p>对外三个方法：{@link #getOrCreate}（按三元组查找或创建）、{@link #get}（按 id 取回）、 {@link #save}（持久化）。会话身份由 channel
 * + user + profile 三元组唯一确定：同一三元组历次进入拿到同一个会话（幂等），任一元素 不同就是不同会话（隔离）。
 *
 * <p><b>session_id 拼接只发生在这一个接口的实现内部</b>（课件「三」）：所有入口（CLI 传 {@code "cli"}、Web 传 {@code "web"}、定时传
 * {@code "scheduler"}）只提供三元组、不自己拼字符串--两处各拼一遍、格式差一个分隔符，同一个人就会出现两条互不相认的历史。
 *
 * <p>第 17 节为最小形态（仅 {@code save}）；完整形态于第 18 节交付（{@code sessions} 表持久化归 JPA 实现，core 不感知）。
 */
public interface SessionManager {

  /**
   * 按三元组查找或创建会话（幂等：同一三元组返回同一 id 的会话）。
   *
   * @param channel 接入 Channel（如 "cli" / "web" / "scheduler"）
   * @param user 用户标识
   * @param profileName 所属 Profile 名
   * @return 已存在则携带历史返回，不存在则新建（首次 save 时落库）
   */
  Session getOrCreate(String channel, String user, String profileName);

  /** 按 id 取回会话（携完整历史）；不存在返回 empty。 */
  Optional<Session> get(String sessionId);

  /** 持久化会话（一次处理结束后由 AgentService 调用；新会话插入、既有会话更新历史与活跃时间）。 */
  void save(Session session);
}
