---
description: "Task list for 第 20 节 Tool 体系（统一注册表 + 注解扫描 + MCP 接入 + 内置工具 12 个 + Sandbox 前向接口）"
---

# Tasks: 第 20 节 Tool 体系

**Input**: Design documents from `/specs/020-lesson20-tool/`（spec.md + plan.md）；课件 `docs/class/第20节：Tool 体系 原理解析、实现与代码讲解.md`。

**Prerequisites**: plan.md（required）、spec.md（required）。前序依赖：**第 16/17/18/19 节全部交付物**（H0 已存在性核对：`Profile.tools`/`ProfileLoader`/`ProfileRegistry`/`OryxTool`（四方法含 `getInputSchema`）/`ToolResult`/`ToolRegistry` 接口/`ToolExecutor`/`PromptBuilder.availableTools`/`EngineConfiguration`（EmptyToolRegistry 过渡 Bean）/`ToolListCommand`/`NotifyTools` 均就位）。

**Tests**: harness 先行/伴随--测试任务与对应实现任务成对出现；`mvn clean verify` 全绿为实现完成定义；本节无 `@Tag("integration")` 项（MCP 连接/DuckDuckGo 全 mock 不碰网；方式一/方式三真跑、真 MCP server 属人工项）。

**Organization**: 按 spec 依赖序组织（pom 依赖 -> Sandbox 前向五件 -> core 基座演进 -> registry 三件与装配切换 -> 内置工具六类 -> MCP 接入 -> 收尾）；包根 `com.oryxos`，标识符一律英文 ASCII（宪法 §9）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 不同文件、无未完成依赖，可并行
- **[Story]**: US1（统一抽象与注册表）/ US2（内置工具第一批）/ US3（MCP 方式二）/ US4（注解注册+notify 收编）/ US5（扩展五工具）/ 共享底座
- 路径相对仓库根；包根：`com.oryxos`

## Path Conventions

- Maven 多模块：`oryxos-tool/src/main/java/com/oryxos/tool/{sandbox,registry,builtin,interaction,search,mcp}/...`；core 演进 `oryxos-core/src/main/java/com/oryxos/core/{tool,agent}/...`
- 测试：对应模块 `src/test/java/...`
- **装配纪律（延续 18/19 节）**: 本节新 Spring 触点全部在 oryxos-tool（boot `scanBasePackages` 已含 `com.oryxos.tool`）；core 仅两处既定改动（`ToolResult` 演进、`EngineConfiguration` 删过渡 Bean，均 17/18 节 javadoc 点名）；前序其余类零改动零注解
- **零触碰**: `oryxos-storage`、`001-init.sql`、`ReActLoop`/`PromptBuilder`/`ToolExecutor`/`Profile`/`NotifyTools` 本体/`ToolListCommand`

---

## Phase 0: 前置核对（部分完成）

- [x] T-R0 H0 依赖检查：16/17/18/19 节交付物逐项存在性核对通过；课件「动手前先检查 `getInputSchema()`」条款核对通过；分支 `020-lesson20-tool` 就位（自 master 切出，命名沿 017~019 先例）；第三方 API 核实完成（`mcp-core` 0.18.3 在锁 + Spring AI 注解/`ToolCallback`/`ToolDefinition` javap 确认，见 plan Pre-Implement Gate）。
- [x] T-R0b 基线验证：`mvn clean verify` 基线绿（19 节收尾态）——implement 第一个动作先跑，红了当场报告不前进。

## Phase 1: 依赖声明（共享底座）

**Purpose**: oryxos-tool 补齐本节编译所需依赖（锁定链内工件，显式锁版本处注明理由）。

