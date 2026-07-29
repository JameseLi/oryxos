package com.oryxos.tool.sandbox;

/**
 * 沙箱接口（宪法工程约束：不使用 Java SecurityManager；接口签名中立，
 * 不出现"白名单/容器/VM"等实现词）。
 */
public interface Sandbox {

    /**
     * 校验一次出站 HTTP 请求是否放行。
     *
     * @throws SandboxViolationException 被策略拒绝时抛出
     */
    void checkHttpRequest(String url);
}
