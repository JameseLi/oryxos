package com.oryxos.core.tool;

/**
 * OryxOS 工具抽象（宪法原则五：所有工具统一抽象，签名中立——不出现
 * "builtin/MCP/skill" 等实现来源词）。
 *
 * <p>实现方只暴露三样元数据 + 一个执行入口；调度、审计、沙箱、进度事件
 * 全部归 {@code ToolExecutor} 与 ReActLoop 控制。
 */
public interface OryxTool {

    /** 工具名（LLM 侧可见的唯一标识，如 http_get）。 */
    String name();

    /** 用途说明（注入给 LLM 的工具清单）。 */
    String description();

    /** 入参 JSON Schema 文本（原样发给 LLM）。 */
    String inputSchema();

    /**
     * 执行工具。
     *
     * @param argumentsJson LLM 给出的入参 JSON（可能非法，实现方必须容错并返回失败结果而非抛异常）
     * @return 执行结果（成功/失败语义在结果对象内）
     */
    ToolResult execute(String argumentsJson);
}
