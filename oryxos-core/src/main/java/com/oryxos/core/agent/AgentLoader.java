package com.oryxos.core.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.oryxos.core.tool.ToolCatalog;
import com.oryxos.core.workspace.WorkspaceLayout;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * AGENT.md 加载器：解析 frontmatter 派生 AgentDefinition（不再手写独立 Profile YAML）。
 * 校验规则见 contracts/agent-definition-schema.md：name 与目录一致、provider 必填、
 * tools 必须已注册、max_iterations 1~50。
 */
@Component
public class AgentLoader {

    private static final int MAX_ITERATIONS_CEILING = 50;

    private final WorkspaceLayout workspace;
    private final ToolCatalog toolCatalog;

    public AgentLoader(WorkspaceLayout workspace, ToolCatalog toolCatalog) {
        this.workspace = workspace;
        this.toolCatalog = toolCatalog;
    }

    public AgentDefinition load(String agentName) {
        Path agentFile = workspace.agentFile(agentName);
        if (!Files.isRegularFile(agentFile)) {
            throw new AgentNotFoundException(agentName, listAgentNames());
        }
        String text = readText(agentFile);
        Map<String, Object> frontmatter = parseFrontmatter(text, agentFile);
        return toDefinition(agentName, agentFile, frontmatter);
    }

    /** 列出可用 Agent 名（= agents/ 下的目录名），用于报错提示。 */
    public List<String> listAgentNames() {
        Path agentsDir = workspace.agentsDir();
        if (!Files.isDirectory(agentsDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(agentsDir)) {
            return stream.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("读取 agents 目录失败: " + agentsDir, e);
        }
    }

    private AgentDefinition toDefinition(String agentName, Path agentFile, Map<String, Object> fm) {
        String name = stringField(fm, "name", true);
        if (!agentName.equals(name)) {
            throw new AgentDefinitionException(
                    "AGENT.md 的 name '" + name + "' 与目录名 '" + agentName + "' 不一致: " + agentFile);
        }
        Map<String, Object> provider = mapField(fm, "provider");
        String providerName = provider == null ? null : stringField(provider, "name", true);
        String model = provider == null ? null : stringField(provider, "model", true);
        if (providerName == null || providerName.isBlank()) {
            throw new AgentDefinitionException("AGENT.md 缺少 provider.name: " + agentFile);
        }
        if (model == null || model.isBlank()) {
            throw new AgentDefinitionException("AGENT.md 缺少 provider.model: " + agentFile);
        }
        List<String> tools = stringListField(fm, "tools");
        List<String> unknown = tools.stream().filter(t -> toolCatalog.find(t).isEmpty()).toList();
        if (!unknown.isEmpty()) {
            throw new AgentDefinitionException("AGENT.md 引用了未注册的工具 " + unknown
                    + "，已注册工具: " + toolCatalog.registeredNames());
        }
        Integer maxIterations = null;
        Object rawMax = fm.get("max_iterations");
        if (rawMax != null) {
            if (!(rawMax instanceof Number number)) {
                throw new AgentDefinitionException("max_iterations 必须是数字: " + agentFile);
            }
            maxIterations = number.intValue();
            if (maxIterations < 1 || maxIterations > MAX_ITERATIONS_CEILING) {
                throw new AgentDefinitionException(
                        "max_iterations 必须在 1~" + MAX_ITERATIONS_CEILING + " 之间: " + agentFile);
            }
        }
        return new AgentDefinition(name, providerName, model, tools, maxIterations, agentFile);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFrontmatter(String text, Path agentFile) {
        String normalized = text.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            throw new AgentDefinitionException("AGENT.md 缺少 YAML frontmatter（文件须以 --- 开头）: " + agentFile);
        }
        int end = normalized.indexOf("\n---", 4);
        if (end < 0) {
            throw new AgentDefinitionException("AGENT.md frontmatter 未闭合（缺少结束 ---）: " + agentFile);
        }
        String yamlText = normalized.substring(4, end);
        try {
            Object parsed = new Yaml().load(yamlText);
            if (parsed == null) {
                throw new AgentDefinitionException("AGENT.md frontmatter 为空: " + agentFile);
            }
            if (!(parsed instanceof Map)) {
                throw new AgentDefinitionException("AGENT.md frontmatter 必须是 YAML 对象: " + agentFile);
            }
            return (Map<String, Object>) parsed;
        } catch (YAMLException e) {
            throw new AgentDefinitionException("AGENT.md frontmatter YAML 非法: " + agentFile + " - " + e.getMessage());
        }
    }

    static String readText(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("读取文件失败: " + path, e);
        }
    }

    private static String stringField(Map<String, Object> map, String key, boolean required) {
        Object value = map.get(key);
        if (value == null) {
            if (required) {
                return null;
            }
            return null;
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapField(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static List<String> stringListField(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    /** Agent 不存在（附可用列表）。 */
    public static class AgentNotFoundException extends RuntimeException {
        public AgentNotFoundException(String agentName, List<String> available) {
            super("Agent 不存在: " + agentName + "（可用: " + (available.isEmpty() ? "<无>" : available) + "）");
        }
    }

    /** AGENT.md 定义非法。 */
    public static class AgentDefinitionException extends RuntimeException {
        public AgentDefinitionException(String message) {
            super(message);
        }
    }
}
