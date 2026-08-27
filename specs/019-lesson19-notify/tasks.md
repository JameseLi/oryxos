---

description: "Task list for 第 19 节 Notify（通知推送模块：notify 包三件套 + NotifyTools 类本体）"
---

# Tasks: 第 19 节 Notify

**Input**: Design documents from `/specs/019-lesson19-notify/`（spec.md + plan.md）；课件 `docs/class/第19节：Notify 模块 原理解析、实现与代码讲解.md`。

**Prerequisites**: plan.md（required）、spec.md（required）。前序依赖：**第 16/17/18 节全部交付物**（H0 已存在性核对：`Profile.notifyChannels`/`ProfileLoader`/`ProfileContext`/`ToolResult`/`ToolExecutor`/`EngineConfiguration`（EmptyToolRegistry 顶位）均就位）；基线 `mvn clean verify` 全绿（18 节收尾态，2026-08-27 实测）。

**Tests**: harness 先行/伴随--测试任务与对应实现任务成对出现；`mvn clean verify` 全绿为实现完成定义；本节无 `@Tag("integration")` 项（真 webhook 验配置属人工项，20 节注册后可验；课件明言 MockWebServer"不算外网依赖，仍是单测层"）。

**Organization**: 按 spec 依赖序组织（pom 依赖 -> notify 包三件套 -> NotifyTools -> 装配）；包根 `com.oryxos`，标识符一律英文 ASCII（宪法 §9）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 不同文件、无未完成依赖，可并行
- **[Story]**: US1（通知出口：接口+webhook 实现）/ US2（notify 内置工具）/ 共享底座
- 路径相对仓库根；包根：`com.oryxos`

## Path Conventions

- Maven 多模块：`oryxos-tool/src/main/java/com/oryxos/tool/notify|builtin/...`
- 测试：`oryxos-tool/src/test/java/com/oryxos/tool/notify|builtin/...`
- **装配纪律（延续 18 节）**: 前序节交付类零改动零注解；本节新 Spring 触点仅 oryxos-tool 内 `@Component`/`@Configuration`（boot `scanBasePackages` 已含 `com.oryxos.tool`）
- **本节零触碰**: core/storage 模块全部文件、`001-init.sql`、`EngineConfiguration`（`EmptyToolRegistry` 顶位不变，20 节替换）

---

## Phase 0: 前置核对（已完成）

- [x] T-R0 H0 依赖检查：16/17/18 节交付物逐项存在性核对通过；分支 `019-lesson19-notify` 就位（自 master 切出，18 节已合入）；`mvn clean verify` 基线绿（reactor：parent + core/provider/memory/tool/channel-cli/web/storage/cli/boot 9 子模块全 SUCCESS，2026-08-27 实测）；`Profile.notifyChannels` 字段与 `ProfileLoader` 的 `notify_channels` 解析 16 节已交付（本节纯消费）。

## Phase 1: 依赖声明（共享底座）

**Purpose**: oryxos-tool 补齐本节编译/测试所需依赖（全部 BOM 内工件，不写版本）。

- [x] T-D1 oryxos-tool pom：main scope 增 `org.springframework:spring-web`（`RestClient`）与 `org.springframework.ai:spring-ai-model`（`@Tool`/`@ToolParam`，显式化 core 传递依赖，spec R4）；test scope 增 `com.squareup.okhttp3:mockwebserver`（spec R5，课件 harness 点名）。加完跑 `mvn -pl oryxos-tool dependency:tree` 核对三工件在锁且版本由 BOM 管理（H3）。

## Phase 2: notify 包三件套（US1 主体，harness 先行/伴随）

**Purpose**: 中立接口 + 通知目标 + 通用 webhook 实现（课件「三」骨架逐字对齐）。

- [x] T-N1 [P] `NotifyTarget`（`com.oryxos.tool.notify`）：`record NotifyTarget(String channelType, Map<String, String> config)`，紧凑构造器 null 防御 + 防御拷贝（口径照抄 `Profile.ChannelRef`）；javadoc：渠道细节由实现类自行解释，接口签名不出现任何一档实现特有的词。
- [x] T-N2 [P] `NotifyChannelAdapter` 接口（`com.oryxos.tool.notify`）：`void send(NotifyTarget target, String content)`--课件骨架逐字保真；javadoc 注明"接口表达意图，不表达实现；加新渠道只新增实现类，不改接口、不改调用方"（课件「二」第一）与"入站 ChannelAdapter 与出站 NotifyChannelAdapter 方向相反、分开建模"（§6.8）。
- [x] T-N3 `WebhookNotifyAdapter`（`com.oryxos.tool.notify`，`@Component implements NotifyChannelAdapter`）：构造注入 `RestClient`；`send` 读 `target.config().get("url")`（缺失抛 `IllegalStateException` 明确报错）-> `restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(Map.of("content", content)).retrieve().toBodilessEntity()`（课件骨架逐行保真）；**不捕获** `RestClientResponseException`（5xx 异常上抛不吞，课件 harness 守点 3）。
- [x] T-N3T `WebhookNotifyAdapterTest`（`com.oryxos.tool.notify`，MockWebServer 本地回环 + 真 `RestClient.create()`）：①发送后收到的 POST body 里带 `content`（`takeRequest().getBody().readUtf8()` 解析 JSON 断言）；②URL 来自 `NotifyTarget.config` 而不是硬编码（两个不同 path 的 Target 各自送达，或换 path 重发验证跟随）；③webhook 返回 5xx 时异常向上抛、不静默吞掉（`MockResponse` code 500，assertThatThrownBy `RestClientResponseException`）。harness 先行/伴随。

