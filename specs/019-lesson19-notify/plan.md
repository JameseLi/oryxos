# Implementation Plan: 第 19 节 Notify（通知推送模块）

**Branch**: `019-lesson19-notify` | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/019-lesson19-notify/spec.md`；课件 `docs/class/第19节：Notify 模块 原理解析、实现与代码讲解.md`；技术方案对位 §6.8/§8.2/§6.7/§9.2。

## Summary

交付 OryxOS 的**出站推送出口**：`NotifyChannelAdapter` 中立接口（表达"把一条内容送到某个通知目标"，签名零渠道词汇）+ `NotifyTarget`（渠道类型 + 键值配置）+ `WebhookNotifyAdapter`（核心阶段唯一实现：POST `{"content": ...}` 到配置 URL）+ `NotifyTools`（`notify` 内置工具类本体：渠道解析 + 缺省取第一 + 未配置明确报错；`@Tool` 注册归 20 节、`Sandbox.enforce` 调用位注释预留归 24 节接线）。形态**逐字对齐课件骨架**（接口签名、webhook body、harness 断言逻辑逐条保真），课件示意代码的三处现实适配（包根/`ToolResult` 工厂名/`ProfileContext` 静态形态）按 spec R1 裁定执行。`notify_channels` 字段 16 节已交付，本节纯消费、前序公共接口零改动。

## Technical Context

**固定技术栈句**：JDK 21 + Spring Boot 3.x + Spring AI Alibaba（动手前先跑 `mvn dependency:tree` 确认锁定 BOM 里目标依赖存在）、SQLite + Spring Data JPA。凭证走环境变量占位，不落明文。SQLite 用手工建表脚本，不依赖 `hibernate.ddl-auto=update`。

- **Language/Version**: Java 21（本节全同步阻塞调用，无并发逻辑）。
- **Primary Dependencies**:
  - Spring Boot **3.5.16** BOM：`org.springframework:spring-web`（`RestClient`，6.1+ 内置同步 HTTP 客户端门面）本节在 oryxos-tool **显式声明**（不写版本）；`com.squareup.okhttp3:mockwebserver`（test scope）新增（spec R5，课件 harness 点名；Boot 3.5 BOM 已移除 okhttp3 版本管理，显式锁 **4.12.0**--implement 期实测修正）。
  - Spring AI BOM **1.1.8**：`spring-ai-model`（`@Tool`/`@ToolParam` 注解）core 已依赖，oryxos-tool 显式化（spec R4，18 节 R3 先例）。
  - SQLite/JPA：本节零触碰（无新表、无新实体、无 DDL 变更--审计走 `ToolExecutor` 既有路径）。
- **Storage**: 无持久化变更（FR6 审计零新增；`notify` 经 20 节注册后自动落 `tool_invocations`）。
- **Testing**: JUnit 5 + Mockito + AssertJ + MockWebServer（本地回环假 webhook，课件明言"不算外网依赖，仍是单测层"）。本节无 `@Tag("integration")` 项（真 webhook 验配置属人工项，20 节注册后可验）。
- **Target Platform**: 企业内网单实例；fat JAR 结构不变。
- **语法禁区句**：避开 P3C/ASM 解析不了的 Java 18+ 语法形态（如增强 switch 的 `default ->` 写法），静态检查是构建门禁。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design. 原则来源 `.specify/memory/constitution.md`。*

| # | 原则 | 判定 | 证据/说明 |
|---|------|------|-----------|
| I | 固定技术栈与九模块结构 | ✅ | 只在既有 oryxos-tool 模块内新增 notify 包 + builtin 包（5 个新文件 + 1 装配类 + pom 依赖声明），不增删模块；JDK 21 + Boot 3.x；spring-web/mockwebserver/spring-ai-model 均为锁定 BOM 内工件。 |
| II | 核心阶段只做运行时内核 | ✅ | 交付出站推送出口；**不做**逐家专用 API/非 HTTP 渠道/Sandbox 本体/注册管道/富文本推送（见"明确不做"）。 |
| III | 自实现 ReAct 循环 | ✅ | 本节不触碰循环；`notify` 只是未来被循环调用的工具之一（20 节注册后）。 |
| IV | Spring AI 只用一半 | ✅ | `@Tool` 注解仅作方法标记（20 节 `AnnotatedToolAdapter` 生成 schema 用），**不启用任何自动 tool 执行路径**；本节无模型调用。 |
| V | 三档接入·零代码优先 | ✅ N/A | 本节不交付 Tool 体系（§20）；`NotifyTools` 是内置 Tool 的类本体，注册动作归 20 节。 |
| VI | 审计数据 Day One 落库 | ✅ | `notify` 走 17 节 `ToolExecutor` 既有"异常->失败结果->`tool_invocations`"路径（spec R6 报错语义），不新增审计逻辑；本节单测不经 ToolExecutor（注册前），20 节注册后链路自动生效。 |
| VII | 每个 User Story 必须可演示 | ✅ | US1 harness 全绿 + `mvn clean verify` 全绿；人工项=接口中立性自查（思维练习）；真 webhook 验证 20 节注册后可做（见验收报告）。 |

## 明确不做（边界）

- **不逐家接专用 API**（企业微信/飞书/钉钉签名、AccessToken 刷新）--扩展阶段按 `channelType` 新增 Adapter。
- **不接非 HTTP 渠道**（邮件 SMTP、短信签名 API）--扩展阶段专用 Adapter。
- **不实现 Sandbox 本体**（`Sandbox`/`SandboxAction`/`ActionType` 归 23/24 节；本节只在 `notify` 执行路径留调用位注释）。
- **不做 `@Tool` 注册管道**（`AnnotatedToolAdapter`/schema 生成归 20 节；本节 `NotifyTools` 不进 `ToolRegistry`，18 节 `EmptyToolRegistry` 继续顶位）。
- **不新增审计逻辑/表**（复用 `ToolExecutor` + `tool_invocations` 既有路径）。
- **不做富文本/卡片推送**（复杂格式走 MCP 方式二，课件 6.4 路二）。
- **不改前序节公共接口**（`Profile.ChannelRef`/`ProfileContext`/`ToolResult`/`OryxTool`/`EngineConfiguration` 零改动）。
- 另（16~18 节已定，本节延续不做）：认证/限流、SSE/WebSocket、多租户。

## Project Structure（模块落位 + 文件级清单）

模块落位（skill 第 19 行）：notify 包（Adapter/Target/Webhook 实现/`NotifyTools`）-> **oryxos-tool**。

| 组件 | 模块/包 | 说明 |
|---|---|---|
| `NotifyChannelAdapter` 接口 | `com.oryxos.tool.notify` | `void send(NotifyTarget target, String content)`--课件骨架逐字对齐；javadoc 注明"接口表达意图不表达实现"（中立性锚点）与"新增渠道只加实现类不改接口/调用方" |
| `NotifyTarget` | `com.oryxos.tool.notify` | `record NotifyTarget(String channelType, Map<String, String> config)`，紧凑构造器防御拷贝（口径照抄 `Profile.ChannelRef`）；渠道细节由实现类自行解释 |
| `WebhookNotifyAdapter` | `com.oryxos.tool.notify` | `@Component implements NotifyChannelAdapter`；构造注入 `RestClient`；`send` 读 `target.config().get("url")` -> `restClient.post().uri(url).contentType(APPLICATION_JSON).body(Map.of("content", content)).retrieve().toBodilessEntity()`--课件骨架逐字对齐；**不捕获** `RestClientResponseException`（异常上抛，spec R6）；url 缺失抛 `IllegalStateException`（明确报错） |
| `NotifyTools` | `com.oryxos.tool.builtin` | `@Component`，构造注入 `NotifyChannelAdapter`（课件骨架三依赖中 Sandbox 未就位不注入、ProfileContext 为静态类不注入--spec R1）；`@Tool(description = "把一条消息推送到当前 Agent 配置好的通知渠道") public ToolResult notify(String content, @ToolParam(required = false, description = ...) String channel)`；执行序：`ProfileContext.current()` 取 Profile -> 解析 `notifyChannels`（channel 缺省取第一、非空匹配 type 未中报错、空列表报错--spec R6 `IllegalStateException`）-> **Sandbox.enforce 调用位注释（24 节接线）** -> `adapter.send` -> `ToolResult.ok("已推送")`；异常不捕获（上抛给 ToolExecutor 统一转失败+审计） |
| `NotifyConfiguration`（装配类） | `com.oryxos.tool.notify` | `@Configuration` 提供 `@Bean RestClient restClient()`（`RestClient.create()`，spec R4）；javadoc 注明 20 节 Tool 体系装配如有需要再演进 |
| pom 两处声明 | oryxos-tool | 增 `org.springframework:spring-web` + `org.springframework.ai:spring-ai-model`（main，不写版本走 BOM）；`com.squareup.okhttp3:mockwebserver`（test，spec R5） |

**装配纪律（延续 18 节）**: 本节新 Spring 触点仅 `WebhookNotifyAdapter`/`NotifyTools`（`@Component`）+ `NotifyConfiguration`（`@Configuration`），全部在 oryxos-tool（boot `scanBasePackages` 已含 `com.oryxos.tool`，零改动）；core/storage 前序类零改动零注解；`NotifyTools` 未注册进 `ToolRegistry`（18 节 `EmptyToolRegistry` 顶位不变）。

## Testing strategy（按课件「验收 harness」执行）

测试策略句：测试策略按课件「验收 harness」执行：`WebhookNotifyAdapterTest`（第一批，本节全绿）、`NotifyToolsTest`（第二批中不依赖 Sandbox 的子集：未配置明确报错、channel 缺省取第一个渠道；enforce InOrder 顺序断言归 24 节接线后补齐--课件「实现顺序说明」的构建顺序裁定），单测默认跑（本节无集成冒烟项）；实现完成的定义是 `mvn clean verify` 全绿。

**harness 映射表**（测试类 ↔ 覆盖点 ↔ 模块）：

| 测试类 | 覆盖的验收点 | 模块 | 跑法 |
|---|---|---|---|
| `WebhookNotifyAdapterTest` | 发送后收到的 POST body 里带 `content`；URL 来自 `NotifyTarget.config` 而不是硬编码（不同 url 的 Target 各自送达）；webhook 返回 5xx 时异常向上抛、不静默吞掉 | tool（MockWebServer 本地回环） | 单测默认跑 |
| `NotifyToolsTest`（本节子集） | `notify_channels` 未配置 -> 明确报错（非静默）；`channel` 参数缺省 -> 取第一个渠道；推送内容经 adapter 原样送出；指定 channel 未命中 -> 明确报错 | tool（Mockito mock `NotifyChannelAdapter` + `ProfileContext` set/clear） | 单测默认跑 |
| （24 节补齐）`NotifyToolsTest` enforce 顺序断言 | `enforce` 先于 `send` 被调用（`InOrder` 钉死--顺序反了就是漏洞） | tool（mock Sandbox + Adapter） | 24 节接线后 |

**课件关键回归测试（写出代码的，断言逻辑逐条保真落地，方法名译英文 + `@DisplayName` 承载课件原文）**：

`enforceBeforeSendOrderPinnedByInOrder`（@DisplayName「发送前必须先过白名单校验」）--**24 节补齐**（依赖 Sandbox 前向接口），本节先在 `NotifyToolsTest` javadoc 与 `NotifyTools` 调用位双处注明归期：

```java
notifyTools.notify("hello", "default");