- [x] T-D1 oryxos-tool pom：main scope 增 `io.modelcontextprotocol.sdk:mcp-core`（**显式锁 0.18.3**——spring-ai-bom/parent 均不管理其版本，plan 实测；锁定链内经 spring-ai-client-chat 传递存在，非新第三方家族）与 `org.yaml:snakeyaml`（Boot BOM 管版本，显式化 starter 传递依赖，18 节 R3 先例）。加完跑 `mvn -pl oryxos-tool dependency:tree` 核对两工件在锁且无 convergence 冲突（enforcer 既有规则保持绿）。

## Phase 2: Sandbox 前向接口五件（共享底座，US2 前置）

**Purpose**: 中立安全校验接口 + 默认放行临时实现（技术方案 §6.7；本体 24 节）。

- [x] T-S1 [P] `ActionType` enum（`com.oryxos.tool.sandbox`）：四值 `FILE_READ`/`FILE_WRITE`/`SHELL_COMMAND`/`HTTP_REQUEST`（§6.7 逐字；javadoc 注明"文件读写分开便于未来按读/写分权限"）。
- [x] T-S2 [P] `SandboxAction`（`record SandboxAction(ActionType type, String target)`，紧凑构造器 null 防御）+ `SandboxViolationException`（`extends RuntimeException`，javadoc 注明异常信息复用 `ToolExecutor` 失败审计路径、不为 Sandbox 新增审计）。
- [x] T-S3 [P] `Sandbox` 接口（`void enforce(SandboxAction action)`——签名零"白名单/容器/VM"词汇，§6.7 中立性自查锚点）+ `PermissiveSandbox`（`@Component`，默认放行空实现，javadoc 注明"临时装配，24 节 `WhitelistSandbox` 替换、调用方零改动"）。

## Phase 3: core 基座演进（共享底座，唯二前序改动点之一）

- [x] T-C1 [P] `ToolResult` 增补 `retryable`（core `com.oryxos.core.tool`）：record 分量序 `(boolean success, String output, String errorMessage, boolean retryable)`；`ok(output)` 保留（false）、新增 `ok(output, retryable)`、`failure(msg)` 保留（false）、新增 `failure(msg, retryable)`（课件 `ToolResult.failure("MCP 调用失败", true)` 对应后者）；`toModelText` 零改动；javadoc 更新为 §6.1 四维度终局形态（17 节"由第 20 节演进"预告兑现）。**伴随回归**：`mvn -pl oryxos-core test` + provider/channel-cli/boot 模块既有测试全绿（`ok`/`failure` 既有调用方零破坏证据）。

## Phase 4: registry 三件与装配切换（US1/US4 主体）

**Purpose**: 统一注册表真实现 + 注解扫描注册管道 + 过渡 Bean 替换（18 节 javadoc 预告动作）。

