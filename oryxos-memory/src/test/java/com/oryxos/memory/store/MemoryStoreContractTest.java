package com.oryxos.memory.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.memory.MemoryScope;
import com.oryxos.memory.MemoryTestApplication;
import com.oryxos.storage.repository.MemoryEntryRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

/**
 * 三档 {@link LongTermMemoryStore} 契约测试（第 22 节最值钱的 harness）：同一套断言，Markdown / SQLite / Mem0 都得过。
 *
 * <p>任何一档破坏契约（缓存、截断连累核心区、scope 路由错、recall 搜核心区），对应参数立刻红，一眼能看出是哪一档破的规矩。 Mem0 参数用本地假 Mem0 服务（{@link
 * FakeMem0Server}）承载，不碰真 server。
 */
@SpringBootTest(classes = MemoryTestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemoryStoreContractTest {

  @Autowired private MemoryEntryRepository repository;

  @TempDir static Path tempDir;

  private FakeMem0Server mem0Server;
  private Mem0MemoryStore mem0Store;

  @BeforeAll
  void startMem0() throws IOException {
    mem0Server = new FakeMem0Server();
    mem0Server.start();
    mem0Store = new Mem0MemoryStore(RestClient.create(), mem0Server.url(), "contract-user");
  }

  @AfterAll
  void stopMem0() throws IOException {
    mem0Server.close();
  }

  @AfterEach
  void resetState() {
    repository.deleteAll();
    mem0Server.reset();
  }

  Stream<Arguments> allStores() {
    return Stream.of(
        Arguments.of(
            "markdown",
            new MarkdownMemoryStore(tempDir.resolve("MEMORY-" + UUID.randomUUID() + ".md"))),
        Arguments.of("sqlite", new SqliteMemoryStore(repository)),
        Arguments.of("mem0", mem0Store));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("allStores")
  @DisplayName("写入后立刻可读 不允许有缓存")
  void appendIsVisibleOnNextRead_noCache(String backend, LongTermMemoryStore store) {
    store.append("刚记的事", MemoryScope.ARCHIVAL);

    assertThat(store.load()).contains("刚记的事");
    assertThat(store.recallByKeyword("刚记的事")).isNotEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("allStores")
  @DisplayName("截断只裁归档区 核心记忆一字不能少")
  void truncationOnlyKeepsArchiveTail_coreMemoryIntact(String backend, LongTermMemoryStore store) {
    store.append("用户叫小王，偏好用 Java", MemoryScope.CORE);
    for (int i = 0; i < 200; i++) {
      store.append("归档流水 " + i, MemoryScope.ARCHIVAL);
    }

    String loaded = store.load();

    assertThat(loaded).contains("用户叫小王，偏好用 Java");
    assertThat(loaded).doesNotContain("归档流水 0");
    assertThat(loaded).contains("归档流水 199");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("allStores")
  @DisplayName("写核心还是写归档 由 scope 显式指定")
  void scopeRoutesToCorrectSection(String backend, LongTermMemoryStore store) {
    store.append("核心-用户叫小王", MemoryScope.CORE);
    store.append("归档-项目用 Spring", MemoryScope.ARCHIVAL);

    String loaded = store.load();

    assertThat(loaded).contains("核心-用户叫小王");
    assertThat(loaded).contains("归档-项目用 Spring");
    assertThat(store.recallByKeyword("核心-用户叫小王")).isEmpty();
    assertThat(store.recallByKeyword("项目用")).anyMatch(line -> line.contains("归档-项目用 Spring"));
  }
}
