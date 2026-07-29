package com.oryxos.tool.registry;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolCatalog;
import org.springframework.stereotype.Component;

/**
 * 工具注册表（core {@link ToolCatalog} 的实现）：
 * 汇聚容器中全部 OryxTool Bean，按名索引。
 */
@Component
public class ToolRegistry implements ToolCatalog {

    private final java.util.Map<String, OryxTool> toolsByName;

    public ToolRegistry(List<OryxTool> tools) {
        this.toolsByName = tools.stream()
                .collect(Collectors.toUnmodifiableMap(OryxTool::name, Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException("工具名重复注册: " + a.name());
                        }));
    }

    @Override
    public Optional<OryxTool> find(String toolName) {
        return Optional.ofNullable(toolsByName.get(toolName));
    }

    @Override
    public List<OryxTool> findAll(List<String> toolNames) {
        return toolNames.stream().map(name -> find(name)
                        .orElseThrow(() -> new IllegalStateException(
                                "工具未注册: " + name + "（已注册: " + registeredNames() + "）")))
                .toList();
    }

    @Override
    public List<String> registeredNames() {
        return toolsByName.keySet().stream().sorted().toList();
    }
}
