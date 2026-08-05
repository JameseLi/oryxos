package com.oryxos.provider;

import com.oryxos.core.tool.OryxTool;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

/**
 * 工具格式适配器（课件「第四步」）：把 OryxOS 内部的 {@link OryxTool} 翻译成 Spring AI {@link ToolCallback}
 * ——<b>只翻译、不执行</b>。
 *
 * <p>{@link ToolCallback} 是 Spring AI 发给模型时承载工具定义（name + description + inputSchema）的载体； 真正执行走后续节
 * OryxOS 自己的 {@code ToolExecutor}。本适配器产出的 {@link SchemaOnlyToolCallback}：
 *
 * <ul>
 *   <li>{@code getToolDefinition()} 返回 {@link OryxTool#name()} / {@link OryxTool#description()} /
 *       {@link OryxTool#getInputSchema()} 翻译出的 {@link ToolDefinition}（字段一一对齐）。
 *   <li>{@code call(String)} 不实现执行——按课件「只翻译不执行」，此路径不应被走到（Spring AI 自动执行已由 {@code ProviderService}
 *       关闭）；若意外被调到，抛 {@link UnsupportedOperationException} 把事故暴露出来， 不悄悄吞掉。
 * </ul>
 *
 * <p>产物里不含任何执行逻辑——这是课件点名的回归保护点（{@code ToolSchemaAdapterTest} 断言）。
 */
@Component
public class ToolSchemaAdapter {

  /** 把一批 {@link OryxTool} 翻译成 Spring AI {@link ToolCallback} 列表（随请求发给模型）。 */
  public List<ToolCallback> toSpringAiTools(List<OryxTool> tools) {
    List<ToolCallback> out = new ArrayList<>();
    if (tools == null) {
      return out;
    }
    for (OryxTool t : tools) {
      out.add(new SchemaOnlyToolCallback(t));
    }
    return out;
  }

  /** 仅承载 schema 的 ToolCallback——执行路径被故意短路（见类注释）。 */
  static final class SchemaOnlyToolCallback implements ToolCallback {

    private final ToolDefinition definition;

    SchemaOnlyToolCallback(OryxTool tool) {
      this.definition =
          DefaultToolDefinition.builder()
              .name(tool.name())
              .description(tool.description())
              .inputSchema(tool.getInputSchema())
              .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
      return definition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
      return ToolMetadata.builder().build();
    }

    /** 课件「只翻译不执行」：执行路径不应被走到（Spring AI 自动执行已关）。被调到即抛出暴露事故。 */
    @Override
    public String call(String toolInput) {
      throw new UnsupportedOperationException(
          "ToolSchemaAdapter 是 schema 载体，不执行工具；执行应由 OryxOS ToolExecutor 接管。");
    }

    @Override
    public String call(
        String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
      throw new UnsupportedOperationException(
          "ToolSchemaAdapter 是 schema 载体，不执行工具；执行应由 OryxOS ToolExecutor 接管。");
    }
  }
}
