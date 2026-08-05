package com.oryxos.core.profile;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Profile 装配配置（第 16 节）：把 {@code oryxos.workspace-dir} 解析成 {@code <workspace>/profiles} 的 {@link
 * Path} bean，并据此构造 {@link ProfileLoader}（不让 {@link ProfileLoader} 直接 {@code @Component}， 因为它需要
 * {@link Path} 入参，而 {@link Path} 没有现成自动装配）。
 */
@Configuration
public class ProfileConfiguration {

  private final String workspaceDir;
  private final KnownProviders knownProviders;
  private final ProfileRegistry registry;

  public ProfileConfiguration(
      @Value("${oryxos.workspace-dir:.oryxos}") String workspaceDir,
      KnownProviders knownProviders,
      ProfileRegistry registry) {
    this.workspaceDir = workspaceDir;
    this.knownProviders = knownProviders;
    this.registry = registry;
  }

  /** Profile 扫描目录：{@code <workspace>/profiles}。 */
  @Bean
  public Path profilesDir() {
    return Path.of(workspaceDir, "profiles");
  }

  /** Profile 加载器：扫描 {@link #profilesDir}，解析 + 校验 + 注册。 */
  @Bean
  public ProfileLoader profileLoader(Path profilesDir) {
    return new ProfileLoader(profilesDir, knownProviders, registry);
  }

  /** 启动时跑一次 Profile 扫描（课件「第零步」：启动期加载）。 */
  @Bean
  public org.springframework.boot.ApplicationRunner profileLoaderRunner(ProfileLoader loader) {
    return args -> loader.loadAll();
  }
}
