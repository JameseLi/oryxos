package com.oryxos.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.oryxos.core.workspace.WorkspaceLayout;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * oryxos init：生成 .oryxos/ 工作区（幂等，已存在的文件跳过不覆盖）。
 */
@Component
@Command(name = "init", description = "初始化 .oryxos/ 工作区（agents/memory/sessions/logs + Bootstrap 文件）")
public class InitCommand implements Callable<Integer> {

    private static final String MEMORY_MD = """
            # MEMORY.md — OryxOS 长期记忆

            ## 核心记忆区
            （永不截断，全量注入 system prompt）

            ## 归档记忆区
            （超出长度时截断，参与关键词检索）
            """;

    private static final String MCP_SERVERS_YAML = """
            # MCP server 配置（US-4 Tool 体系特性启用，本文件当前为占位）
            # servers:
            #   - name: example
            #     command: npx
            #     args: ["-y", "@modelcontextprotocol/server-example"]
            """;

    private static final String AGENTS_MD = """
            # AGENTS.md — 工作区级协作约定（Bootstrap 文件，注入所有 Agent 的 system prompt）
            你是运行在 OryxOS 上的企业业务 Agent。回答使用与用户相同的语言，简洁专业。
            """;

    private static final String SOUL_MD = """
            # SOUL.md — 底座性格（Bootstrap 文件）
            稳重、可靠、可审计。不编造数据；需要外部数据时使用可用工具获取。
            """;

    private static final String USER_MD = """
            # USER.md — 用户偏好（Bootstrap 文件，可按需填写）
            """;

    private final WorkspaceLayout workspace;

    public InitCommand(WorkspaceLayout workspace) {
        this.workspace = workspace;
    }

    @Option(names = "--dir", description = "工作区路径（默认当前目录下 .oryxos）")
    private String dir;

    @Override
    public Integer call() {
        WorkspaceLayout layout = dir == null ? workspace : WorkspaceLayout.of(Path.of(dir));
        try {
            for (Path requiredDir : layout.requiredDirs()) {
                Files.createDirectories(requiredDir);
            }
            writeIfAbsent(layout.memoryFile(), MEMORY_MD);
            writeIfAbsent(layout.root().resolve("mcp_servers.yaml"), MCP_SERVERS_YAML);
            writeIfAbsent(layout.root().resolve("AGENTS.md"), AGENTS_MD);
            writeIfAbsent(layout.root().resolve("SOUL.md"), SOUL_MD);
            writeIfAbsent(layout.root().resolve("USER.md"), USER_MD);
        } catch (IOException e) {
            System.err.println("init 失败: " + e.getMessage());
            return 1;
        }
        System.out.println("工作区就绪: " + layout.root().toAbsolutePath());
        System.out.println("下一步：在 " + layout.agentsDir() + "/<name>/AGENT.md 定义你的第一个 Agent");
        return 0;
    }

    private static void writeIfAbsent(Path path, String content) throws IOException {
        if (Files.exists(path)) {
            System.out.println("跳过（已存在）: " + path);
            return;
        }
        try {
            Files.writeString(path, content);
            System.out.println("创建: " + path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
