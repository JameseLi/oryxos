package com.oryxos.storage;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * oryxos-storage 模块自带的测试引导类：模块本身没有 {@code @SpringBootConfiguration}（main 入口在 oryxos-boot）， 故为
 * {@link LlmCallRepositoryTest} / {@link JpaLlmCallAuditTest} 提供本模块最小上下文：扫本包 + 实体 + 仓储， 不依赖向上跨模块找
 * {@code com.oryxos.boot.OryxOsApplication}。
 */
@SpringBootApplication(scanBasePackages = "com.oryxos.storage")
@EntityScan("com.oryxos.storage.entity")
@EnableJpaRepositories("com.oryxos.storage.repository")
class StorageTestApplication {}
