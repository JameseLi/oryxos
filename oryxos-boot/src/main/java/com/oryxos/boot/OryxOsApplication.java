package com.oryxos.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OryxOS Spring Boot 启动类。
 *
 * <p>注意：scanBasePackages 必须覆盖整个 {@code com.oryxos}，否则其他模块
 * （provider / memory / tool / web / storage …）里的组件扫不到。
 *
 * <p>后续 user story 实施时，CLI 命令分流（不需要 Spring 的命令直接走文件操作）
 * 在 oryxos-cli 的 Picocli 入口实现，见技术方案 8.7。
 */
@SpringBootApplication(scanBasePackages = "com.oryxos")
public class OryxOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(OryxOsApplication.class, args);
    }
}
