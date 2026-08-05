package com.oryxos.core.tool;

/**
 * OryxOS 工具抽象（最小形态，第 16 节最早一节交付）。
 *
 * <p>本节只用于把工具「参数说明」翻译成模型看得懂的 schema；{@code execute} 不在此——执行由后续节 {@code ToolExecutor}（§17）/ 完整 Tool
 * 体系（§20）补齐。本接口只承载第 16 节 {@code ToolSchemaAdapter} 与 {@code ProviderService.chat} 所需成员 {@code name
 * / description / getInputSchema()}，不越界加方法 （spec R1：后续 §17/§20 扩展时若需改公共接口由对应节课件明列「改造点」裁定）。
 */
public interface OryxTool {

  /** 工具名（provider 翻译 schema 时回传给模型；也是 {@code ToolRegistry} 查找键）。 */
  String name();

  /** 工具的自然语言描述（告诉模型这个工具用来干嘛、什么时候该用它）。 */
  String description();

  /** 工具参数的 JSON Schema（字符串形式）；模型据此判断要传什么参数。 */
  String getInputSchema();
}
