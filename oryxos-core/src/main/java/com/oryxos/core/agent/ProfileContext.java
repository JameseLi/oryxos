package com.oryxos.core.agent;

import com.oryxos.core.profile.Profile;

/**
 * Profile 的 ThreadLocal 上下文（课件「编排者」）。
 *
 * <p>为什么需要它：工具接口签名不带 Profile，但有些工具执行时要知道「当前是哪个 Agent」（如 19 节 notify 要读 当前 Profile 的
 * notify_channels）。改工具接口签名代价太大，让统一入口在处理开始时把 Profile 放进来、 结束时清掉，工具想用就取。
 *
 * <p>虚拟线程下每个请求独占一个线程，天然不串；但用完必须清（finally）--否则下一个复用此线程的请求 会拿到别人的 Profile。这是最阴险的一类
 * bug：单请求测试永不报错，只在并发复用时串号，harness 里已显式钉死。
 */
public final class ProfileContext {

  private static final ThreadLocal<Profile> CURRENT = new ThreadLocal<>();

  private ProfileContext() {}

  public static void set(Profile profile) {
    CURRENT.set(profile);
  }

  /** 当前线程绑定的 Profile；未绑定返回 {@code null}。 */
  public static Profile current() {
    return CURRENT.get();
  }

  public static void clear() {
    CURRENT.remove();
  }
}
