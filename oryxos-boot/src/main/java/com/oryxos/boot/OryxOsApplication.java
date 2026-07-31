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
 * <p>注意：scanBasePackages 必须覆盖整个 {@code com.oryxos}，否则其他模块 （provider / memory / tool / web / storage
 * …）里的组件扫不到。 JPA 实体/仓储在 oryxos-storage 模块，自动配置包默认只看 com.oryxos.boot， 必须显式 EntityScan +
 * EnableJpaRepositories。
 */
@SpringBootApplication(scanBasePackages = "com.oryxos")
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