InOrder inOrder = inOrder(sandbox, adapter);
inOrder.verify(sandbox).enforce(argThat(a -> a.type() == ActionType.HTTP_REQUEST));
inOrder.verify(adapter).send(any(), eq("hello"));   // 校验在前，发送在后--顺序反了就是漏洞
```

（`WebhookNotifyAdapterTest` 三个守点与 `NotifyToolsTest` 两个守点按 harness 表格逐条落地，断言语义保真：body 带 `content`、URL 随 Target 变化、5xx 上抛、未配置抛明确异常、缺省取第一。）

## Risks & Mitigations

- **R1~R6（spec 记录）**: NotifyTools 拆解时点/测试子集/config 类型收窄/RestClient 供给/MockWebServer 依赖/报错形态 -> Step 5 固定停点与用户对账后才 implement。
- **R-typeMatch（channel 匹配键语义）**: 课件只说"channel 对应 notify_channels、缺省取第一个"，未明说匹配键。本节裁定：匹配 `ChannelRef.type`（与 `NotifyTarget.channelType` 同源，多渠道同 type 时取第一个匹配）--spec FR3"未命中明确报错"可测；harness 未覆盖多渠道按名选取（不自行发挥加 named channel 概念，软门禁 1 边界内）。
- **R-configNarrow（Map<String,Object> -> Map<String,String>）**: `Profile.ChannelRef.config` 值可能非 String（YAML 数值/布尔）；`NotifyTools` 收窄时 `String.valueOf` + null 值剔除，收窄失败（url 缺失）走明确报错路径；`ChannelRef` 本体零改动。
- **R-profileCtx（测试隔离）**: `ProfileContext` 是静态 ThreadLocal，`NotifyToolsTest` 必须 `@AfterEach clear()`（17 节 `AgentServiceTest` 同款纪律），防串号。
- **R-P3C/静态门禁**: record 紧凑构造器防御拷贝（SpotBugs EI_EXPOSE_REP，`ChannelRef` 先例）；测试方法名英文 + `@DisplayName` 中文；无 `System.out`（日志走 SLF4J）。
- **R-boot（boot 上下文冒烟）**: 新增 `@Component` 后 16 节 `OryxOsApplicationTests` 上下文起不来即回归（`NotifyConfiguration` 的 RestClient Bean + 两个 Component 装配链）--自动成为装配回归证据。

## Pre-Implement Verification Gate（H3）

逐任务执行前，凡涉及第三方 API 的任务先在本地依赖核实方法存在：Spring AI `spring-ai-model` 1.1.8 的 `org.springframework.ai.tool.annotation.Tool`/`ToolParam`（注解属性 `description`/`required`/`defaultValue`）；`spring-web` `RestClient`（`post()`/`uri()`/`contentType()`/`body()`/`retrieve()`/`toBodilessEntity()`）；MockWebServer（`start()`/`url()`/`takeRequest()`/`enqueue(MockResponse)`）与 `okhttp3.mockwebserver.MockResponse.Builder`（Boot 3.5 BOM 锁定的 okhttp 4.x 形态）--`mvn dependency:tree` 确认工件在锁；核实不到 -> 软门禁停下问用户。

## 关键设计决定（implement 期逐字执行的对齐锚）

1. **接口签名逐字保真**：`void send(NotifyTarget target, String content)`；`record NotifyTarget(String channelType, Map<String, String> config)`--课件「三」两段骨架的字面量。
2. **webhook body 逐字保真**：`restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(Map.of("content", content)).retrieve().toBodilessEntity()`（课件骨架逐行；`url` 取自 `target.config().get("url")`）。
3. **`notify` 工具签名**：`ToolResult notify(String content, @ToolParam(required = false) String channel)`--课件骨架 `notify(String content, String channel)` + 「二」第四"channel 是可选参数"；description 逐字"把一条消息推送到当前 Agent 配置好的通知渠道"。
4. **渠道解析语义**（spec R1/R6）：`ProfileContext.current()` 为 null 或 `notifyChannels()` 为空 -> 抛 `IllegalStateException("当前 Profile 未配置 notify_channels")`；channel 缺省 -> 取列表第一个；channel 非空 -> 取第一个 `type` 匹配项，未中抛 `IllegalStateException`；命中后 `ChannelRef` -> `NotifyTarget` 收窄转换（R-configNarrow）。
5. **Sandbox 调用位**：`adapter.send` 之前单行注释 `// Sandbox.enforce(...) 接线点（第 24 节交付）：出站 HTTP 与 http_post 同墙，校验先于发送`--与 17 节 `ToolExecutor` 调用位注释同构（H4 ①）。
6. **异常零捕获**：`NotifyTools.notify` 与 `WebhookNotifyAdapter.send` 均不 try-catch--课件 harness"异常向上抛、不静默吞掉"；上层 17 节 `ToolExecutor` 统一转 `ToolResult.failure` + 审计（异常不吞铁律）。
7. **包结构**：`com.oryxos.tool.notify`（接口/目标/webhook 实现/装配）+ `com.oryxos.tool.builtin`（`NotifyTools`，20 节 FileTools/ShellTools 等将同包汇入）；课件 `io.oryxos` 前缀按代码库 `com.oryxos` 既有约定适配（spec R1）。
8. **MockWebServer 断言口径**：`takeRequest()` 取 `getBody().readUtf8()` 解析 JSON 断言 `content`；URL 非硬编码用两个不同 path（或两个 server 实例）各自送达验证；5xx 用 `MockResponse.Builder().code(500)` 断言抛 `RestClientResponseException`。
