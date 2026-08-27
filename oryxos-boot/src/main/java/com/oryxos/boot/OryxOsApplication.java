package com.oryxos.boot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * OryxOS Spring Boot 启动类。
 *
 * <p>注意 1：扫描范围显式列出各模块包（不含 com.oryxos.cli）--第 18 节起 cli 模块有了自己的重命令启动类 SpringLauncher（同样声明 JPA
 * 扫描），两套启动类互不可见，否则同一个上下文里 @EnableJpaRepositories 跑两遍、仓储 Bean 定义重复直接报错（SpringLauncher 侧已对称地排除
 * com.oryxos.boot.*）。后续若在 cli 模块新增 Spring Bean，需同步把 对应包加进这里。
 *
 * <p>注意 2：JPA 实体/仓储在 oryxos-storage 模块，自动配置包默认只看 com.oryxos.boot， 必须显式 EntityScan +
 * EnableJpaRepositories。
 */
@SpringBootApplication(
    scanBasePackages = {
      "com.oryxos.core",
      "com.oryxos.provider",
      "com.oryxos.memory",
      "com.oryxos.tool",
      "com.oryxos.channel",
      "com.oryxos.storage",
      "com.oryxos.web"
    })
@EntityScan("com.oryxos.storage.entity")
@EnableJpaRepositories("com.oryxos.storage.repository")
public class OryxOsApplication {

  public static void main(String[] args) {
    ensureWorkspaceDirs();
    SpringApplication.run(OryxOsApplication.class, args);
  }

  /** Spring 上下文启动前确保工作区目录存在——SQLite 无法在不存在的目录下建库。 （init 命令负责生成完整文件内容，这里只保底目录。） */
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
