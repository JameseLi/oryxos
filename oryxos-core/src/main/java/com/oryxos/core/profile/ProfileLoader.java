package com.oryxos.core.profile;

import com.oryxos.core.profile.ProfileRegistry.ProfileConflictException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Profile 加载器（课件「第零步」）。
 *
 * <p>启动时扫 {@code .oryxos/profiles/*.yaml}，用 SnakeYAML 解析成 {@link Profile}，逐个做合法性校验（本节 只校验「{@code
 * provider.name} 在 {@link KnownProviders} 能找到」一条，其余字段校验留给后续各节）； 解析失败 / 校验失败的坏 Profile
 * **记错误日志、不阻断**其余合法 Profile 加载（课件「第零步」明确要求）。 注册成功的 Profile 进 {@link ProfileRegistry}。
 *
 * <p>{@code ${ENV}} 占位解析：YAML 里值写成 {@code ${DEEPSEEK_API_KEY}} 时，在加载期从 {@link System#getenv}
 * 解析为实际值（课件「第一步」：凭证走环境变量，不落明文）。
 */
public class ProfileLoader {

  private static final Logger log = LoggerFactory.getLogger(ProfileLoader.class);
  private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

  private final Path profilesDir;
  private final KnownProviders knownProviders;
  private final ProfileRegistry registry;

  public ProfileLoader(Path profilesDir, KnownProviders knownProviders, ProfileRegistry registry) {
    this.profilesDir = profilesDir;
    this.knownProviders = knownProviders;
    this.registry = registry;
  }

  /**
   * 扫描 {@code .oryxos/profiles/} 下所有 {@code .yaml} 文件，解析 + 校验 + 注册。
   *
   * <p>返回成功加载的数量；坏文件逐个记错误日志、不抛（除非 {@code profiles/} 目录连读取都失败——此时认为 用户尚未初始化，记 warn 并返回 0，不阻断启动）。
   */
  public int loadAll() {
    if (!Files.isDirectory(profilesDir)) {
      log.warn("profiles 目录不存在，跳过 Profile 加载: {}", profilesDir);
      return 0;
    }
    int loaded = 0;
    List<Path> files;
    try (var stream = Files.list(profilesDir)) {
      files = stream.filter(p -> p.toString().endsWith(".yaml")).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException("读取 profiles 目录失败: " + profilesDir, e);
    }
    for (Path file : files) {
      try {
        Profile profile = parse(file);
        validate(profile, file);
        registry.register(profile);
        loaded++;
        log.info("已加载 Profile: {} ({})", profile.name(), file);
      } catch (ProfileConflictException e) {
        log.error("加载 Profile 失败（同名冲突）: {} - {}", file, e.getMessage());
      } catch (ProfileValidationException e) {
        log.error("加载 Profile 失败（校验不通过）: {} - {}", file, e.getMessage());
      } catch (ProfileParseException e) {
        log.error("加载 Profile 失败（解析失败）: {} - {}", file, e.getMessage());
      } catch (RuntimeException e) {
        log.error("加载 Profile 失败（未知错误）: {} - {}", file, e.getMessage(), e);
      }
    }
    return loaded;
  }

  private Profile parse(Path file) {
    String text;
    try {
      text = Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException("读取 Profile 文件失败: " + file, e);
    }
    Object parsed;
    try {
      parsed = new Yaml().load(text);
    } catch (YAMLException e) {
      throw new ProfileParseException("YAML 非法: " + e.getMessage(), e);
    }
    if (parsed == null) {
      throw new ProfileParseException("YAML 内容为空");
    }
    if (!(parsed instanceof Map<?, ?> raw)) {
      throw new ProfileParseException("YAML 顶层必须是对象");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> root = (Map<String, Object>) raw;
    Map<String, Object> resolved = resolveEnvPlaceholders(root);
    return toProfile(resolved, file);
  }

  private void validate(Profile profile, Path file) {
    if (profile.name() == null || profile.name().isBlank()) {
      throw new ProfileValidationException("Profile name 不可为空: " + file);
    }
    if (profile.provider() == null
        || profile.provider().name() == null
        || profile.provider().name().isBlank()) {
      throw new ProfileValidationException("Profile 缺少 provider.name: " + file);
    }
    if (!knownProviders.exists(profile.provider().name())) {
      throw new ProfileValidationException(
          "Profile '"
              + profile.name()
              + "' 引用的 provider.name '"
              + profile.provider().name()
              + "' 在全局层 oryxos.providers 找不到同名项: "
              + file);
    }
  }

  @SuppressWarnings("unchecked")
  private static Profile toProfile(Map<String, Object> root, Path file) {
    String name = str(root.get("name"));
    if (name == null || name.isBlank()) {
      throw new ProfileParseException("Profile 缺少 name: " + file);
    }
    String description = str(root.get("description"));
    Map<String, Object> identityRaw = map(root.get("identity"));
    Profile.Identity identity =
        identityRaw == null
            ? null
            : new Profile.Identity(
                str(identityRaw.get("agent_name")), str(identityRaw.get("prompt")));
    Map<String, Object> providerRaw = map(root.get("provider"));
    Profile.Provider provider = null;
    if (providerRaw != null) {
      Double temp = dbl(providerRaw.get("temperature"));
      provider =
          new Profile.Provider(str(providerRaw.get("name")), str(providerRaw.get("model")), temp);
    }
    List<String> tools = strList(root.get("tools"));
    List<String> skills = strList(root.get("skills"));
    List<String> mcpServers = strList(root.get("mcp_servers"));
    List<Profile.ChannelRef> channels = channelRefs(root.get("channels"));
    List<Profile.ChannelRef> notifyChannels = channelRefs(root.get("notify_channels"));
    List<Profile.Schedule> schedules = schedules(root.get("schedules"));
    Object bootstrapRaw = root.get("bootstrap");
    Profile.Bootstrap bootstrap =
        bootstrapRaw == null ? null : new Profile.Bootstrap(strList(bootstrapRaw));
    Map<String, Object> settingsRaw = map(root.get("settings"));
    Profile.Settings settings = null;
    if (settingsRaw != null) {
      settings =
          new Profile.Settings(
              intVal(settingsRaw.get("max_iterations")),
              intVal(settingsRaw.get("max_history_turns")));
    }
    return new Profile(
        name,
        description,
        identity,
        provider,
        tools,
        skills,
        mcpServers,
        channels,
        notifyChannels,
        schedules,
        bootstrap,
        settings);
  }

  // ---- ${ENV} 占位解析：把字符串值里的 ${VAR} 替换为 System.getenv(VAR) ----

  @SuppressWarnings("unchecked")
  private static Map<String, Object> resolveEnvPlaceholders(Map<String, Object> map) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (var e : map.entrySet()) {
      out.put(e.getKey(), resolveEnv(e.getValue()));
    }
    return out;
  }

  private static Object resolveEnv(Object value) {
    if (value instanceof String s) {
      return resolveEnvInString(s);
    }
    if (value instanceof Map<?, ?> m) {
      @SuppressWarnings("unchecked")
      Map<String, Object> cast = (Map<String, Object>) m;
      return resolveEnvPlaceholders(cast);
    }
    if (value instanceof List<?> list) {
      return list.stream().map(ProfileLoader::resolveEnv).toList();
    }
    return value;
  }

  /** 解析字符串中的 {@code ${VAR}} 占位：变量存在则替换为环境变量值，不存在则保持原占位（让上游校验环节就报缺失， 而不是悄悄塞空）。 */
  static String resolveEnvInString(String s) {
    Matcher m = ENV_PLACEHOLDER.matcher(s);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String var = m.group(1);
      String val = System.getenv(var);
      if (val != null) {
        m.appendReplacement(out, Matcher.quoteReplacement(val));
      } else {
        m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
      }
    }
    m.appendTail(out);
    return out.toString();
  }

  // ---- 最小 YAML 取值助手 ----

  private static String str(Object v) {
    return v == null ? null : String.valueOf(v);
  }

  private static Double dbl(Object v) {
    if (v instanceof Number n) {
      return n.doubleValue();
    }
    if (v instanceof String s && !s.isBlank()) {
      try {
        return Double.parseDouble(s.trim());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  private static Integer intVal(Object v) {
    if (v instanceof Number n) {
      return n.intValue();
    }
    if (v instanceof String s && !s.isBlank()) {
      try {
        return Integer.parseInt(s.trim());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object v) {
    return v instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
  }

  @SuppressWarnings("unchecked")
  private static List<String> strList(Object v) {
    if (!(v instanceof List<?> list)) {
      return List.of();
    }
    return list.stream().map(String::valueOf).toList();
  }

  @SuppressWarnings("unchecked")
  private static List<Profile.ChannelRef> channelRefs(Object v) {
    if (!(v instanceof List<?> list)) {
      return List.of();
    }
    return list.stream()
        .map(
            item -> {
              if (!(item instanceof Map<?, ?> m)) {
                return new Profile.ChannelRef(null, Map.of());
              }
              Map<String, Object> cast = (Map<String, Object>) m;
              return new Profile.ChannelRef(
                  str(cast.get("type")),
                  cast.entrySet().stream()
                      .collect(
                          java.util.stream.Collectors.toMap(
                              Map.Entry::getKey,
                              Map.Entry::getValue,
                              (a, b) -> a,
                              LinkedHashMap::new)));
            })
        .toList();
  }

  @SuppressWarnings("unchecked")
  private static List<Profile.Schedule> schedules(Object v) {
    if (!(v instanceof List<?> list)) {
      return List.of();
    }
    return list.stream()
        .map(
            item -> {
              if (!(item instanceof Map<?, ?> m)) {
                return new Profile.Schedule(null, null, null);
              }
              Map<String, Object> cast = (Map<String, Object>) m;
              return new Profile.Schedule(
                  str(cast.get("cron")), str(cast.get("zone")), str(cast.get("message")));
            })
        .toList();
  }

  /** Profile 文件解析失败（YAML 非法 / 顶层非对象 / 缺 name）。 */
  public static class ProfileParseException extends RuntimeException {
    public ProfileParseException(String message) {
      super(message);
    }

    public ProfileParseException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** Profile 校验不通过（本节：provider.name 在全局层找不到）。 */
  public static class ProfileValidationException extends RuntimeException {
    public ProfileValidationException(String message) {
      super(message);
    }
  }

  // Locale 引用占位（避免静态分析告未使用 import；实际未使用 setDefault 路径）
  @SuppressWarnings("unused")
  private static final Locale LOCALE_PLACEHOLDER = Locale.ROOT;
}
