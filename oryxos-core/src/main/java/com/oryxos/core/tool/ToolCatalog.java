package com.oryxos.core.tool;

import java.util.List;
import java.util.Optional;

/**
 * 工具目录（DIP：core 定义接口，oryxos-tool 的 ToolRegistry 实现）。
 * 供 AgentLoader 校验工具名、ToolExecutor 按名查找。
 */
public interface ToolCatalog {

    Optional<OryxTool> find(String toolName);

    /** 按名批量解析；未知名称原样保留在返回的 unknown 列表外层由调用方校验。 */
    List<OryxTool> findAll(List<String> toolNames);

    /** 已注册的全部工具名（用于报错提示与 Agent 校验）。 */
    List<String> registeredNames();
}
