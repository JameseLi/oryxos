package com.oryxos.boot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** 冒烟测试：验证 9 模块聚合后的 Spring 上下文能正常装配启动。测试库指向 target/ 下临时文件，不污染真实工作区。 */
@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:sqlite:target/test-dbs/boot-smoke.db",
      "oryxos.workspace-dir=target/test-dbs/boot-workspace"
    })
class OryxOsApplicationTests {

  /**
   * SQLite 无法在不存在的目录下建库；{@link OryxOsApplication#ensureWorkspaceDirs()} 只在 main()
   * 执行，测试期不触发，故在此补建测试库目录与工作区目录。
   */
  @BeforeAll
  static void createTestDirs() throws IOException {
    Files.createDirectories(Path.of("target/test-dbs"));
    Files.createDirectories(Path.of("target/test-dbs/boot-workspace/agents"));
  }

  @Test
  void contextLoads() {}
}