- [x] T-R1 [P] `AnnotatedToolAdapter`（`com.oryxos.tool.registry`）：包 Spring AI `ToolCallback` 为 `OryxTool`——`name()/description()/getInputSchema()` 直接映射 `ToolDefinition` 三方法（已 javap 核实同构）；`execute(String)` → `callback.call(input)` 正常返回包 `ToolResult.ok`、异常**原样上抛**（课件 harness `assertThrows` 语义；`ToolExecutor` 统一转失败+审计）；javadoc 注明"只用 Spring AI 注解与 schema 生成一半，ToolCallback 不接任何 ChatModel，执行唯一收口 OryxOS ToolExecutor"（原则四）。
- [x] T-R2 [P] `DefaultToolRegistry`（`com.oryxos.tool.registry`，implements 17 节 `ToolRegistry`）：`ConcurrentHashMap`；`find`/`all`（快照）语义零变；注册入口 `register(OryxTool)`（重名抛 `IllegalStateException` 防静默覆盖）、`registerAll(List)`、`registerAnnotated(Object... toolBeans)`（课件 6.5 原话命名：逐 bean `MethodToolCallbackProvider.builder().toolObjects(bean).build().getToolCallbacks()` → 每个callback 包 `AnnotatedToolAdapter` 注册）；javadoc 注明"ReAct 循环不感知 Tool 来源"。
- [x] T-R3 `ToolRegistryConfiguration`（`com.oryxos.tool.registry`，`@Configuration`）：`@Bean ToolRegistry`——构造注入 `FileTools`/`ShellTools`/`HttpTools`/`InteractionTools`/`WebSearchTools`/`NotifyTools` 六 Bean 逐个 `registerAnnotated`（课件 6.5"装配处多 registerAnnotated 两行"；`NotifyTools` 收编=19 节 Resolve R1 预告兑现，本体零改动）；`@Bean Sandbox`（`PermissiveSandbox`）；`@Bean UserInteraction`（`ConsoleUserInteraction`）。**依赖 T-F1~T-F6 六工具类与 T-M 系列的类型存在**（编译序：Phase 5/6 类型先落）。
- [x] T-R4 `EngineConfiguration` 删过渡 Bean（core `com.oryxos.core.agent`）：删除 `toolRegistry()` Bean 方法与内嵌 `EmptyToolRegistry` 类（18 节 javadoc"届时本类删除此内嵌类"逐字兑现）；其余 Bean 零改动（`PromptBuilder` 的 `ToolRegistry` 由 tool 模块配置供给）。**伴随**：16 节 `OryxOsApplicationTests` 上下文冒烟绿（装配迁移回归证据）。
- [x] T-R2T `ToolRegistryTest`（`com.oryxos.tool.registry`）：①三种来源工具（匿名 `OryxTool` 直register + `registerAnnotated` 包装 Bean + mock MCP adapter）都以统一身份注册、`find`/`all` 语义正确；②重名注册抛 `IllegalStateException`；③Profile `tools` 过滤**恰好等于**声明列表（`DefaultToolRegistry` + 17 节 `PromptBuilder` 构造 → `build(session, profile)` → `Prompt.availableTools()` 断言——多一个少一个都红；`PromptBuilder`/`Prompt` 零改动，复用 17 节测试构造手法）；④未声明 `tools` → 空集（17 节白名单 fail-closed 语义）。harness 先行/伴随。

## Phase 5: 内置工具六类（US2/US5 主体，每类 harness 伴随）

**Purpose**: 文件六方法 + shell + HTTP 两方法 + ask_user + web_search；**每方法第一行 `sandbox.enforce(...)`**（课件"这是硬规矩"）；正常路径 `PermissiveSandbox`、越界守点用拒绝型 mock Sandbox 验证调用位语义（enforce 先于 IO、异常上抛），真白名单+InOrder 归 24 节（测试类 javadoc 与调用位双处注明归期）。

