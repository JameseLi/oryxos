package com.oryxos.core.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.oryxos.core.support.FakeToolCatalog;
import com.oryxos.core.workspace.WorkspaceLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AgentLoader 单测：frontmatter 解析与越界引用报错（contracts/agent-definition-schema.md）。 */
class AgentLoaderTest {

    @TempDir
    Path workspaceRoot;

    private AgentLoader loader;

    @BeforeEach
    void setUp() {
        loader = new AgentLoader(WorkspaceLayout.of(workspaceRoot),
                new FakeToolCatalog().register(FakeToolCatalog.echoTool("http_get")));
    }

    private Path writeAgent(String dirName, String content) throws IOException {
        Path dir = workspaceRoot.resolve("agents").resolve(dirName);
        Files.createDirectories(dir);
        Path file = dir.resolve("AGENT.md");
        Files.writeString(file, content);
        return file;
    }

    private static final String VALID = """
            ---
            name: weather
            provider: { name: deepseek, model: deepseek-chat }
            tools: [http_get]
            max_iterations: 5
            ---
            你是天气助手。
            """;

    @Test
    void 合法定义正常解析() throws IOException {
        writeAgent("weather", VALID);

        AgentDefinition def = loader.load("weather");

        assertThat(def.name()).isEqualTo("weather");
        assertThat(def.providerName()).isEqualTo("deepseek");
        assertThat(def.model()).isEqualTo("deepseek-chat");
        assertThat(def.tools()).containsExactly("http_get");
        assertThat(def.effectiveMaxIterations(10)).isEqualTo(5);
    }

    @Test
    void 无max_iterations时用全局默认() throws IOException {
        writeAgent("weather", VALID.replace("max_iterations: 5\n", ""));

        assertThat(loader.load("weather").effectiveMaxIterations(10)).isEqualTo(10);
    }

    @Test
    void Agent不存在时报可用列表() {
        assertThatThrownBy(() -> loader.load("ghost"))
                .isInstanceOf(AgentLoader.AgentNotFoundException.class)
                .hasMessageContaining("ghost")
                .hasMessageContaining("可用");
    }

    @Test
    void name与目录名不一致报错() throws IOException {
        writeAgent("weather", VALID.replace("name: weather", "name: other"));

        assertThatThrownBy(() -> loader.load("weather"))
                .isInstanceOf(AgentLoader.AgentDefinitionException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    void 缺provider点name报错() throws IOException {
        writeAgent("weather", VALID.replace("provider: { name: deepseek, model: deepseek-chat }",
                "provider: { model: deepseek-chat }"));

        assertThatThrownBy(() -> loader.load("weather"))
                .isInstanceOf(AgentLoader.AgentDefinitionException.class)
                .hasMessageContaining("provider.name");
    }

    @Test
    void 引用未注册工具报错并列出已注册() throws IOException {
        writeAgent("weather", VALID.replace("tools: [http_get]", "tools: [http_get, shell]"));

        assertThatThrownBy(() -> loader.load("weather"))
                .isInstanceOf(AgentLoader.AgentDefinitionException.class)
                .hasMessageContaining("shell")
                .hasMessageContaining("http_get");
    }

    @Test
    void max_iterations越界报错() throws IOException {
        writeAgent("weather", VALID.replace("max_iterations: 5", "max_iterations: 0"));

        assertThatThrownBy(() -> loader.load("weather"))
                .isInstanceOf(AgentLoader.AgentDefinitionException.class)
                .hasMessageContaining("1~50");
    }
}
