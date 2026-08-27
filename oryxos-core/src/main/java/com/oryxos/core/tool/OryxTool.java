package com.oryxos.core.tool;

/**
 * OryxOS 工具抽象（§6.1 终局四方法形态的最小前进）。
 *
 * <p>第 16 节仅承载 schema 翻译三方法（{@code name/description/getInputSchema}）；第 17 节增补 {@link #execute}--课件
 * 「配角二」ToolExecutor 执行工具所需的统一入口。四方法对齐 §6.1 终局形态； "是否可重试"结果维度由第 20 节 Tool 体系在 {@link ToolResult}
 * 上演进。
 */
public interface OryxTool {

  /** 工具名（provider 翻译 schema 时回传给模型；也是 {@code ToolRegistry} 查找键）。 */
  String name();

  /** 工具的自然语言描述（告诉模型这个工具用来干嘛、什么时候该用它）。 */
  String description();

  /** 工具参数的 JSON Schema（字符串形式）；模型据此判断要传什么参数。 */
  String getInputSchema();

  /**
   * 执行一次工具调用（第 17 节增补：执行权唯一收口在 ToolExecutor，本方法只定义工具自身行为）。
   *
   * @param input 模型给出的 JSON 入参原文
   * @return 统一结果（失败也是业务语义，不抛异常打断对话）
   */
  ToolResult execute(String input);
}
