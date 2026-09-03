package com.oryxos.tool.builtin;

import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 文件族内置工具（技术方案 §6.2 + 课件「六」6.2）：{@code read_file}/{@code write_file}/{@code list_dir} 规划三件，实现时补齐
 * {@code edit_file}/{@code grep}/{@code glob} 共六个方法。
 *
 * <p>硬规矩（课件「三」）：每个方法<b>第一行</b>过 {@code Sandbox.enforce}（文件读/写分开路由，§6.7）， 校验不过抛 {@code
 * SandboxViolationException}、真实 IO 根本不发生。本体白名单 24 节交付，当前为 {@code PermissiveSandbox} 临时装配。
 *
 * <p>编辑语义（课件 6.2）：{@code edit_file} 要求旧文本<b>唯一匹配</b>--找不到报错、出现多次也报错， 逼调用方给足上下文定位唯一编辑点，报错时文件一个字不动（与
 * Claude Code / Cursor 的 edit 工具同一套约束）。 {@code grep}/{@code glob} 有返回条数上限（200
 * 条）：超大目录一次全吐会把模型上下文撑爆，超限截断并注明； {@code grep} 读到二进制/非 UTF-8 文件跳过而不是中断整次搜索。
 */
@Component
public class FileTools {

  /** grep/glob 返回条数上限（课件 6.2：超限截断并注明）。 */
  static final int RESULT_LIMIT = 200;

  private final Sandbox sandbox;

  public FileTools(Sandbox sandbox) {
    this.sandbox = sandbox;
  }

  /** 读文件：返回文件全文（UTF-8）。 */
  @Tool(name = "read_file", description = "读取一个文本文件的内容（UTF-8）")
  public String readFile(@ToolParam(description = "要读取的文件路径（受文件白名单约束）") String path) {
    sandbox.enforce(new SandboxAction(ActionType.FILE_READ, path));
    try {
      return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("read_file 失败: " + e.getMessage(), e);
    }
  }

  /** 写文件：整文件覆盖写（父目录不存在则创建）。 */
  @Tool(name = "write_file", description = "把内容写入一个文本文件（整文件覆盖，父目录自动创建）")
  public String writeFile(
      @ToolParam(description = "目标文件路径（受文件白名单约束）") String path,
      @ToolParam(description = "要写入的完整内容") String content) {
    sandbox.enforce(new SandboxAction(ActionType.FILE_WRITE, path));
    try {
      Path target = Path.of(path);
      Path parent = target.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(target, content, StandardCharsets.UTF_8);
      return "已写入 " + target + "（" + content.length() + " 字符）";
    } catch (IOException e) {
      throw new IllegalStateException("write_file 失败: " + e.getMessage(), e);
    }
  }

  /** 列目录：返回条目清单（目录带 / 后缀），不存在/非目录明确报错。 */
  @Tool(name = "list_dir", description = "列出一个目录的直接子条目（目录带 / 后缀）")
  public String listDir(@ToolParam(description = "要列出的目录路径（受文件白名单约束）") String path) {
    sandbox.enforce(new SandboxAction(ActionType.FILE_READ, path));
    Path dir = Path.of(path);
    if (!Files.isDirectory(dir)) {
      throw new IllegalStateException("list_dir 失败: '" + path + "' 不是目录或不存在");
    }
    try (Stream<Path> entries = Files.list(dir)) {
      List<String> names = new ArrayList<>();
      entries.sorted().forEach(entry -> names.add(displayName(entry)));
      return String.join("\n", names);
    } catch (IOException e) {
      throw new IllegalStateException("list_dir 失败: " + e.getMessage(), e);
    }
  }

  /** 编辑文件：旧文本唯一匹配替换（零/多处命中报错且文件不动，课件 6.2）。 */
  @Tool(name = "edit_file", description = "把文件里唯一出现的一段旧文本替换为新文本（要求唯一匹配，多处命中报错且文件不动）")
  public String editFile(
      @ToolParam(description = "目标文件路径（受文件白名单约束）") String path,
      @ToolParam(description = "要被替换的旧文本（必须在文件中唯一出现）") String oldText,
      @ToolParam(description = "替换后的新文本") String newText) {
    sandbox.enforce(new SandboxAction(ActionType.FILE_WRITE, path));
    if (oldText == null || oldText.isEmpty()) {
      throw new IllegalArgumentException("edit_file 的旧文本不能为空");
    }
    try {
      Path target = Path.of(path);
      String original = Files.readString(target, StandardCharsets.UTF_8);
      int occurrences = countOccurrences(original, oldText);
      if (occurrences == 0) {
        throw new IllegalStateException("edit_file 失败: 旧文本在 '" + path + "' 中未找到（文件未改动）");
      }
      if (occurrences > 1) {
        throw new IllegalStateException(
            "edit_file 失败: 旧文本在 '" + path + "' 中出现 " + occurrences + " 次，请提供更长上下文定位唯一编辑点（文件未改动）");
      }
      Files.writeString(target, original.replace(oldText, newText), StandardCharsets.UTF_8);
      return "已编辑 " + target + "（替换 1 处）";
    } catch (IOException e) {
      throw new IllegalStateException("edit_file 失败: " + e.getMessage(), e);
    }
  }

