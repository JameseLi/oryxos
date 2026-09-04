package com.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.oryxos.tool.sandbox.PermissiveSandbox;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxViolationException;
import com.oryxos.tool.search.SearchProvider;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link WebSearchTools} 验收 harness（课件 6.4）：首行过 HTTP 域名白名单调用位（不能因为叫「搜索」 就绕过治理）+ 结果渲染成模型好读文本。搜索源
 * mock 不碰网（真搜属人工项）。
 *
 * <p>时间裁定：enforce InOrder 顺序断言归 24 节（同 19 节模式）。
 */
class WebSearchToolsTest {

  private static final String ENDPOINT = "https://api.duckduckgo.com";

  @Test
  @DisplayName("web_search 首行过白名单调用位：拒绝型 Sandbox 抛校验异常且搜索源根本不被调用")
  void webSearchEnforcedBeforeNetwork() {
    SearchProvider provider = mock(SearchProvider.class);
    WebSearchTools tools = new WebSearchTools(denying(), provider, ENDPOINT);

    assertThatThrownBy(() -> tools.webSearch("oryxos agent os"))
        .isInstanceOf(SandboxViolationException.class);
    verifyNoInteractions(provider);
  }

  @Test
  @DisplayName("web_search 正常渲染：标题/链接/摘要行式文本（搜索源 mock）")
  void webSearchRendersResults() {
    SearchProvider provider = mock(SearchProvider.class);
    org.mockito.Mockito.when(provider.search(anyString()))
        .thenReturn(
            List.of(
                new SearchProvider.SearchResult("OryxOS 官网", "https://oryxos.dev", "企业级 Agent OS"),
                new SearchProvider.SearchResult(
                    "Agent OS 对比", "https://blog.example/os", "与 Dify 互补")));
    WebSearchTools tools = new WebSearchTools(new PermissiveSandbox(), provider, ENDPOINT);

    String result = tools.webSearch("agent os");

    assertThat(result)
        .contains("OryxOS 官网", "https://oryxos.dev", "企业级 Agent OS")
        .contains("Agent OS 对比", "https://blog.example/os", "与 Dify 互补");
  }

  @Test
  @DisplayName("空结果语义明确：不是空字符串也不是静默成功")
  void webSearchRendersEmptyResultNote() {
    SearchProvider provider = mock(SearchProvider.class);
    org.mockito.Mockito.when(provider.search(anyString())).thenReturn(List.of());
    WebSearchTools tools = new WebSearchTools(new PermissiveSandbox(), provider, ENDPOINT);

    String result = tools.webSearch("no-such-thing");

    assertThat(result).contains("没有找到");
  }

  @Test
  @DisplayName("空查询词明确报错")
  void webSearchRejectsBlankQuery() {
    WebSearchTools tools =
        new WebSearchTools(new PermissiveSandbox(), mock(SearchProvider.class), ENDPOINT);

    assertThatThrownBy(() -> tools.webSearch(" ")).isInstanceOf(IllegalArgumentException.class);
  }

  private static Sandbox denying() {
    return action -> {
      throw new SandboxViolationException("动作被白名单拒绝: " + action.target());
    };
  }
}
