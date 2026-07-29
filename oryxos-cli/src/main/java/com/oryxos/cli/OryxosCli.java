package com.oryxos.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Picocli 主入口：Spring Boot 启动后由 CommandLineRunner 分发到子命令。
 * 核心阶段只交付 init / chat 两个子命令（contracts/cli-contract.md）。
 */
@Component
public class OryxosCli implements CommandLineRunner {

    private final ApplicationContext applicationContext;

    public OryxosCli(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) {
        CommandLine commandLine = new CommandLine(new TopCommand())
                .addSubcommand("init", applicationContext.getBean(InitCommand.class))
                .addSubcommand("chat", applicationContext.getBean(ChatCommand.class));
        if (args.length == 0) {
            commandLine.usage(System.out);
            return;
        }
        int exitCode = commandLine.execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    @Command(name = "oryxos", mixinStandardHelpOptions = true,
            description = "OryxOS — Java 原生的企业级 Agent OS。子命令: init / chat")
    static class TopCommand {
    }
}
