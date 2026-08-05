package com.oryxos.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.oryxos.core.tool.OryxTool;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

/**
 * {@link ToolSchemaAdapter} 验收 harness（课件「验收 harness」第三个单测类）。
 *
 * <p>覆盖验收点：{@link OryxTool} 的 schema 翻译成 Spring AI 格式后字段一一对齐；**只翻译、产物里不含任何 执行逻辑**。
 */
class ToolSchemaAdapterTest {

  @Test
  @DisplayName("OryxTool 翻译成 Spring AI ToolCallback 后 name/description/inputSchema 字段一一对齐")
  void translatesSchemaFieldsOneToOne() {
    OryxTool tool =
        new OryxTool() {
          @Override
          public String name() {
            return "http_get";
          }

          @Override
          public String description() {
            return "发起一个 HTTP GET 请求并返回响应体文本";
          }

          @Override
          public String getInputSchema() {
            return "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}},\"required\":[\"url\"]}";
          }
        };

    List<ToolCallback> callbacks = new ToolSchemaAdapter().toSpringAiTools(List.of(tool));

    assertEquals(1, callbacks.size());
    var def = callbacks.get(0).getToolDefinition();
    assertEquals("http_get", def.name());
    assertEquals("发起一个 HTTP GET 请求并返回响应体文本", def.description());
    assertEquals(tool.getInputSchema(), def.inputSchema());
  }

  @Test
  @DisplayName("只翻译不执行：ToolCallback.call 被走到即抛 UnsupportedOperationException，不含任何执行逻辑")
  void translateOnlyNoExecution() {
    OryxTool tool =
        new OryxTool() {
          @Override
          public String name() {
            return "noop";
          }

          @Override
          public String description() {
            return "noop";
          }

          @Override
          public String getInputSchema() {
            return "{}";
          }
        };
    ToolCallback cb = new ToolSchemaAdapter().toSpringAiTools(List.of(tool)).get(0);

    // 产物不应被走到执行路径；被调到即抛出暴露事故，绝不悄悄执行
    UnsupportedOperationException ex =
        assertThrows(UnsupportedOperationException.class, () -> cb.call("{}"));
    assertNotNull(ex.getMessage());
  }

  @Test
  @DisplayName("空列表 / null 输入返回空列表（不 NPE、不悄悄塞默认工具）")
  void emptyOrNullReturnsEmpty() {
    ToolSchemaAdapter adapter = new ToolSchemaAdapter();
    assertEquals(List.of(), adapter.toSpringAiTools(null));
    assertEquals(List.of(), adapter.toSpringAiTools(List.of()));
  }
}
