package com.oryxos.memory;

import com.oryxos.core.memory.MemoryService;
import com.oryxos.memory.store.LongTermMemoryStore;
import com.oryxos.memory.store.MarkdownMemoryStore;
import com.oryxos.memory.store.Mem0MemoryStore;
import com.oryxos.memory.store.SqliteMemoryStore;
import com.oryxos.memory.tool.MemoryTools;
import com.oryxos.storage.repository.MemoryEntryRepository;
import java.nio.file.Path;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Memory 装配（第 22 节）：一个 {@code memory.backend} 配置项决定装配哪档 {@link LongTermMemoryStore}， 注入给同一个 {@link
 * MemoryService}——换后端只改一行配置，{@code PromptBuilder}/{@code MemoryTools}/{@code ReActLoop} 一个字不用动。
 *
 * <p>三档：{@code markdown}（默认，零依赖）、{@code sqlite}（复用已有 SQLite）、{@code mem0}（自托管外部服务，地址/凭证走环境变量）。
 * sqlite 分支的仓储用 {@link ObjectProvider} 懒取，markdown 档启动时不触发数据库。
 */
@Configuration
public class MemoryConfiguration {

  @Bean
  public LongTermMemoryStore longTermMemoryStore(
      @Value("${memory.backend:markdown}") String backend,
      @Value("${oryxos.workspace-dir:.oryxos}") String workspaceDir,
      @Value("${MEM0_BASE_URL:http://localhost:8888}") String mem0BaseUrl,
      @Value("${MEM0_USER_ID:oryxos}") String mem0UserId,
      @Value("${MEM0_API_KEY:}") String mem0ApiKey,
      ObjectProvider<MemoryEntryRepository> memoryEntryRepository) {
    String effective = backend == null ? "markdown" : backend.trim();
    switch (effective) {
      case "markdown":
        return new MarkdownMemoryStore(Path.of(workspaceDir, "memory", "MEMORY.md"));
      case "sqlite":
        return new SqliteMemoryStore(memoryEntryRepository.getObject());
      case "mem0":
        return new Mem0MemoryStore(RestClient.create(), mem0BaseUrl, mem0UserId, mem0ApiKey);
      default:
        throw new IllegalArgumentException("未知 memory.backend: " + backend);
    }
  }

  @Bean
  public MemoryService memoryService(LongTermMemoryStore longTermMemoryStore) {
    return new MemoryServiceImpl(longTermMemoryStore);
  }

  @Bean
  public MemoryTools memoryTools(MemoryService memoryService) {
    return new MemoryTools(memoryService);
  }
}
