package com.oryxos.core.profile;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Profile 内存索引（{@code Map<name, Profile>}）。
 *
 * <p>本节只有启动扫单条注册路径：{@link ProfileLoader} 解析好逐个 {@link #register}。运行时 {@link #register} 路径（API 上传
 * Agent 由此注册）留第 29 节补；本节不做运行时增删改，只「扫盘注册 + 按 name 查」。
 */
@Component
public class ProfileRegistry {

  private final Map<String, Profile> profiles = new ConcurrentHashMap<>();

  public void register(Profile profile) {
    if (profile == null || profile.name() == null || profile.name().isBlank()) {
      throw new IllegalArgumentException("Profile name 不可为空");
    }
    if (profiles.containsKey(profile.name())) {
      throw new ProfileConflictException(profile.name());
    }
    profiles.put(profile.name(), profile);
  }

  public Optional<Profile> find(String name) {
    if (name == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(profiles.get(name));
  }

  /** 已注册的 Profile 数量（加载统计/测试断言用）。 */
  public int size() {
    return profiles.size();
  }

  /** 已注册的 Profile name 列表（启动报错提示 / 测试断言用）。 */
  public java.util.List<String> registeredNames() {
    return profiles.keySet().stream().sorted().toList();
  }

  /** 同名 Profile 重复注册（启动扫描见同名时抛；本节按启动扫单条注册处理）。 */
  public static class ProfileConflictException extends RuntimeException {
    public ProfileConflictException(String name) {
      super("同名 Profile 重复出现: " + name);
    }
  }
}
