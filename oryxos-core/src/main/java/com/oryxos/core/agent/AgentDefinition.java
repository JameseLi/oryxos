package com.oryxos.core.agent;

import java.nio.file.Path;
import java.util.List;

/**
 * Agent 定义（AGENT.md frontmatter 派生的运行时 Profile + 文件位置）。
 * 正文不进本对象——ContextLoader 每次组装 prompt 热读（宪法：改完即时生效）。
 */
public record AgentDefinition(
        String name,
        String providerName,
        String model,
        List<String> tools,
        Integer maxIterations,
        Path agentFile) {

    /** 本 Agent 的迭代上限：frontmatter 覆盖优先，缺省用全局默认。 */
    public int effectiveMaxIterations(int globalDefault) {
        return maxIterations != null ? maxIterations : globalDefault;
    }
}
