# Implementation Plan: 第 20 节 Tool 体系

**Branch**: `020-lesson20-tool` | **Date**: 2026-09-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/020-lesson20-tool/spec.md`；课件 `docs/class/第20节：Tool 体系 原理解析、实现与代码讲解.md`；技术方案对位 §6.1~6.6（Tool 体系）、§6.7（Sandbox 前向接口）、§8.2（Profile `tools` 字段消费）。

## Summary

交付 Tool 体系地基：**统一注册表真实现**（替换 18 节 `EmptyToolRegistry` 过渡 Bean）+ **`AnnotatedToolAdapter` 注解扫描注册**（`@Tool` 方法 → `OryxTool`，经 Spring AI `MethodToolCallbackProvider` 生成 schema，只用注解一半、绝不接自动执行）+ **MCP 方式二接入**（`McpClientService` 读 `.oryxos/mcp_servers.yaml` 连接所有 server、`tools/list` 包装注册、`McpToolAdapter` 经 MCP 协议原样转发；单 server 失联 WARN 跳过不拖垮启动）+ **内置工具 12 个落注册表**（FileTools 六方法、ShellTools、HttpTools 两方法、InteractionTools、WebSearchTools、19 节 `NotifyTools` 收编）+ **Sandbox 前向接口五件**（`PermissiveSandbox` 默认放行临时装配，本体 24 节）+ **`ToolResult` 增补 `retryable` 维度**（17 节 javadoc 点名的既定演进点）。执行链路消费端（`ReActLoop`/`PromptBuilder`/`ToolExecutor`）零改动。

## Technical Context

**固定技术栈句**：JDK 21 + Spring Boot 3.x + Spring AI Alibaba（动手前先跑 `mvn dependency:tree` 确认锁定 BOM 里目标依赖存在）、SQLite + Spring Data JPA。凭证走环境变量占位，不落明文。SQLite 用手工建表脚本，不依赖 `hibernate.ddl-auto=update`。

- **Language/Version**: Java 21（全同步阻塞；MCP 用 `McpSyncClient` 同步门面，OryxOS 代码零 Reactor/`CompletableFuture`/自建线程池——H4⑤）。
- **Primary Dependencies**（**H3 依赖核实已完成**，2026-09-02 实测，见 Pre-Implement Verification Gate）:
  - `io.modelcontextprotocol.sdk:mcp-core` **0.18.3**：已在锁定依赖链（`spring-ai-client-chat` 1.1.8 → `mcp-json-jackson2` 0.18.3 → `mcp-core` 0.18.3，`mvn dependency:tree` 实测）；spring-ai-bom 1.1.8 与 parent pom 均不管理其版本 → oryxos-tool pom **显式锁 0.18.3**（mockwebserver 4.12.0 先例）。API 已 javap 核实：`McpClient$SyncSpec`/`McpSyncClient`/`StdioClientTransport`/`HttpClientSseClientTransport`/`McpSchema$Tool`/`McpSchema$CallToolResult`/`McpSchema$JsonSchema`。
  - `org.yaml:snakeyaml`（Boot BOM 管版本，经 spring-boot-starter 传递已在链；显式化声明）：`mcp_servers.yaml` 解析。
  - `spring-ai-model` 1.1.8（tool pom 已有）：`@Tool`/`@ToolParam`、`MethodToolCallbackProvider.builder().toolObjects(...)`、`ToolCallback.call(String)`、`ToolDefinition.name()/description()/inputSchema()`——均已 javap 核实。
  - `spring-web`/`jackson-databind`/`mockwebserver` 4.12.0：tool pom 已有（19 节先例），本节复用。
- **Storage**: 零变更（无新表、无新实体、无 DDL；工具执行成败走 `ToolExecutor` 既有 `tool_invocations` 审计路径）。
- **Testing**: JUnit 5 + Mockito + AssertJ + MockWebServer（本地回环）；MCP 客户端全 mock 不碰网、不真起 stdio 子进程；`SearchProvider` mock 不碰网；stdin 重定向用 `ByteArrayInputStream`。
- **Target Platform**: 企业内网单实例；fat JAR 结构不变。
- **语法禁区句**：避开 P3C/ASM 解析不了的 Java 18+ 语法形态（如增强 switch 的 `default ->` 写法），静态检查是构建门禁。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design. 原则来源 `.specify/memory/constitution.md`。*

| # | 原则 | 判定 | 证据/说明 |
|---|------|------|-----------|
| I | 固定技术栈与九模块结构 | ✅ | 只在既有 oryxos-tool（registry/builtin/mcp/sandbox/interaction/search 六包）+ oryxos-core（`ToolResult` 演进）内新增，不增删模块；mcp-core/snakeyaml 均为锁定依赖链内工件（显式锁版本，非新第三方家族）。 |
| II | 核心阶段只做运行时内核 | ✅ | **不做** Tool Policy/按需加载/自身变 MCP server/容器沙箱/多工具并行调（spec 边界）；治理=白名单调用位 + Profile `tools` 字段。 |
| III | 自实现 ReAct 循环 | ✅ | 循环零触碰；本节供循环的"手"，`ReActLoop`/`ToolExecutor` 消费端零改动。 |
| IV | Spring AI 只用一半 | ✅ | 只用 `@Tool`/`@ToolParam` 注解与 `MethodToolCallbackProvider` 的 schema 生成（`ToolDefinition.inputSchema()`）；`ToolCallback` **不接入任何 ChatModel**，执行唯一收口 OryxOS `ToolExecutor` → `OryxTool.execute` → `ToolCallback.call`；自动 tool 执行路径零启用。 |
| V | Plugin Tool 三档接入·零代码优先 | ✅ | 方式二=`McpClientService`、方式三=`AnnotatedToolAdapter`；方式一不新增机制（承载件归既有节）；主推次序在课件「二」第二、装配面以 `registerAnnotated` 收口。 |
| VI | 审计数据 Day One 落库 | ✅ | 工具执行成败走 `ToolExecutor` 既有 `tool_invocations` 路径，零新增审计逻辑/表。 |
| VII | 每个 User Story 必须可演示 | ✅ | harness 全绿 + `mvn clean verify` 全绿；`oryxos tool list` 在真注册表替换后自然可见 12 个工具（冒烟演示点）。 |

## 明确不做（边界）

- **不做 Tool Policy**（allow/deny 规则）——扩展阶段；本期白名单调用位 + Profile `tools` 字段顶住。
- **不做按需加载**、**不做一次响应多工具并行调**——扩展阶段。
- **不把 OryxOS 自己变成 MCP server 对外暴露**——本期只做 MCP Client。
- **不做 Sandbox 白名单本体**（`WhitelistSandbox` + `file.allowed_paths`/`shell.allowed_commands`/`http.allowed_domains` 配置键 + enforce InOrder 顺序断言）——23/24 节；本节只交付中立前向接口五件 + 默认放行临时实现。
- **不交付 `MemoryTools`**（`save_memory`/`recall_memory`）——22 节 Memory 模块交付后注册。
- **不做方式一专属新机制**——AGENT.md 解析（17 节）、`mcp_servers.yaml`（18 节 init 已生成模板）、`skills/` 各归其位。
- **不改前序节公共接口**——唯二既定改动点：`ToolResult` 增补 `retryable`（17 节 javadoc 点名）、`EngineConfiguration` 过渡 Bean 替换（18 节 javadoc 点名）；其余（`OryxTool`/`ToolExecutor`/`PromptBuilder`/`Profile`/`NotifyTools` 本体/`ToolListCommand`）零改动。
- 另（16~19 节已定，本节延续不做）：认证/限流、SSE/WebSocket、多租户。

## Project Structure（模块落位 + 文件级清单）

模块落位（skill 落位表）：`OryxTool`/`ToolResult` → oryxos-core（已交付，本节仅 `ToolResult` 演进）；其余（Registry 实现/内置 Tool/MCP/Sandbox 前向）→ **oryxos-tool**。

| 组件 | 模块/包 | 说明 |
|---|---|---|
| `ToolResult` 演进 | core `com.oryxos.core.tool` | record 增补 `retryable` 分量：`(boolean success, String output, String errorMessage, boolean retryable)`；`ok(output)` 保留（retryable=false）、新增 `ok(output, retryable)`/`failure(msg, retryable)` 重载；`failure(msg)` 保留（false）；`toModelText` 零改动；javadoc 更新为 §6.1 四维度终局形态 |
| `DefaultToolRegistry` | tool `com.oryxos.tool.registry` | 实现 17 节 `ToolRegistry` 接口（find/all 语义零变）；`ConcurrentHashMap` 存储；注册入口 `register(OryxTool)`/`registerAll`/`registerAnnotated(Object... toolBeans)`（课件 6.5 原话命名的公开入口）；重名注册抛 `IllegalStateException`（防静默覆盖）；`all()` 返回快照 |
| `AnnotatedToolAdapter` | tool `com.oryxos.tool.registry` | 把 Spring AI `ToolCallback` 包装成 `OryxTool`：`name()/description()/getInputSchema()` 直接映射 `ToolDefinition` 三方法（同构，已核实）；`execute(String)` → `callback.call(input)` 正常返回包 `ToolResult.ok`、异常**原样上抛**（课件 harness `assertThrows` 语义；`ToolExecutor` 统一转失败+审计） |
| `ToolRegistryConfiguration` | tool `com.oryxos.tool.registry` | `@Configuration`：`@Bean ToolRegistry`（构造时显式注入五个内置工具 Bean + `NotifyTools`，逐个 `registerAnnotated`；课件 6.5"装配处多 registerAnnotated 两行"）；`@Bean Sandbox`（`PermissiveSandbox`，24 节换 `WhitelistSandbox`）；`@Bean UserInteraction`（`ConsoleUserInteraction`）；MCP 注册触发（调 `McpClientService.connectAll`） |
| `Sandbox` 前向五件 | tool `com.oryxos.tool.sandbox` | `Sandbox`（单方法 `void enforce(SandboxAction action)`，签名零"白名单/容器/VM"词汇）、`SandboxAction record(ActionType type, String target)`、`ActionType enum{FILE_READ, FILE_WRITE, SHELL_COMMAND, HTTP_REQUEST}`（四值，§6.7 逐字）、`SandboxViolationException extends RuntimeException`、`PermissiveSandbox`（默认放行空实现，javadoc 注明 24 节替换） |
| `FileTools` | tool `com.oryxos.tool.builtin` | `@Component`，构造注入 `Sandbox`；六个 `@Tool` 方法：`read_file`/`write_file`/`list_dir`/`edit_file`/`grep`/`glob`；**每方法第一行 `sandbox.enforce(...)`**；`edit_file` 唯一匹配语义（零/多处命中抛 `IllegalStateException`、文件零改动）；`grep`/`glob` 上限 200 条截断注明；`grep` 跳过二进制/非 UTF-8 |
| `ShellTools` | tool `com.oryxos.tool.builtin` | `@Component` + `Sandbox`；`shell` 方法：首行 enforce(SHELL_COMMAND) → `ProcessBuilder` 执行、**30s 超时**（课件只说"带超时"未定值，常量 + javadoc 注明，不立新配置键）、超时/非零退出语义明确 |
| `HttpTools` | tool `com.oryxos.tool.builtin` | `@Component` + `Sandbox` + `RestClient`（19 节 Bean 复用）；`http_get`（课件骨架逐字：description"发起一个 HTTP GET 请求，返回响应体"）/`http_post`（url+body）；首行 enforce(HTTP_REQUEST) |
| `InteractionTools` + `UserInteraction` 三件 | tool `com.oryxos.tool.builtin` + `com.oryxos.tool.interaction` | `UserInteraction` 接口（`String ask(String question)`，课件 6.3 逐字：拿不到回答就抛异常绝不静默卡住）；`ConsoleUserInteraction`（读终端一行）、`UnsupportedUserInteraction`（抛 `IllegalStateException("当前渠道不支持交互")`）；`InteractionTools.ask_user` 首行 enforce 不需要（无涉外 IO），委托 `UserInteraction` |
| `WebSearchTools` + `SearchProvider` 两件 | tool `com.oryxos.tool.builtin` + `com.oryxos.tool.search` | `SearchProvider` 接口（`record SearchResult(String title, String url, String snippet)` + `List<SearchResult> search(String query)`，课件 6.4 逐字）；`DuckDuckGoSearchProvider`（免 key Instant Answer API，`RestClient`）；`WebSearchTools.web_search` **首行 enforce(HTTP_REQUEST)**（不能因为叫"搜索"绕过治理），结果渲染成模型好读文本（title/url/snippet 行式） |
| `McpClientService` + `McpServerConfig` | tool `com.oryxos.tool.mcp` | `@Component`；`@PostConstruct connectAll()` 课件骨架逐字同构：读 `.oryxos/mcp_servers.yaml`（SnakeYAML → `List<McpServerConfig>`）→ 逐 server try-connect（`stdio`: `StdioClientTransport`；`sse`: `HttpClientSseClientTransport`）→ `listTools()` 逐个 `register(new McpToolAdapter(...))`；**单 server 失败 log.warn 跳过、其余照常、启动不炸**；`@PreDestroy` close 全部连接防 stdio 子进程泄漏；`McpServerConfig record(String name, String transport, String command, Map<String,String> env)`（课件骨架点名名字） |
| `McpToolAdapter` | tool `com.oryxos.tool.mcp` | implements `OryxTool`：`name()/description()` 直接映射 `McpSchema$Tool`；`getInputSchema()` 用 jackson 序列化 `McpSchema$JsonSchema`；`execute(String)`：jackson 解析入参原文为 Map → `McpSyncClient.callTool(CallToolRequest)` 原样转发 → `CallToolResult` 包 `ToolResult`（isError/失败 → `failure("MCP 调用失败", true)` **可重试**，课件骨架逐字） |
| `EngineConfiguration` 瘦身 | core `com.oryxos.core.agent` | 删除 `toolRegistry()` Bean 方法与内嵌 `EmptyToolRegistry` 类（18 节 javadoc 预告的替换动作）；其余 Bean 零改动（`PromptBuilder` 注入的 `ToolRegistry` 由 tool 模块配置供给，boot `scanBasePackages` 已含 `com.oryxos.tool`） |
| pom 两处声明 | oryxos-tool | `io.modelcontextprotocol.sdk:mcp-core` **0.18.3**（main，显式锁）+ `org.yaml:snakeyaml`（main，Boot BOM 管版本） |

**装配纪律（延续 18/19 节）**: 新 Spring 触点全部在 oryxos-tool（`@Component`×8 + `@Configuration`×1）；core 仅 `EngineConfiguration` 删除过渡 Bean 与 `ToolResult` 演进（均 17/18 节 javadoc 预告的既定动作）；`ProfileContext` 静态形态照旧。

## Testing strategy（按课件「验收 harness」执行）

测试策略句：测试策略按课件「验收 harness」执行：`OryxToolContractTest`、`ToolRegistryTest`、`FileToolsTest`/`ShellToolsTest`/`HttpToolsTest`、`McpToolAdapterTest`/`McpClientServiceTest`（覆盖 契约三件套遍历/三源注册与 Profile 过滤不多不少/正常+越界两条/MCP 失联隔离），外加 `InteractionToolsTest`/`WebSearchToolsTest`（扩展五工具守点），单测默认跑、集成冒烟打 `@Tag("integration")` CI 跳过；实现完成的定义是 `mvn clean verify` 全绿。

**harness 映射表**（测试类 ↔ 覆盖点 ↔ 模块）：

| 测试类 | 覆盖的验收点 | 模块 | 跑法 |
|---|---|---|---|
| `OryxToolContractTest` | **参数化遍历注册表每个工具**（`@MethodSource("allRegisteredTools")`，构造含全部 12 内置 + 注解来源 + mock MCP 来源的完整注册表）：工具名/描述/参数说明三件套非空——新工具自动纳入契约检查 | tool | 单测默认跑 |
| `ToolRegistryTest` | 三种来源工具都以 `OryxTool` 身份注册进同一注册表；按名查找；重名注册拒绝；Profile `tools` 过滤**恰好等于**声明列表（经 17 节 `PromptBuilder`+`Prompt.availableTools` 公开 record 断言，`PromptBuilder` 零改动）、未声明空集 | tool（+core 公开 record） | 单测默认跑 |
| `FileToolsTest` | 六方法各自"正常能跑通 + 越界会被拦"（临时目录为界，正常路径 `PermissiveSandbox`、越界路径注入**拒绝型 mock Sandbox** 验证首行 enforce 抛 `SandboxViolationException`、真实 IO 不发生）；`edit_file` 唯一匹配（零/多处命中报错且文件不动）；`grep`/`glob` 上限 200 截断注明；`grep` 跳过二进制 | tool | 单测默认跑 |
| `ShellToolsTest` | `shell` 正常执行返回输出；越界（拒绝型 mock）抛 `SandboxViolationException`；超时语义 | tool | 单测默认跑 |
| `HttpToolsTest` | `http_get` 正常取回响应体（MockWebServer 本地回环，真链路不外网）；**越界域名被拦下**（拒绝型 mock + `assertThrows`，课件正文用例逐字）；`http_post` 正常发送 | tool | 单测默认跑 |
| `McpToolAdapterTest` | mock `McpSyncClient`：name/description/inputSchema 映射 `tools/list` 返回；execute 转发参数**原样**（`CallToolRequest.arguments` 断言）；成功包 ok；失败包 `failure(..., retryable=true)` | tool | 单测默认跑 |
| `McpClientServiceTest` | mock 连接：**某个 MCP server 失联不能拖垮启动和其他工具**（bad client `listTools` 抛 `ConnectException` → `connectAll` 不抛、只 WARN，好 server 工具照常注册、bad 工具不存在）；配置文件解析（name/transport/command/env） | tool | 单测默认跑 |
| `InteractionToolsTest` | `ask_user` 经 `ConsoleUserInteraction` 读回一行（stdin 重定向）；`UnsupportedUserInteraction` 明确报错（非静默卡住） | tool | 单测默认跑 |
| `WebSearchToolsTest` | `web_search` 首行 enforce（拒绝型 mock 抛 `SandboxViolationException`）；结果渲染含 title/url/snippet（mock `SearchProvider` 不碰网） | tool | 单测默认跑 |

**课件关键回归测试（写出代码的，断言逻辑逐条保真落地，方法名译英文 + `@DisplayName` 承载课件原文）**：

1. `allRegisteredTools_haveCompleteContract`（@DisplayName「每个工具的契约三件套都不能缺」）——`@ParameterizedTest @MethodSource("allRegisteredTools")`：`assertNotNull(name/description/inputSchema)` 三连，课件注释语义（缺参数说明 Provider 翻译 Function Calling 直接卡死）进断言消息。
2. `serverDisconnectDoesNotBreakStartupOrOtherTools`（@DisplayName「某个MCP server失联 不能拖垮启动和其他工具」）——`when(badClient.listTools()).thenThrow(ConnectException)`；`connectAll()` 不抛；好 server 工具可 `find` 到、bad 工具 `find` 不到（harness 伪代码 `toolRegistry.contains(...)` 语义等价映射为 `find(name).isPresent()`——`ToolRegistry` 接口无 `contains`，17 节接口零改动）。
3. `httpGetReturnsResponseBody`（@DisplayName「http_get 应能取回响应」）——MockWebServer 本地回环 URL，`execute` 返回 `success=true` 且 `content` 非空。
4. `httpRequestOutsideWhitelistIsRejected`（@DisplayName「http_get 命中白名单外域名应被拦下」）——拒绝型 Sandbox mock + `assertThrows(SandboxViolationException.class)`（课件注释"23、24 节会讲清楚这里具体抛的是 SandboxViolationException"，本节前向接口已定名）。

**越界守点的时间裁定**（同 19 节 R2 模式）：本节"越界会被拦"以**调用位语义**承载——enforce 先于真实 IO、异常上抛不被吞（拒绝型 mock 承载拦截行为）；真实白名单匹配逻辑（路径穿越/命令 token/域名通配）与 enforce InOrder 顺序断言归 24 节 `WhitelistSandbox` 交付时补测。相关测试类 javadoc 与工具调用位双处注明归期。

## Risks & Mitigations

- **R-toolResult（前序 record 签名演进）**: 唯一前序公共类型改动点——新增分量放最后 + 工厂重载保兼容 + 16~19 节全部测试回归绿兜底；`toModelText`/既有模式匹配零破坏。
- **R-annotatedScan（注册发现机制）**: 不做全容器魔法扫描——内置工具 Bean 显式注入 `ToolRegistryConfiguration`（五个类全在本模块），`registerAnnotated(Object...)` 为课件 6.5 点名的公开入口；方式三业务方接入=装配处一行（全容器自动扫描属扩展演进，课件 6.5 实现回溯即此形态）。
- **R-mcpLifecycle（stdio 子进程泄漏）**: `McpSyncClient` 持有子进程/连接资源——连接失败即 close 半开连接；`@PreDestroy` 统一 close；测试全 mock 不真起子进程（真 server 连接属人工项）。
- **R-mcpAsync（SDK 内部 reactor）**: mcp-core 内部 reactor 实现（`McpAsyncClient` 存在）——OryxOS 代码只用 `McpSyncClient` 同步门面，H4⑤ 不破坏；依赖树含 reactor-core 属 SDK 内部事实，非本节引入使用。
- **R-shellTimeout（超时值未定）**: 课件只说"带超时"——定 30s 常量 + javadoc 注明理由，不立新配置键（避免越交付物创建配置概念，软门禁 1 边界内）。
- **R-grepEncoding（非 UTF-8 文件）**: `CharsetDecoder` 严格模式解码失败即跳过该文件（课件 6.2"跳过那个文件，而不是中断整次搜索"）。
- **R-yamlKeys（`mcp_servers.yaml` 键核对）**: 18 节 init 模板已有 servers 示例——implement 期逐键对照课件交付物（`name`/`transport`/`command`/`env`），如需改模板字符串（前序交付物）→ 软门禁停下报告。
- **R-P3C/静态门禁**: 测试方法名英文 + `@DisplayName` 中文；无 `System.out`（log.warn 走 SLF4J）；record 紧凑构造器防御拷贝（SpotBugs 先例）；无增强 switch `default ->` 写法。
- **R-boot（装配迁移冒烟）**: `EmptyToolRegistry` 删除 + tool 模块 `@Configuration` 顶上后，16 节 `OryxOsApplicationTests` 上下文起不起得来即装配回归证据；`ToolListCommand` 消费 `registry.all()` 零改动自动生效。

## Pre-Implement Verification Gate（H3）

**已完成（2026-09-02，本 plan 期实测）**：`mvn dependency:tree` 确认 `mcp-core:0.18.3` 在锁定链（spring-ai-client-chat 1.1.8 传递）；javap 核实 `McpClient$SyncSpec`/`McpSyncClient`/`StdioClientTransport`/`HttpClientSseClientTransport`/`McpSchema$CallToolResult`/`McpSchema$JsonSchema`/`MethodToolCallbackProvider$Builder.toolObjects(Object...)`/`ToolCallback.call(String)`/`ToolDefinition.name()/description()/inputSchema()` 存在且签名与计划用法一致。

**implement 期逐任务再核**：SnakeYAML `Yaml.load(...)` 读取形态；jackson `ObjectMapper.readTree/readValue` 对入参原文解析；`ProcessBuilder`/`process.waitFor(long, TimeUnit)` 超时形态；`RestClient.get()/post()` 形态（19 节已用）。核实不到 → 软门禁停下问用户。

## 关键设计决定（implement 期逐字执行的对齐锚）

1. **`OryxTool` 前序形态沿用**：`name()/description()/getInputSchema():String/execute(String)`——课件四能力语义同构、16/17 节 javadoc 已声明对齐 §6.1；本节零改动该接口。
2. **`ToolResult` 演进**：`record(boolean success, String output, String errorMessage, boolean retryable)`；`ok(output)`=retryable false、`failure(msg)`=false、`failure(msg, true)` 对应课件 `ToolResult.failure("MCP 调用失败", true)`；`toModelText` 零改动。
3. **Sandbox 前向五件**：`enforce(SandboxAction)`、`ActionType` 四值、`SandboxAction(ActionType, String)`、`SandboxViolationException`、`PermissiveSandbox`；接口签名零实现词汇（§6.7 中立性自查：用最重 microVM 实现反向套签名也应干净套入）。
4. **工具执行语义**：`@Tool` 方法返回 String（adapter 包 ok）；异常上抛（adapter 不吞、`ToolExecutor` 统一转失败+审计）；**每方法第一行 enforce**（课件"这是硬规矩"）；本节越界守点=调用位语义（拒绝型 mock），真白名单+InOrder 归 24 节。
5. **MCP 骨架同构**（课件「三」两段代码逐字对齐语义）：`connectAll` 循环 try-connect→listTools→register；失联 `log.warn("MCP server {} 连接失败，跳过它的工具")` + continue；adapter `callTool` 转发、失败包可重试 failure。
6. **注册表装配**：`ToolRegistryConfiguration` 显式注入 `FileTools/ShellTools/HttpTools/InteractionTools/WebSearchTools/NotifyTools` 六 Bean 逐个 `registerAnnotated`；MCP 注册在 `McpClientService.connectAll`（`ToolRegistryConfiguration` 触发）；重名抛 `IllegalStateException`。
7. **Profile 过滤验证路径**：`DefaultToolRegistry` + 17 节 `PromptBuilder` 构造 → `build(session, profile)` → `Prompt.availableTools()` 断言恰好等于声明列表（`PromptBuilder`/`Prompt` 零改动，复用 17 节测试构造手法）。
8. **包结构**：`com.oryxos.tool.{registry,builtin,mcp,sandbox,interaction,search}`；课件 `io.oryxos` 前缀按 `com.oryxos` 既有约定适配（16~19 节先例）；内置工具与 `NotifyTools` 同汇 `builtin` 包（19 节 plan 第 7 条预告）。
