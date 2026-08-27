package com.oryxos.core.agent;

import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.react.ReActLoop;
import com.oryxos.core.session.Session;
import com.oryxos.core.session.SessionManager;

/**
 * 统一处理入口（课件「编排者」）：CLI、Web、定时三种触发源最终都调同一个 {@link #process}。
 *
 * <p>一次处理的完整编排：按会话所属 Profile 名取 Profile -> 放进 {@link ProfileContext}（工具执行时靠它知道「当前是哪个 Agent」）-> 跑
 * ReAct 循环 -> 把累积完历史的会话持久化 -> 返回响应。 {@code ProfileContext} 在 finally 里清：虚拟线程每请求独立，用完必须清。
 */
public class AgentService {

  private final ProfileRegistry profileRegistry;
  private final ReActLoop reActLoop;
  private final SessionManager sessionManager;

  public AgentService(
      ProfileRegistry profileRegistry, ReActLoop reActLoop, SessionManager sessionManager) {
    this.profileRegistry = profileRegistry;
    this.reActLoop = reActLoop;
    this.sessionManager = sessionManager;
  }

  /**
   * 处理一条用户消息（三源归一的入口，ReAct 不感知消息从哪个入口来）。
   *
   * @throws IllegalStateException 会话所属 Profile 名在注册表里不存在（不悄悄用错）
   */
  public String process(Session session, String userMessage) {
    Profile profile =
        profileRegistry
            .find(session.profileName())
            .orElseThrow(
                () -> new IllegalStateException("会话引用的 Profile 不存在: " + session.profileName()));
    ProfileContext.set(profile);
    try {
      String reply = reActLoop.run(session, userMessage, profile);
      sessionManager.save(session); // 把累积完的历史持久化
      return reply;
    } finally {
      ProfileContext.clear(); // 虚拟线程每请求独立，用完必须清（防串号）
    }
  }
}
