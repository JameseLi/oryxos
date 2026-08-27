package com.oryxos.core.agent;

import com.oryxos.core.profile.Profile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 上下文供给者（课件「供给者」）：给 PromptBuilder 的第一部分--Bootstrap 文件 + Skill 文本。
 *
 * <p>两条铁律（课件原文）：①每次组装 prompt 都重新读文件、不缓存--用户改完立即生效； ②Profile 里显式引用的文件缺失要报错、 Bootstrap 缺失至少
 * WARN--静默跳过会造成「人格悄悄丢了」这种最难查的软故障。
 *
 * <p>Bootstrap 与 Skill 的缺失处理不同级：Skill 是 Profile 显式点名的能力，丢了就是配置坏了（报错）； Bootstrap 是全局人格底座，本节 Profile
 * 层可选（spec R4 读法：未声明或文件缺失 -> WARN 后跳过，不阻断）。
 */
public class ContextLoader {

  private static final Logger log = LoggerFactory.getLogger(ContextLoader.class);

  private final Path workspaceRoot;

  public ContextLoader(Path workspaceRoot) {
    this.workspaceRoot = workspaceRoot;
  }

  /**
   * 加载 Profile 的上下文文本（Bootstrap + Skill，按 Profile 声明顺序拼接）。
   *
   * @throws IllegalStateException Profile 显式引用的 Skill 文件缺失（消息含文件路径与 Profile 名）
   */
  public String load(Profile profile) {
    StringBuilder sb = new StringBuilder();
    appendBootstrap(profile, sb);
    appendSkills(profile, sb);
    return sb.toString();
  }

  private void appendBootstrap(Profile profile, StringBuilder sb) {
    boolean loadedAny = false;
    if (profile.bootstrap() != null) {
      for (String file : profile.bootstrap().files()) {
        Path path = workspaceRoot.resolve(file);
        if (!Files.isRegularFile(path)) {
          // WARN 不阻断：Bootstrap 缺位只降人格底座，不该让整个 Agent 起不来（spec R4）
          log.warn("Bootstrap 文件缺失，已跳过: {} (profile={})", path, profile.name());
          continue;
        }
        sb.append(readFile(path)).append('\n');
        loadedAny = true;
      }
    }
    if (!loadedAny) {
      log.warn("Profile 未装配任何 Bootstrap 内容 (profile={})", profile.name());
    }
  }

  private void appendSkills(Profile profile, StringBuilder sb) {
    for (String skill : profile.skills()) {
      Path path = workspaceRoot.resolve(skill);
      if (!Files.isRegularFile(path)) {
        // 显式引用缺了 = 配置坏了：报错，绝不静默（否则 Agent 能力悄悄残缺）
        throw new IllegalStateException("Profile '" + profile.name() + "' 引用的 Skill 文件缺失: " + path);
      }
      sb.append(readFile(path)).append('\n');
    }
  }

  // 每次调用现读磁盘、不做任何缓存--铁律①（无缓存回归测试钉死这条）
  private String readFile(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new UncheckedIOException("读取上下文文件失败: " + path, e);
    }
  }
}
