package com.oryxos.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 重命令启动类（第 18 节）：{@code chat} / {@code session list} / {@code tool list} 等重命令经它启动 Spring 上下文。
 *
 * <p>课件「第四」坑的解法本体：{@code scanBasePackages} 只管普通 Bean 的组件扫描，**不会**带动自动配置的
 * {@code @EnableJpaRepositories}/{@code @EntityScan} 跟着扫到别的模块（两者默认只看主类自己所在的包）。CLI 与存储模块是分开的 Maven
 * 模块，不显式声明 basePackages，启动会得到 "Found 0 JPA repository interfaces"（会话/审计写不进去）。
 *
 * <p>用 REGEX excludeFilter 排除 {@code com.oryxos.boot.*}：fat JAR 里 boot 模块的 {@code
 * OryxOsApplication} 也在扫描范围内，不排除会被当作普通 {@code @Configuration} 重复装配（两个 main 类各自带一套注解）。
 *
 * <p>此处用 {@code @SpringBootConfiguration + @EnableAutoConfiguration + @ComponentScan} 平铺组合而非
 * {@code @SpringBootApplication}：后者不暴露 excludeFilters 属性。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan("com.oryxos.storage.entity")
@EnableJpaRepositories("com.oryxos.storage.repository")
@ComponentScan(
    basePackages = "com.oryxos",
    excludeFilters =
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.oryxos\\.boot\\..*"))
public class SpringLauncher {

  /** 启动重命令的 Spring 上下文（先确保工作区目录存在--SQLite 无法在不存在的目录下建库）。 */
  public static ConfigurableApplicationContext start(String... args) {
    ensureWorkspaceDirs();
    return SpringApplication.run(SpringLauncher.class, args);
  }

  private static void ensureWorkspaceDirs() {
    String workspaceDir = System.getProperty("oryxos.workspace-dir", ".oryxos");
    try {
      Files.createDirectories(Path.of(workspaceDir));
      Files.createDirectories(Path.of(workspaceDir, "agents"));
    } catch (IOException e) {
      throw new UncheckedIOException("无法创建工作区目录: " + workspaceDir, e);
    }
  }
}
