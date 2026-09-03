package com.oryxos.tool.registry;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.ai.util.json.JsonParser;

/**
 * 注解工具适配器（本节交付物）：把 Spring AI {@code @Tool} 方法包装成 {@link OryxTool} 统一形态。
 *
 * <p>三个契约方法直接映射 {@code ToolDefinition} 的 {@code name()/description()/inputSchema()}（已 javap
 * 核实同构，schema 由 Spring AI 的 {@code @Tool}/{@code @ToolParam} 注解生成——原则四「允许的一半」， 参数名进 schema 依赖编译器
 * {@code -parameters}）。
 *
 * <p>执行与异常语义：{@code execute} 正常返回包 {@link ToolResult#ok}；Spring AI 会把方法抛出的异常包成 {@link
 * ToolExecutionException}（原异常为 cause），这里<b>解包重抛</b>原 RuntimeException——课件 harness 的 {@code
 * assertThrows(SandboxViolationException)} 依赖这个保真度；最终由 17 节 {@code ToolExecutor}
 * 统一转失败结果并落审计（异常不吞铁律）。
 *
 * <p>结果转换（为什么不用默认 converter）：Spring AI 默认把一切结果 JSON 编码，String 返回会被引号包裹 （{@code "beijing sunny"} 变成
 * {@code "\"beijing sunny\""}），文本型工具的响应体全部带引号噪声。 {@link #STRING_PRESERVING_CONVERTER} 对 String
 * 保真直传、其余（如 19 节 NotifyTools 的 {@code ToolResult} 返回形态）仍走 {@code JsonParser.toJson}
 * 序列化——两种返回形态对模型都可读。
 *
 * <p>原则四红线：ToolCallback 绝不接入任何 ChatModel，执行唯一收口 OryxOS ToolExecutor， 不存在自动 tool 执行路径。
 */
public class AnnotatedToolAdapter implements OryxTool {

  /** String 保真的结果转换器：String 直传（文本型工具），其余 JSON 序列化（对象型工具）。 */
  private static final ToolCallResultConverter STRING_PRESERVING_CONVERTER =
      new ToolCallResultConverter() {
        @Override
        public String convert(Object result, Type returnType) {
          if (result instanceof String) {
            return (String) result;
          }
          return JsonParser.toJson(result);
        }
      };

  private final ToolCallback callback;

  public AnnotatedToolAdapter(ToolCallback callback) {
    this.callback = callback;
  }

  /**
   * 扫描一个工具 Bean 的全部 {@code @Tool} 方法并包装成适配器列表（注册表与测试共用同一构造路径， 避免 MethodToolCallbackProvider 默认
   * converter 的 String 引号噪声）。一个 {@code @Tool} 方法都没有 直接抛错——装配错位尽早暴露。
   */
  public static List<AnnotatedToolAdapter> forToolBean(Object toolBean) {
    List<AnnotatedToolAdapter> adapters = new ArrayList<>();
    for (Method method : toolBean.getClass().getDeclaredMethods()) {
      if (method.isAnnotationPresent(Tool.class)) {
        ToolDefinition definition = ToolDefinitions.from(method);
        MethodToolCallback callback =
            new MethodToolCallback(definition, null, method, toolBean, STRING_PRESERVING_CONVERTER);
        adapters.add(new AnnotatedToolAdapter(callback));
      }
    }
    if (adapters.isEmpty()) {
      throw new IllegalStateException(
          "工具 Bean '" + toolBean.getClass().getSimpleName() + "' 没有任何 @Tool 方法（装配错位）");
    }
    return adapters;
  }

  @Override
  public String name() {
    return callback.getToolDefinition().name();
  }

  @Override
  public String description() {
    return callback.getToolDefinition().description();
  }

  @Override
  public String getInputSchema() {
    return callback.getToolDefinition().inputSchema();
  }

  @Override
  public ToolResult execute(String input) {
    try {
      return ToolResult.ok(callback.call(input));
    } catch (ToolExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      throw e;
    }
  }
}
