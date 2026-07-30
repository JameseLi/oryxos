package com.oryxos.tool.builtin;

import java.util.List;

import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.sandbox.WhitelistSandbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** HttpGetTool 单测：白名单拦截与入参容错（不发真实网络请求）。 */
class HttpGetToolTest {

    private final HttpGetTool tool = new HttpGetTool(new WhitelistSandbox(List.of("example.com")), 5);

    @Test
    @DisplayName("非白名单域名被拦截")
    void nonWhitelistedDomainBlocked() {
        ToolResult result = tool.execute("{\"url\":\"https://evil.com/api\"}");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("白名单");
    }

    @Test
    @DisplayName("白名单子域名放行校验逻辑")
    void whitelistedSubdomainPassesCheck() {
        // 不真正发请求：用非法 scheme 让请求在校验后失败于网络层，从而证明沙箱已放行
        ToolResult result = tool.execute("{\"url\":\"ftp://api.example.com/x\"}");

        assertThat(result.errorMessage()).doesNotContain("白名单");
    }

    @Test
    @DisplayName("入参非法 JSON 返回失败不抛出")
    void invalidJsonInputReturnsFailureWithoutThrowing() {
        ToolResult result = tool.execute("not-json");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("JSON");
    }

    @Test
    @DisplayName("入参缺 url 字段返回失败")
    void missingUrlFieldReturnsFailure() {
        ToolResult result = tool.execute("{\"u\":\"https://example.com\"}");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("url");
    }
}
