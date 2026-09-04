package com.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.tool.registry.AnnotatedToolAdapter;
import com.oryxos.tool.sandbox.PermissiveSandbox;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxViolationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link FileTools} 验收 harness（课件「验收 harness」第三批，模板即课件正文 http_get 两用例）： 六个方法各自「正常能跑通 +
 * 越界会被拦」，外加扩展三方法的专属守点（唯一匹配/上限截断/二进制跳过）。
 *
 * <p>时间裁定：正常路径用 {@link PermissiveSandbox}（默认放行）；「越界会被拦」以调用位语义承载—— 拒绝型 Sandbox 在首行 enforce 抛 {@link
 * SandboxViolationException}，真实 IO 不发生；真实白名单匹配 逻辑与 enforce InOrder 顺序断言归 24 节 {@code
 * WhitelistSandbox} 交付时补测。
 */
class FileToolsTest {

  @TempDir Path workspace;

  @Test
  @DisplayName("read_file/write_file 正常能跑通：写入后读回一致")
  void readWriteRoundTrip() throws IOException {
    FileTools tools = new FileTools(new PermissiveSandbox());
    Path file = workspace.resolve("note.txt");

    String written = tools.writeFile(file.toString(), "今天天气晴朗");
    assertThat(written).contains("已写入");
    assertThat(tools.readFile(file.toString())).isEqualTo("今天天气晴朗");
  }

  @Test
  @DisplayName("list_dir 正常能跑通：条目清单、目录带 / 后缀")
  void listDirListsEntriesWithDirSuffix() throws IOException {
    Files.createDirectories(workspace.resolve("sub"));
    Files.writeString(workspace.resolve("a.txt"), "x");
    FileTools tools = new FileTools(new PermissiveSandbox());

    String listing = tools.listDir(workspace.toString());

    assertThat(listing).contains("sub/", "a.txt");
  }

  @Test
  @DisplayName("命中白名单外的路径应被拦下：真实 IO 根本不发生")
  void fileOpsOutsideSandboxAreRejected() {
    FileTools tools = new FileTools(denying());

    assertThatThrownBy(() -> tools.readFile("C:/evil/secret.txt"))
        .isInstanceOf(SandboxViolationException.class);
    assertThatThrownBy(() -> tools.writeFile("C:/evil/x.txt", "boom"))
        .isInstanceOf(SandboxViolationException.class);
    assertThatThrownBy(() -> tools.listDir("C:/evil"))
        .isInstanceOf(SandboxViolationException.class);
    assertThatThrownBy(() -> tools.editFile("C:/evil/x.txt", "a", "b"))
        .isInstanceOf(SandboxViolationException.class);
    assertThatThrownBy(() -> tools.grep("x", "C:/evil"))
        .isInstanceOf(SandboxViolationException.class);
    assertThatThrownBy(() -> tools.glob("**/*.txt", "C:/evil"))
        .isInstanceOf(SandboxViolationException.class);
    assertThat(Files.exists(Path.of("C:/evil/x.txt"))).isFalse();
  }

  @Test
  @DisplayName("edit_file 唯一匹配替换：仅该处被替换")
  void editFileReplacesUniqueOccurrence() throws IOException {
    Path file = workspace.resolve("cfg.yaml");
    Files.writeString(file, "timeout: 30\nname: demo\ntimeout单位: 秒\n");
    FileTools tools = new FileTools(new PermissiveSandbox());

    String result = tools.editFile(file.toString(), "name: demo", "name: demo-v2");

    assertThat(result).contains("已编辑");
    assertThat(Files.readString(file)).isEqualTo("timeout: 30\nname: demo-v2\ntimeout单位: 秒\n");
  }

