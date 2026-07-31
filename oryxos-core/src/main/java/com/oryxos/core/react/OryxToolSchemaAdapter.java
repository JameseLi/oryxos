package com.oryxos.core.react;

import com.oryxos.core.tool.OryxTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * OryxTool → Spring AI ToolCallback 适配器（宪法原则四红线）： <b>仅作 JSON Schema 载体</b>，让工具定义随请求发给模型； {@link
 * #call} 永不执行——直接调用 ChatModel 时 Spring AI 不会自动执行 tool call， 且我们在 options 中显式
 * internalToolExecutionEnabled(false) 双保险。
 */
public class OryxToolSchemaAdapter implements ToolCallback {

  private final OryxTool tool;

  public OryxToolSchemaAdapter(OryxTool tool) {
    this.tool = tool;
  }

  @Override
  public ToolDefinition getToolDefinition() {
    return ToolDefinition.builder()
        .name(tool.name())
        .description(tool.description())
        .inputSchema(tool.inputSchema())
        .build();
  }

  @Override
  public ToolMetadata getToolMetadata() {
    return ToolMetadata.builder().build();
  }

  /**
   * 禁止调用：工具执行只能走 OryxOS 自己的 ToolExecutor（调度 + 沙箱 + 审计）。 若此方法被触发，说明 Spring AI 自动执行链被意外启用——必须立即失败暴露。
   */
  @Override
  public String call(String toolInput) {
    throw new UnsupportedOperationException(
        "宪法原则四：工具执行由 OryxOS ToolExecutor 控制，Spring AI 自动执行已禁用: " + tool.name());
  }
}
