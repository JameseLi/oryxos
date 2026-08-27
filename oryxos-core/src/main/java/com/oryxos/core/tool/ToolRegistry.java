package com.oryxos.core.tool;

import java.util.List;
import java.util.Optional;

/**
 * 工具注册表接口（第 17 节最小形态，spec R1）。
 *
 * <p>课件骨架里 {@code ToolExecutor} "从工具表里找到模型要调的那个工具"、{@code PromptBuilder}
 * 组装"当前可用的工具列表"--这两处消费的就是本接口。统一注册、按名查找、全量列出，ReAct 循环不感知 Tool 的来源 （内置/MCP/注解 Bean）。
 *
 * <p>实现（扫描 {@code @Tool} 注解、MCP Client 注册、Profile 过滤时机）是第 20 节 Tool 体系的交付； 本接口签名按接口中立原则不含任何来源特有词汇。
 */
public interface ToolRegistry {

  /** 按工具名查找（模型回传的 name 即查找键）。 */
  Optional<OryxTool> find(String name);

  /** 全量工具列表（{@code PromptBuilder} 组装可用工具时的数据源）。 */
  List<OryxTool> all();
}