  @Test
  @DisplayName("edit_file 旧文本未找到：报错且文件一个字不动")
  void editFileMissingTextFailsAndLeavesFileUntouched() throws IOException {
    Path file = workspace.resolve("cfg.yaml");
    String original = "timeout: 30\nname: demo\n";
    Files.writeString(file, original);
    FileTools tools = new FileTools(new PermissiveSandbox());

    assertThatThrownBy(() -> tools.editFile(file.toString(), "不存在的文本", "x"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("未找到");

    assertThat(Files.readString(file)).isEqualTo(original);
  }

  @Test
  @DisplayName("edit_file 旧文本出现多次：报错且文件一个字不动（逼调用方给足上下文）")
  void editFileMultipleMatchesFailAndLeaveFileUntouched() throws IOException {
    Path file = workspace.resolve("cfg.yaml");
    String original = "value: 0.7\nother: 0.7\n";
    Files.writeString(file, original);
    FileTools tools = new FileTools(new PermissiveSandbox());

    assertThatThrownBy(() -> tools.editFile(file.toString(), "0.7", "0.8"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("2 次");

    assertThat(Files.readString(file)).isEqualTo(original);
  }

  @Test
  @DisplayName("grep 正常能跑通：返回 文件:行号:内容 形式")
  void grepReturnsFileLineContent() throws IOException {
    Files.writeString(workspace.resolve("app.yaml"), "port: 8080\ndb: memory\n");
    FileTools tools = new FileTools(new PermissiveSandbox());

    String result = tools.grep("port", workspace.toString());

    assertThat(result).contains("app.yaml:1:port: 8080");
  }

  @Test
  @DisplayName("grep 读到二进制/非 UTF-8 文件：跳过那个文件，不中断整次搜索")
  void grepSkipsBinaryFiles() throws IOException {
    Files.write(workspace.resolve("blob.bin"), new byte[] {0x00, 0x01, (byte) 0xFF, 'x'});
    Files.writeString(workspace.resolve("text.txt"), "needle here\n");
    FileTools tools = new FileTools(new PermissiveSandbox());

    String result = tools.grep("needle", workspace.toString());

    assertThat(result).contains("text.txt:1:needle here");
    assertThat(result).doesNotContain("blob.bin");
  }

  @Test
  @DisplayName("grep 命中超上限：截断并注明")
  void grepTruncatesAtLimitWithNote() throws IOException {
    StringBuilder big = new StringBuilder();
    for (int i = 0; i < FileTools.RESULT_LIMIT + 50; i++) {
      big.append("hit ").append(i).append('\n');
    }
    Files.writeString(workspace.resolve("big.log"), big.toString());
    FileTools tools = new FileTools(new PermissiveSandbox());

    String result = tools.grep("hit", workspace.toString());
    String[] lines = result.split("\n");

    assertThat(lines).hasSize(FileTools.RESULT_LIMIT + 1);
    assertThat(lines[FileTools.RESULT_LIMIT]).contains("已截断");
  }

  @Test
  @DisplayName("glob 正常能跑通：按通配返回相对路径列表")
  void globMatchesRelativePaths() throws IOException {
    Files.createDirectories(workspace.resolve("a"));
    Files.writeString(workspace.resolve("a/b.yaml"), "x");
    Files.writeString(workspace.resolve("c.yaml"), "x");
    Files.writeString(workspace.resolve("d.txt"), "x");
    FileTools tools = new FileTools(new PermissiveSandbox());

    String result = tools.glob("**/*.yaml", workspace.toString());

    assertThat(result).contains("a/b.yaml", "c.yaml").doesNotContain("d.txt");
  }

  @Test
  @DisplayName("glob 命中超上限：截断并注明")
  void globTruncatesAtLimitWithNote() throws IOException {
    for (int i = 0; i < FileTools.RESULT_LIMIT + 10; i++) {
      Files.writeString(workspace.resolve("f" + i + ".yaml"), "x");
    }
    FileTools tools = new FileTools(new PermissiveSandbox());

    String result = tools.glob("*.yaml", workspace.toString());
    String[] lines = result.split("\n");

    assertThat(lines).hasSize(FileTools.RESULT_LIMIT + 1);
    assertThat(lines[FileTools.RESULT_LIMIT]).contains("已截断");
  }

  @Test
  @DisplayName("经统一抽象视图调用（课件 harness 形态）：http_get 模板对文件工具同样成立")
  void toolViewByAnnotatedPipeline() {
    FileTools tools = new FileTools(new PermissiveSandbox());
    OryxTool readView = view(tools, "read_file");

    assertThat(readView.name()).isEqualTo("read_file");
    assertThat(readView.description()).isNotBlank();
    assertThat(readView.getInputSchema()).contains("path");
  }

  private static Sandbox denying() {
    return action -> {
      throw new SandboxViolationException("动作被白名单拒绝: " + action.target());
    };
  }

  static OryxTool view(Object toolBean, String toolName) {
    for (AnnotatedToolAdapter adapter : AnnotatedToolAdapter.forToolBean(toolBean)) {
      if (adapter.name().equals(toolName)) {
        return adapter;
      }
    }
    throw new IllegalStateException("工具 Bean 中找不到 @Tool 方法: " + toolName);
  }
}
