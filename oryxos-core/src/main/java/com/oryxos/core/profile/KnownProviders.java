package com.oryxos.core.profile;

import java.util.Set;

/**
 * 全局层已声明的 provider 名集合（DIP：接口立在 {@code oryxos-core}，实现在 {@code oryxos-provider}， 由其读 {@code
 * application.yaml} 的 {@code oryxos.providers} 构造）。
 *
 * <p>课件「第一步」强调两层职责分清：全局层只管「连不连得上」（provider 存不存在），Profile 层引用的 {@code provider.name}
 * 必须能在全局层找到同名项，找不到直接报错。{@link ProfileLoader} 依赖本接口做这条 校验，不直接耦合 provider 模块的配置类。
 */
public interface KnownProviders {

  /** 全局层已声明的 provider 名集合（启动时的解析结果）。 */
  Set<String> names();

  /** 该 provider name 是否在全局层声明（含 null 安全）。 */
  default boolean exists(String name) {
    return name != null && names().contains(name);
  }
}