  /** 内容搜索：递归按正则搜文本，返回「文件:行号:内容」行式，上限截断注明、二进制跳过。 */
  @Tool(name = "grep", description = "在目录下按正则表达式搜索文本内容，返回 文件:行号:内容 列表（跳过二进制文件）")
  public String grep(
      @ToolParam(description = "正则表达式") String pattern,
      @ToolParam(description = "搜索根目录（递归，受文件白名单约束）") String rootDir) {
    sandbox.enforce(new SandboxAction(ActionType.FILE_READ, rootDir));
    Pattern regex = compileRegex(pattern);
    Path root = Path.of(rootDir);
    List<String> hits = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(root)) {
      List<Path> files = stream.filter(Files::isRegularFile).sorted().toList();
      for (Path file : files) {
        collectLineHits(root, file, regex, hits);
        if (hits.size() >= RESULT_LIMIT) {
          break;
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("grep 失败: " + e.getMessage(), e);
    }
    return renderWithLimitNote(hits);
  }

  /** 文件名搜索：按 glob 通配找相对路径，上限截断注明。 */
  @Tool(name = "glob", description = "按 glob 通配模式（如 **/*.yaml）在目录下查找文件，返回相对路径列表")
  public String glob(
      @ToolParam(description = "glob 通配模式，相对搜索根目录，如 **/*.yaml") String pattern,
      @ToolParam(description = "搜索根目录（递归，受文件白名单约束）") String rootDir) {
    sandbox.enforce(new SandboxAction(ActionType.FILE_READ, rootDir));
    if (pattern == null || pattern.isBlank()) {
      throw new IllegalArgumentException("glob 的通配模式不能为空");
    }
    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
    // 课件对齐 Claude Code glob 语义：**/*.yaml 也命中根级文件（JDK glob 的 **/ 要求至少一级目录）
    PathMatcher rootLevelMatcher =
        pattern.startsWith("**/")
            ? FileSystems.getDefault().getPathMatcher("glob:" + pattern.substring(3))
            : null;
    Path root = Path.of(rootDir);
    List<String> matches = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(root)) {
      List<Path> files = stream.filter(Files::isRegularFile).sorted().toList();
      for (Path file : files) {
        Path relative = root.relativize(file);
        boolean matched = matcher.matches(relative);
        if (!matched && rootLevelMatcher != null) {
          matched = rootLevelMatcher.matches(relative);
        }
        if (matched) {
          // 归一为 / 分隔：Windows 反斜杠路径对模型不可读且不可再拼进 glob
          matches.add(relative.toString().replace('\\', '/'));
          if (matches.size() >= RESULT_LIMIT) {
            break;
          }
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("glob 失败: " + e.getMessage(), e);
    }
    return renderWithLimitNote(matches);
  }

  /** 列目录条目展示名：目录带 / 后缀；根路径无文件名时退回全路径。 */
  private static String displayName(Path entry) {
    Path fileName = entry.getFileName();
    String name = fileName == null ? entry.toString() : fileName.toString();
    return Files.isDirectory(entry) ? name + "/" : name;
  }

  private static int countOccurrences(String content, String needle) {
    int count = 0;
    int index = 0;
    while ((index = content.indexOf(needle, index)) >= 0) {
      count++;
      index += needle.length();
    }
    return count;
  }

  private static Pattern compileRegex(String pattern) {
    if (pattern == null || pattern.isBlank()) {
      throw new IllegalArgumentException("grep 的正则表达式不能为空");
    }
    try {
      return Pattern.compile(pattern);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException("grep 的正则表达式无效: " + e.getMessage(), e);
    }
  }

  /** 单文件命中收集：非 UTF-8/二进制文件（严格解码抛 MalformedInputException）跳过不中断（课件 6.2）。 */
  private void collectLineHits(Path root, Path file, Pattern regex, List<String> hits) {
    List<String> lines;
    try {
      lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      // 跳过二进制/非 UTF-8 文件与不可读文件，不中断整次搜索（课件 6.2）
      return;
    }
    String relative = root.relativize(file).toString();
    for (int i = 0; i < lines.size() && hits.size() < RESULT_LIMIT; i++) {
      if (regex.matcher(lines.get(i)).find()) {
        hits.add(relative + ":" + (i + 1) + ":" + lines.get(i));
      }
    }
  }

  private static String renderWithLimitNote(List<String> rows) {
    if (rows.size() >= RESULT_LIMIT) {
      rows.add("（结果已达上限 " + RESULT_LIMIT + " 条，已截断）");
    }
    return String.join("\n", rows);
  }
}