- [x] T-F1 [P] `FileTools` 基础三方法（`com.oryxos.tool.builtin`，`@Component` 构造注入 `Sandbox`）：`read_file(path)`/`write_file(path, content)`/`list_dir(path)` 三个 `@Tool` 方法，首行 enforce(FILE_READ/FILE_WRITE/FILE_READ)（§6.7"文件读写分开"）；返回文本/确认语；IO 异常上抛不吞。
- [x] T-F1T `FileToolsTest`（基础三方法）：各方法正常读写列（`@TempDir` 白名单内）+ 越界（拒绝型 mock Sandbox 首行抛 `SandboxViolationException`、真实 IO 不发生）两条；`write_file` 后 `read_file` 往返一致。
- [x] T-F2 [P] `FileTools` 扩展三方法：`edit_file(path, old_text, new_text)`——**唯一匹配**语义（零命中/多处命中抛 `IllegalStateException` 且文件一个字不动，课件 6.2 逐字）；`grep(pattern, path)`——递归搜内容返回 `文件:行号:内容` 行式、**上限 200 条**超限截断注明、二进制/非 UTF-8 文件跳过不中断（严格解码失败即跳过）；`glob(pattern)`——通配找路径、同上限；各首行 enforce。
- [x] T-F2T `FileToolsTest` 扩展守点：edit_file 唯一匹配三态（命中替换/零命中报错文件不动/多处命中报错文件不动）；grep 上限截断注明 + 二进制跳过；glob 通配与上限；越界路径拒绝型 mock 两条。
- [x] T-F3 [P] `ShellTools`（`com.oryxos.tool.builtin`，`@Component` + `Sandbox`）：`shell(command)`——首行 enforce(SHELL_COMMAND) → `ProcessBuilder` 分隔执行 → **30s 超时**（课件只说"带超时"未定值：常量 + javadoc 注明理由，不立新配置键）；超时/非零退出语义明确（含 stderr 摘要）；工作目录为 `.oryxos` 工作区。
- [x] T-F3T `ShellToolsTest`：正常执行返回输出（echo）；越界（拒绝型 mock）抛 `SandboxViolationException`；超时语义（sleep 超限被杀、明确报错）。
- [x] T-F4 [P] `HttpTools`（`com.oryxos.tool.builtin`，`@Component` + `Sandbox` + `RestClient`）：`http_get(url)`——课件骨架逐字（description"发起一个 HTTP GET 请求，返回响应体"，首行 enforce(HTTP_REQUEST)）→ 返回响应体；`http_post(url, body)`——首行 enforce → POST JSON 返回响应体；异常上抛不吞。
- [x] T-F4T `HttpToolsTest`（MockWebServer 本地回环）：①`httpGetReturnsResponseBody`（@DisplayName「http_get 应能取回响应」——`execute` success 且 content 非空，课件用例逐字）；②`httpRequestOutsideWhitelistIsRejected`（@DisplayName「http_get 命中白名单外域名应被拦下」——拒绝型 mock + `assertThrows(SandboxViolationException)`，课件用例逐字；javadoc 注明真白名单 24 节）；③http_post 正常发送（MockWebServer 收到 body）。
- [x] T-F5 [P] `UserInteraction` 三件（`com.oryxos.tool.interaction`）：接口 `String ask(String question)`（课件 6.3 逐字："拿不到回答就抛异常，绝不静默卡住"）；`ConsoleUserInteraction`（读终端一行，`System.in`）；`UnsupportedUserInteraction`（抛 `IllegalStateException("当前渠道不支持交互")`）；javadoc 注明"接口表达意图、实现按渠道挂"（与 19 节 NotifyChannelAdapter、本节 Sandbox 同套路）+ "CLI ask_user 与 chat 读行同 stdin、同步循环一问一答不并发争抢"（课件 6.3）。
- [x] T-F5T `InteractionToolsTest`（`com.oryxos.tool.builtin`）：`ask_user(question)` 经 `ConsoleUserInteraction` 读回一行（`ByteArrayInputStream` 重定向 stdin）；`UnsupportedUserInteraction` 明确报错（非静默卡住）；`InteractionTools.ask_user` 工具方法委托 + 回答原样返回。
- [x] T-F6 [P] `SearchProvider` 两件（`com.oryxos.tool.search`）：接口 `record SearchResult(String title, String url, String snippet)` + `List<SearchResult> search(String query)`（课件 6.4 逐字）；`DuckDuckGoSearchProvider`（免 key Instant Answer API，`RestClient`，javadoc 注明"换 Google/Bing/Tavily 只加实现类"）。
- [x] T-F6T `WebSearchToolsTest`（`com.oryxos.tool.builtin`）：`WebSearchTools.web_search(query)` 首行 enforce(HTTP_REQUEST)（拒绝型 mock 抛 `SandboxViolationException`——"不能因为叫搜索就绕过治理"）；mock `SearchProvider` 渲染结果含 title/url/snippet 行式；空结果语义明确。javadoc 注明 enforce InOrder 顺序断言归 24 节（同 19 节模式）。

## Phase 6: MCP 接入（US3 主体）

**Purpose**: 方式二协议化收编（31 节日报 Agent 硬依赖）；全 mock 不碰网、不真起 stdio 子进程。

