package com.oryxos.memory;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * oryxos-memory 模块自带的测试引导类（第 22 节）：模块 main 没有 Spring Boot 入口， 故为 Memory 测试提供最小上下文——扫 memory +
 * storage 两个包、显式声明 storage 的实体/仓储扫描（JPA 自动配置默认只看主类所在包）。
 */
@SpringBootApplication(scanBasePackages = {"com.oryxos.memory", "com.oryxos.storage"})
@EntityScan("com.oryxos.storage.entity")
@EnableJpaRepositories("com.oryxos.storage.repository")
public class MemoryTestApplication {}