## Phase 3: NotifyTools（US2 主体，harness 先行/伴随）

**Purpose**: `notify` 内置工具类本体（渠道解析 + 缺省取第一 + 明确报错）；注册归 20 节、Sandbox 调用位注释预留归 24 节接线。

- [x] T-B1 `NotifyTools`（`com.oryxos.tool.builtin`，`@Component`，构造注入 `NotifyChannelAdapter`）：`@Tool(description = "把一条消息推送到当前 Agent 配置好的通知渠道") public ToolResult notify(String content, @ToolParam(required = false, description = "通知渠道类型，缺省用 Profile 配置的第一个渠道") String channel)`；执行序：`ProfileContext.current()`（null 抛 IllegalStateException）-> 解析 `notifyChannels()`（空列表抛 IllegalStateException"未配置 notify_channels"；channel 缺省取第一个；channel 非空取第一个 type 匹配项，未中抛 IllegalStateException）-> `ChannelRef` 收窄转 `NotifyTarget`（`String.valueOf` + null 剔除，spec R3）-> **Sandbox.enforce 调用位注释（24 节接线）** -> `adapter.send(target, content)` -> `ToolResult.ok("已推送")`；全方法零 try-catch（异常上抛给 17 节 ToolExecutor 统一转失败+审计，spec R6）；javadoc 注明"20 节 AnnotatedToolAdapter 注册、24 节 Sandbox 接线、InOrder 顺序断言 24 节补"（课件「实现顺序说明」）。
- [x] T-B1T `NotifyToolsTest`（`com.oryxos.tool.builtin`，Mockito mock `NotifyChannelAdapter`；`ProfileContext` set/clear + `@AfterEach clear()` 防串号）：①`notify_channels` 未配置 -> 明确报错（assertThatThrownBy IllegalStateException，非静默--Agent 不会以为发出去了）；②`channel` 参数缺省 -> 取第一个渠道（verify adapter.send 参数为列表第一个渠道的 NotifyTarget）；③推送内容经 adapter 原样送出（verify send 第二参 eq(content)）；④指定 `channel` 未命中 -> 明确报错；⑤多渠道配置 + 指定 channel 命中第一个匹配项。javadoc 注明 enforce InOrder 断言（课件「发送前必须先过白名单校验」）归 24 节补齐。harness 先行/伴随。

## Phase 4: 装配与收尾

- [x] T-A1 `NotifyConfiguration`（`com.oryxos.tool.notify`，`@Configuration`）：`@Bean RestClient restClient() { return RestClient.create(); }`（spec R4：Boot 自动配置只供 Builder 不供 RestClient Bean；同步阻塞客户端，核心阶段口径）；javadoc 注明 20 节 Tool 体系装配需要时再演进。
- [x] T-A2 全量门禁：`mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD/Spotless；16 节 `OryxOsApplicationTests` boot 上下文冒烟自动成为新增 Bean 的装配回归）；前序节测试回归绿；H4 六条不变量自查；输出节级验收报告（六项证据 DoD + 变更总结三段结构）。

---

## 课件「本节交付物」对账表（Step 5 停点核对用）

| 课件交付物 | 本任务清单落点 | 时点 |
|---|---|---|
| 代码：`NotifyChannelAdapter` 接口 | T-N2 | 本节 ✅ |
| 代码：`NotifyTarget` | T-N1 | 本节 ✅ |
| 代码：`WebhookNotifyAdapter` | T-N3 | 本节 ✅ |
| 代码：`NotifyTools`（完整接线依赖 20/24 节） | T-B1（类本体+渠道解析；注册 20 节、Sandbox 接线 24 节） | 本节交付本体 ✅ / 完整接线后续节 |
| 测试：`WebhookNotifyAdapterTest` | T-N3T | 本节全绿 ✅ |
| 测试：`NotifyToolsTest`（第二批依赖 20/24 节） | T-B1T（未配置报错/缺省取第一/内容原样/未命中报错；enforce InOrder 24 节补） | 本节子集 ✅ / 顺序断言 24 节 |
| 配置：Profile 新增 `notify_channels` 字段 | 无任务--16 节已交付（`Profile.notifyChannels` + `ProfileLoader` 解析），本节纯消费 | 已就位 ✅ |