- [x] T-M1 [P] `McpServerConfig`（`com.oryxos.tool.mcp`，`record McpServerConfig(String name, String transport, String command, Map<String,String> env)`——课件骨架点名名字）+ YAML 解析（SnakeYAML 读 `.oryxos/mcp_servers.yaml` 的 `servers` 列表；空/缺失文件返回空列表不报错）。
- [x] T-M2 [P] `McpToolAdapter`（`com.oryxos.tool.mcp`，implements `OryxTool`，课件骨架逐字同构）：`name()/description()` 直接映射 `McpSchema$Tool`；`getInputSchema()` jackson 序列化 `McpSchema$JsonSchema`；`execute(String)`：jackson 解析入参原文为 Map → `McpSyncClient.callTool(new CallToolRequest(name, args))` **参数原样转发** → `CallToolResult` 包 `ToolResult`（isError/异常 → `ToolResult.failure("MCP 调用失败", true)` **可重试**，课件骨架逐字）。
- [x] T-M2T `McpToolAdapterTest`（mock `McpSyncClient`）：①name/description/inputSchema 映射 `tools/list` 返回（inputSchema 序列化为合法 JSON 字符串）；②execute 转发参数原样（`ArgumentCaptor` 断言 `CallToolRequest.arguments` 等于入参解析结果）；③成功包 ok（content 文本提取）；④isError/抛异常包 `failure(..., retryable=true)`。
- [x] T-M3 `McpClientService`（`com.oryxos.tool.mcp`，`@Component` 构造注入 `DefaultToolRegistry`，课件骨架逐字同构）：`@PostConstruct connectAll()`——读配置逐 server try-connect（`stdio`: `StdioClientTransport`（command 首个 token 为命令、余为参数 + `env` 合并进程环境）；`sse`: `HttpClientSseClientTransport`）→ `McpClient.connect(syncSpec)` → `listTools()` 逐个 `register(new McpToolAdapter(client, spec))`；**单 server 失败 `log.warn("MCP server {} 连接失败，跳过它的工具", cfg.name(), e)` + continue、其余照常、启动不炸**（课件骨架注释逐字）；`@PreDestroy` close 全部连接（stdio 子进程泄漏防护）。
- [x] T-M3T `McpClientServiceTest`（mock 连接工厂/`McpSyncClient`）：①`serverDisconnectDoesNotBreakStartupOrOtherTools`（@DisplayName「某个MCP server失联 不能拖垮启动和其他工具」——bad client `listTools` 抛 `ConnectException` → `connectAll()` 不抛、只 WARN，好 server 工具 `find` 到、bad 工具 `find` 不到；harness 伪代码 `contains` 语义映射 `find(name).isPresent()`，`ToolRegistry` 接口零改动）；②配置解析（两 server name/transport/command/env 逐键）；③空配置启动零注册不报错。

## Phase 7: 装配收尾与全量门禁

