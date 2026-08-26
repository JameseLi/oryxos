package com.oryxos.cli.command;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine.Command;

/**
 * {@code oryxos provider list}（第 18 节轻命令）：列出全局层 {@code oryxos.providers}（application.yaml）已声明的
 * provider。
 *
 * <p>轻命令不启动 Spring：直接读 classpath 的 {@code application.yaml} 首个 YAML 文档（文件带 {@code ---} 分段的 profile
 * 文档，取首个即可--provider 全局层声明在主文档）。读不到（如模块单独运行无该资源）给清晰提示。
 */
@Command(mixinStandardHelpOptions = true, name = "list", description = "列出全局层已声明的 LLM Provider")
public final class ProviderListCommand implements Runnable {

  @Override
  public void run() {
    Object firstDoc;
    try (InputStream in = ProviderListCommand.class.getResourceAsStream("/application.yaml")) {
      if (in == null) {
        System.out.println("未找到 application.yaml（classpath 缺失），无法列出 provider");
        return;
      }
      // loadAll 处理多文档 YAML，取首个文档（主配置；后续 --- 段是 profile 专属）
      firstDoc = new Yaml().loadAll(in).iterator().next();
    } catch (IOException | RuntimeException e) {
      System.out.println("读取 application.yaml 失败: " + e.getMessage());
      return;
    }
    if (!(firstDoc instanceof Map<?, ?> root)) {
      System.out.println("application.yaml 格式异常（顶层不是对象）");
      return;
    }
    Object oryxos = root.get("oryxos");
    if (!(oryxos instanceof Map<?, ?> oryxosMap)
        || !(oryxosMap.get("providers") instanceof List<?> providers)
        || providers.isEmpty()) {
      System.out.println("全局层未声明任何 provider（oryxos.providers 为空）");
      return;
    }
    for (Object item : providers) {
      if (item instanceof Map<?, ?> provider) {
        System.out.printf(
            "%-12s type=%-18s model=%s%n",
            provider.get("name"), provider.get("type"), provider.get("model"));
      }
    }
    System.out.println("（凭证走环境变量占位，本命令不显示；可编辑 application.yaml 增删）");
  }
}
