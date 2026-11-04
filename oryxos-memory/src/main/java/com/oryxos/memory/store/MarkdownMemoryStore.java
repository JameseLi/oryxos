package com.oryxos.memory.store;

import com.oryxos.core.memory.MemoryScope;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 长期记忆后端（第 22 节默认档，技术方案 §5.2）。
 *
 * <p>操作 {@code .oryxos/memory/MEMORY.md} 一个文件，按 {@code ## 核心记忆} / {@code ## 归档记忆} 两个区块分区，每条记忆带日期。
 * 零依赖、人可读、git 可跟踪，记忆量不大时的首选。
 *
 * <p>契约落点：{@link #load()} 每次 {@code Files.readString} 不缓存（契约一）；{@link #truncateIfNeeded(String)}
 * 只接收归档段文本、物理上碰不到核心区（契约二）；{@link #append} 按传入 scope 选区块（契约三）； {@link #recallByKeyword} 只扫归档段（契约四）。
 */
public class MarkdownMemoryStore implements LongTermMemoryStore {

  static final String CORE_HEADER = "## 核心记忆";
  static final String ARCHIVE_HEADER = "## 归档记忆";
  static final int MAX_ARCHIVE_CHARS = 4000;

  private static final String FILE_TITLE = "# MEMORY.md";
  private static final String PLACEHOLDER = "（暂无）";
  private static final String SECTION_PREFIX = "## ";

  private final Path memoryFile;

  public MarkdownMemoryStore(Path memoryFile) {
    this.memoryFile = memoryFile;
  }

  @Override
  public synchronized void append(String content, MemoryScope scope) {
    MemoryScope effective = scope == null ? MemoryScope.ARCHIVAL : scope;
    String header = effective == MemoryScope.CORE ? CORE_HEADER : ARCHIVE_HEADER;
    List<String> lines = readLines();
    int headerIndex = indexOfHeader(lines, header);
    if (headerIndex < 0) {
      if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
        lines.add("");
      }
      lines.add(header);
      headerIndex = lines.size() - 1;
    }
    int sectionEnd = headerIndex + 1;
    while (sectionEnd < lines.size() && !lines.get(sectionEnd).startsWith(SECTION_PREFIX)) {
      sectionEnd++;
    }
    List<String> section = new ArrayList<>(lines.subList(headerIndex + 1, sectionEnd));
    section.removeIf(line -> PLACEHOLDER.equals(line.trim()));
    while (!section.isEmpty() && section.get(section.size() - 1).isBlank()) {
      section.remove(section.size() - 1);
    }
    section.add("");
    section.add("- [" + LocalDate.now() + "] " + content);
    section.add("");
    lines.subList(headerIndex + 1, sectionEnd).clear();
    lines.addAll(headerIndex + 1, section);
    writeLines(lines);
  }

  @Override
  public String load() {
    String raw = readRaw();
    String core = extractSection(raw, CORE_HEADER).trim();
    String archive = truncateIfNeeded(extractSection(raw, ARCHIVE_HEADER).trim());
    if (core.isBlank() && archive.isBlank()) {
      return "";
    }
    return CORE_HEADER + "\n" + core + "\n" + ARCHIVE_HEADER + "\n" + archive;
  }

  @Override
  public List<String> recallByKeyword(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return List.of();
    }
    return extractSection(readRaw(), ARCHIVE_HEADER)
        .lines()
        .map(String::trim)
        .filter(line -> !line.isBlank() && !PLACEHOLDER.equals(line))
        .filter(line -> line.contains(keyword))
        .toList();
  }

  private static String truncateIfNeeded(String archive) {
    if (archive.length() <= MAX_ARCHIVE_CHARS) {
      return archive;
    }
    // 只裁归档段：从头部截掉，保留最近的记忆；核心区不在本方法的输入里。
    return archive.substring(archive.length() - MAX_ARCHIVE_CHARS);
  }

  private String readRaw() {
    if (Files.notExists(memoryFile)) {
      return "";
    }
    try {
      return Files.readString(memoryFile, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("读取长期记忆文件失败: " + memoryFile, e);
    }
  }

  private List<String> readLines() {
    List<String> lines = new ArrayList<>(readRaw().lines().toList());
    if (lines.isEmpty()) {
      lines.add(FILE_TITLE);
      lines.add("");
      lines.add(CORE_HEADER);
      lines.add("");
      lines.add(ARCHIVE_HEADER);
      lines.add("");
    }
    return lines;
  }

  private static int indexOfHeader(List<String> lines, String header) {
    for (int i = 0; i < lines.size(); i++) {
      if (header.equals(lines.get(i).trim())) {
        return i;
      }
    }
    return -1;
  }

  private static String extractSection(String raw, String header) {
    List<String> lines = raw.lines().toList();
    int start = -1;
    for (int i = 0; i < lines.size(); i++) {
      if (header.equals(lines.get(i).trim())) {
        start = i + 1;
        break;
      }
    }
    if (start < 0) {
      return "";
    }
    StringBuilder section = new StringBuilder();
    for (int i = start; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.startsWith(SECTION_PREFIX)) {
        break;
      }
      section.append(line).append('\n');
    }
    return section.toString();
  }

  private void writeLines(List<String> lines) {
    try {
      Path parent = memoryFile.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.write(memoryFile, lines, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("写入长期记忆文件失败: " + memoryFile, e);
    }
  }
}
