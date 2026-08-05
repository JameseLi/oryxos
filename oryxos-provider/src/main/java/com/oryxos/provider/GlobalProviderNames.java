package com.oryxos.provider;

import com.oryxos.core.profile.KnownProviders;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * {@link KnownProviders} 的 provider 模块实现：从绑定好的 {@link ProviderProperties} 提取全局层已声明的 provider name
 * 集合。Profile 层的 {@code provider.name} 校验就调它（课件「第一步」：引用不到全局层同名项 直接报错）。
 */
@Component
public class GlobalProviderNames implements KnownProviders {

  private final ProviderProperties properties;

  public GlobalProviderNames(ProviderProperties properties) {
    this.properties = properties;
  }

  @Override
  public Set<String> names() {
    Set<String> out = new LinkedHashSet<>();
    for (ProviderProperties.ProviderEntry e : properties.getProviders()) {
      if (e.getName() != null && !e.getName().isBlank()) {
        out.add(e.getName());
      }
    }
    return out;
  }
}
