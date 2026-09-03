package com.oryxos.tool.registry;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolRegistry;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一注册表真实现（本节交付物，替换 18 节 {@code EmptyToolRegistry} 过渡 Bean）。
 *
 * <p>三种来源的工具全部以 {@link OryxTool} 身份注册进同一张表：内置/注解 Bean 经 {@link #registerAnnotated(Object...)}（课件
 * 6.5「在装配处多 registerAnnotated 两行」的原话入口）、MCP 工具经 {@link #register}（{@code McpClientService}
 * 包装后逐个注册）。 {@code find}/{@code all} 语义与 17 节接口 零偏差--ReAct 循环不感知 Tool 来源。
 *
 * <p>重名注册抛 {@link IllegalStateException}：拒绝静默覆盖（两个来源同名工具时，宁可启动失败也不悄悄 吞掉一个）。
 */
public class DefaultToolRegistry implements ToolRegistry {

  private final ConcurrentHashMap<String, OryxTool> tools = new ConcurrentHashMap<>();

  @Override
  public Optional<OryxTool> find(String name) {
    return Optional.ofNullable(tools.get(name));
  }

  /** 全量工具快照，按名排序（tool list 展示与 prompt 组装顺序确定性）。 */
  @Override
  public List<OryxTool> all() {
    return tools.values().stream().sorted(Comparator.comparing(OryxTool::name)).toList();
  }

  /** 注册一个工具；重名抛 {@link IllegalStateException}（拒绝静默覆盖）。 */
  public void register(OryxTool tool) {
    if (tool == null || tool.name() == null || tool.name().isBlank()) {
      throw new IllegalArgumentException("注册的工具及其名称不能为空");
    }
    OryxTool existing = tools.putIfAbsent(tool.name(), tool);
    if (existing != null) {
      throw new IllegalStateException("工具重名注册被拒绝: '" + tool.name() + "' 已存在（来源之间不允许静默覆盖）");
    }
  }

  /** 批量注册（MCP 连接后逐个注册的便捷入口）。 */
  public void registerAll(List<? extends OryxTool> tools) {
    if (tools != null) {
      tools.forEach(this::register);
    }
  }

  /**
   * 注解工具注册（课件 6.5 原话入口）：逐 Bean 经 Spring AI {@code MethodToolCallbackProvider} 生成 {@code
   * ToolCallback}（含 {@code @Tool}/{@code @ToolParam} 的 JSON Schema），包成 {@link AnnotatedToolAdapter}
   * 进表。Bean 内没有 {@code @Tool} 方法会直接抛错（装配错位尽早暴露）。
   */
  public void registerAnnotated(Object... toolBeans) {
    if (toolBeans == null) {
      return;
    }
    for (Object toolBean : toolBeans) {
      if (toolBean == null) {
        throw new IllegalArgumentException("registerAnnotated 的工具 Bean 不能为 null");
      }
      // AnnotatedToolAdapter.forToolBean：String 保真 converter + @Tool 方法扫描，与测试共用同一构造路径
      registerAll(AnnotatedToolAdapter.forToolBean(toolBean));
    }
  }
}
