package com.oryxos.core.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.oryxos.core.audit.ToolInvocationAudit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

/**
 * {@link ToolExecutor} 验收 harness（课件「验收 harness」第三个单测类）。
 *
 * <p>覆盖验收点：成功写审计 {@code success=true}；失败也写 {@code success=false} 带原因，异常不吞。
 */
class ToolExecutorTest {

  private final ToolInvocationAudit audit = mock(ToolInvocationAudit.class);

  @Test
  @DisplayName("成功写审计 success=true：record 参数逐项对齐（会话/工具名/入参/结果/耗时）")
  void successWritesAuditWithSuccessTrue() {
    OryxTool httpGet = new FakeTool("http_get", ToolResult.ok("{\"temp\":25}"));
    ToolExecutor executor = new ToolExecutor(new FixedRegistry(httpGet), audit);

    ToolResult result =
        executor.execute("s-1", toolCall("http_get", "{\"url\":\"https://weather.com\"}"));

    assertTrue(result.success());
    assertEquals("{\"temp\":25}", result.output());
    verify(audit)
        .record(
            eq("s-1"),
            eq("http_get"),
            eq("{\"url\":\"https://weather.com\"}"),
            eq("{\"temp\":25}"),
            eq(true),
            isNull(),
            anyLong());
  }

  @Test
  @DisplayName("失败也写审计 success=false 带原因，异常不吞：error_message 记原因、日志留痕、失败文本回填")
  void failureWritesAuditWithSuccessFalseAndReason() {
    OryxTool failing =
        new FakeTool("shell", null) {
          @Override
          public ToolResult execute(String input) {
            throw new RuntimeException("command not allowed");
          }
        };
    ToolExecutor executor = new ToolExecutor(new FixedRegistry(failing), audit);

    ToolResult result = executor.execute("s-2", toolCall("shell", "{\"cmd\":\"rm -rf /\"}"));

    assertFalse(result.success());
    assertEquals("command not allowed", result.errorMessage());
    assertTrue(result.toModelText().startsWith("ERROR:"), "失败文本回填模型，前缀 ERROR");
    verify(audit)
        .record(
            eq("s-2"),
            eq("shell"),
            eq("{\"cmd\":\"rm -rf /\"}"),
            isNull(),
            eq(false),
            eq("command not allowed"),
            anyLong());
  }

  @Test
  @DisplayName("未知工具走失败路径：审计 success=false、原因含「未知工具」，不打断对话")
  void unknownToolFallsIntoFailurePath() {
    ToolExecutor executor = new ToolExecutor(new FixedRegistry(), audit);

    ToolResult result = executor.execute("s-3", toolCall("no_such_tool", "{}"));

    assertFalse(result.success());
    assertTrue(result.errorMessage().contains("未知工具"), "原因要指明未知工具: " + result.errorMessage());
    verify(audit)
        .record(
            eq("s-3"),
            eq("no_such_tool"),
            eq("{}"),
            isNull(),
            eq(false),
            contains("未知工具"),
            anyLong());
  }

  @Test
  @DisplayName("执行结果原样返回给循环回填（executor 不改写工具输出）")
  void returnsToolResultAsIs() {
    ToolResult raw = ToolResult.ok("done");
    ToolExecutor executor =
        new ToolExecutor(new FixedRegistry(new FakeTool("read_file", raw)), audit);

    ToolResult result = executor.execute("s-4", toolCall("read_file", "{\"path\":\"a.txt\"}"));

    assertEquals(raw, result);
    assertNull(result.errorMessage());
  }

  private static AssistantMessage.ToolCall toolCall(String name, String arguments) {
    return new AssistantMessage.ToolCall("call-1", "function", name, arguments);
  }

  /** 固定工具集的测试注册表（varargs 构造，内部存不可变 List）。 */
  private static final class FixedRegistry implements ToolRegistry {

    private final List<OryxTool> tools;

    FixedRegistry(OryxTool... tools) {
      this.tools = List.of(tools);
    }

    @Override
    public Optional<OryxTool> find(String name) {
      return tools.stream().filter(t -> t.name().equals(name)).findFirst();
    }

    @Override
    public List<OryxTool> all() {
      return tools;
    }
  }

  /** 固定返回预设结果的测试工具。 */
  private static class FakeTool implements OryxTool {

    private final String name;
    private final ToolResult result;

    FakeTool(String name, ToolResult result) {
      this.name = name;
      this.result = result;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public String description() {
      return "测试工具 " + name;
    }

    @Override
    public String getInputSchema() {
      return "{}";
    }

    @Override
    public ToolResult execute(String input) {
      if (result == null) {
        throw new IllegalStateException("未配置结果");
      }
      return result;
    }
  }
}
