package com.oryxos.boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 冒烟测试：验证 9 模块聚合后的 Spring 上下文能正常装配启动。
 * 测试库指向 target/ 下临时文件，不污染真实工作区。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-dbs/boot-smoke.db",
        "oryxos.workspace-dir=target/test-dbs/boot-workspace"
})
class OryxOsApplicationTests {

    @Test
    void contextLoads() {
    }
}