- [x] T-A1 `OryxToolContractTest`（`com.oryxos.tool.registry`，**课件 harness 最值钱测试**）：`@ParameterizedTest @MethodSource("allRegisteredTools")`——构造**完整注册表**（12 个内置工具 + 注解来源 + mock MCP 来源），`allRegisteredTools_haveCompleteContract`（@DisplayName「每个工具的契约三件套都不能缺」）断言 `name/description/inputSchema` 三连非空（断言消息承载课件语义"缺了它，Provider 翻译 Function Calling 时直接卡死"）；`all()` 快照不可变；新工具自动纳入（加一个即被遍历到）。
- [x] T-A2 `mcp_servers.yaml` 键核对（plan R7）：init 模板（18 节交付物）逐键对照课件交付物 `name`/`transport`/`command`/`env`；**如需改模板字符串 → 软门禁停下报告**，不擅改。
- [x] T-A3 全量门禁与报告：`mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD/Spotless）；16/17/18/19 节测试回归绿（`ToolResult` 演进与过渡 Bean 替换的跨节契约证据）；H4 六条全局不变量逐条自查（①Sandbox 调用位就位/24 节接线注明 ②工具执行成败落 `tool_invocations` 既有路径 ③无明文 key ④`session_id` 拼接不触碰 ⑤无 Reactor/`CompletableFuture`/自建线程池（MCP 用同步门面）⑥无 Spring AI 自动工具执行路径）；输出节级验收报告（六项证据 DoD + 人工项清单 + 变更总结三段结构）。

## 课件「本节交付物」对账表（Step 5 停点核对用）

| 课件交付物 | 本任务清单落点 | 时点 |
|---|---|---|
| 代码：`OryxTool` 接口（含 `getInputSchema`） | 无任务——16/17 节已前向交付四方法（课件「动手前先检查」条款核对通过），本节零改动 | 已就位 ✅ |
| 代码：`ToolResult` | T-C1（增补 `retryable`，17 节 javadoc 点名的既定演进点） | 本节 ✅ |
| 代码：`ToolRegistry` | 接口 17 节已交付；真实现 T-R2 + 装配切换 T-R3/T-R4（`EmptyToolRegistry` 替换，18 节 javadoc 点名） | 本节 ✅ |
| 代码：`AnnotatedToolAdapter` | T-R1 | 本节 ✅ |
| 代码：`FileTools`（3 个） | T-F1（三方法）+ T-F2（扩展 edit_file/grep/glob，课件第六部分） | 本节 ✅ |
| 代码：`ShellTools` | T-F3 | 本节 ✅ |
| 代码：`HttpTools`（2 个） | T-F4 | 本节 ✅ |
| 代码：`McpClientService` | T-M1（配置）+ T-M3 | 本节 ✅ |
| 代码：`McpToolAdapter` | T-M2 | 本节 ✅ |
| 代码：`NotifyTools`（19 节）在此完成注册 | T-R3（装配显式注入收编；本体零改动） | 本节 ✅ |
| 代码：`Sandbox` 前向接口五件（含 `PermissiveSandbox` 临时装配） | T-S1/T-S2/T-S3（本体 `WhitelistSandbox` 23/24 节） | 本节交付接口+临时实现 ✅ / 本体 24 节 |
| 测试：`OryxToolContractTest` | T-A1 | 本节 ✅ |
| 测试：`ToolRegistryTest` | T-R2T | 本节 ✅ |
| 测试：`FileToolsTest`/`ShellToolsTest`/`HttpToolsTest` | T-F1T/T-F2T、T-F3T、T-F4T | 本节 ✅ |
| 测试：`McpClientServiceTest`/`McpToolAdapterTest` | T-M3T、T-M2T | 本节 ✅ |
| 配置：`.oryxos/mcp_servers.yaml`（name/transport/command/env） | T-M1（解析）+ T-A2（键核对；模板 18 节 init 已生成，如需改→软门禁报告） | 本节 ✅（消费） |
| 配置：Profile 的 `tools` 字段过滤 | 无任务——17 节 `PromptBuilder.availableTools` 已交付；T-R2T 在真注册表上验证"恰好等于、不多不少" | 已就位+本节验证 ✅ |
| 扩展交付：`edit_file`/`grep`/`glob` | T-F2/T-F2T | 本节 ✅ |
| 扩展交付：`ask_user`（`InteractionTools` + `UserInteraction` 抽象） | T-F5/T-F5T | 本节 ✅ |
| 扩展交付：`web_search`（`WebSearchTools` + `SearchProvider` 抽象） | T-F6/T-F6T | 本节 ✅ |
| （非本节）`MemoryTools`（save_memory/recall_memory） | 无任务——技术方案 §6.2"归 Memory 模块"，22 节交付后注册 | 22 节 |
| （非本节）Sandbox 本体/白名单配置键/InOrder 断言 | 无任务——23/24 节（本节调用位+临时实现已就位） | 24 节 |
