package com.oryxos.cli;

import java.util.concurrent.Callable;

import com.oryxos.channel.cli.CliChannel;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * oryxos chat：进入 CLI 多轮对话（REPL）。
 */
@Component
@Command(name = "chat", description = "与指定 Agent 开始多轮对话（exit/quit 退出）")
public class ChatCommand implements Callable<Integer> {

    private final CliChannel cliChannel;

    public ChatCommand(CliChannel cliChannel) {
        this.cliChannel = cliChannel;
    }

    // --profile 为 --agent 别名（兼容 README 早期用语；AGENTS.md 已无独立 Profile YAML）
    @Option(names = {"--agent", "--profile"}, required = true,
            description = "Agent 名（= .oryxos/agents/<name>/AGENT.md）")
    private String agentName;

    @Option(names = "--session", description = "会话 id（缺省新建会话）")
    private String sessionId;

    @Override
    public Integer call() {
        try {
            cliChannel.runRepl(agentName, sessionId);
            return 0;
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 启动级错误已在 CliChannel 打印，这里只映射退出码（contracts: 退出码 1）
            return 1;
        }
    }
}
