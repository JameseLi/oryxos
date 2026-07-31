package com.oryxos.tool.sandbox;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 应用层白名单沙箱（核心阶段实现档）：按域名白名单放行出站 HTTP 请求。 支持后缀匹配——白名单含 example.com 时 api.example.com 亦放行。 */
@Component
public class WhitelistSandbox implements Sandbox {

  private static final Logger log = LoggerFactory.getLogger(WhitelistSandbox.class);

  private final Set<String> allowedDomains;

  public WhitelistSandbox(
      @Value("${oryxos.sandbox.allowed-domains:wttr.in}") List<String> allowedDomains) {
    this.allowedDomains = Set.copyOf(allowedDomains);
  }

  @Override
  public void checkHttpRequest(String url) {
    String host;
    try {
      host = URI.create(url).getHost();
    } catch (IllegalArgumentException e) {
      throw new SandboxViolationException("URL 无法解析: " + url);
    }
    if (host == null || host.isBlank()) {
      throw new SandboxViolationException("URL 缺少 host: " + url);
    }
    String normalized = host.toLowerCase(Locale.ROOT);
    boolean allowed =
        allowedDomains.stream()
            .map(domain -> domain.toLowerCase(Locale.ROOT))
            .anyMatch(domain -> normalized.equals(domain) || normalized.endsWith("." + domain));
    if (!allowed) {
      log.warn("沙箱拦截出站请求: host={} allowed={}", normalized, allowedDomains);
      throw new SandboxViolationException(
          "域名不在白名单: " + normalized + "（白名单: " + allowedDomains + "）");
    }
  }
}
