# Feature Specification: 第 19 节 Notify（通知推送模块）

**Feature Branch**: `019-lesson19-notify`

**Created**: 2026-08-27

**Status**: Draft

**Input**: 课件 `docs/class/第19节：Notify 模块 原理解析、实现与代码讲解.md`--本节是 OryxOS 的"出站推送"实现课（通知抽象接口 + 通用 webhook 实现 + `notify` 内置工具），来源实证以课件为准；技术方案对位 `TechnicalSolution` §6.8（通知推送）、§8.2（Profile 的 `notify_channels` 字段）、§6.7（Sandbox 复用关系）、§9.2（审计关系）。

**Source-of-truth note**: 延续 16/17/18 节裁定的逐节开发形态--**课件为唯一事实源**，实现形态逐字对齐课件骨架（`NotifyChannelAdapter.send(NotifyTarget, String)` 签名、`NotifyTarget = {channelType, config}`、webhook body `{"content": ...}`、harness 断言逻辑与方法名字面量如"发送前必须先过白名单校验"）；与课件示意代码的既有差异按前序节裁定继续沿用（包根 `com.oryxos` 非 `io.oryxos`；`ToolResult` 为 17 节已交付的 core 形态、`ProfileContext` 为静态 ThreadLocal 而非注入实例）。`notify_channels` 字段 16 节已在 `Profile`/`ProfileLoader` 交付，本节只消费不改动。

---

## Clarifications

### Session 2026-08-27（逐条采自课件「二、动手前先想清楚几件事」+「实现顺序说明」与技术方案 §6.8，无外部答案外推）

- **Q1: 为什么需要 Notify？已有 CLI/Web 不够吗？** -> A（课件「一」）: CLI 和 Web Service 都是"人推"：有人发起调用，响应沿同一条请求-响应链路同步返回。一旦触发源变成"到点自动"（定时查天气、每日汇总），这条链路就断了--没有人在另一端等着，Agent 必须自己决定把结果送到哪、怎么送。Notify 补的是与入站 Channel 对称的另一半："结果怎么主动送出去"。
- **Q2: 为什么先定接口、接口长什么样？** -> A（课件「二」第一）: 先抽一个不携带具体渠道细节的接口，表达"把一条内容送到某个通知目标"这个意图，签名里不出现"企业微信""飞书"这类某一档实现特有的词。核心阶段只在接口后面挂一档实现；以后加新渠道只新增实现类，不改接口、不改调用方。
- **Q3: 核心阶段为什么只做通用 webhook？** -> A（课件「二」第二）: 企业微信、飞书、钉钉的群机器人都提供 webhook 地址，一个通用实现就能覆盖大部分场景；逐家的签名算法、AccessToken 刷新这些认证细节留给扩展阶段（课件第六节盘点：给一个 URL 发一次 POST 就能送达的，核心阶段就能接；需要维护连接/签名/Token 刷新的，属扩展阶段）。
- **Q4: 安全校验怎么处理？** -> A（课件「二」第三）: `notify` 发出去的是一次 HTTP 请求，理应跟 `http_post` 一样过域名白名单，不能因为是"往外推"就绕过去。但白名单具体怎么校验是 23/24 节 Sandbox 的事--这里先按接口调用的方式接进去（enforce 先于 send 的顺序由 24 节补测钉死），不重复设计、不提前实现。
- **Q5: 推到哪里谁说了算？** -> A（课件「二」第四）: 配置在 Profile，不暴露在对话里。webhook 地址是运行时配置，不是模型需要知道的信息；LLM 调用时大多数情况只传 `content` 就够，`channel` 是可选参数，对应 Profile 的 `notify_channels` 字段。
- **Q6: 审计怎么走？** -> A（技术方案 §6.8「跟已有机制的关系」）: `notify` 跟其他 Tool 一样走 `ToolExecutor` 现有的成功/失败审计路径写入 `tool_invocations`，**不新增审计逻辑**；Sandbox 复用既有统一机制，不新增沙箱概念。
- **Q7: `NotifyTools` 的完成时点？** -> A（课件「实现顺序说明」）: 授课顺序 ≠ 构建顺序。`NotifyChannelAdapter`/`NotifyTarget`/`WebhookNotifyAdapter` 三样**本节立即实现、独立单测**；`NotifyTools` 的完整接线有三个依赖在后面的课--`@Tool` 注册机制（20 节 `AnnotatedToolAdapter`）、`Sandbox.enforce`（23/24 节）。20 节课件明文"NotifyTools（19 节）在此完成注册"：类本体归 19 节，注册动作归 20 节，Sandbox 接线与顺序断言归 24 节，27/28 节串联时做全量验证。
- **Q8: 渠道没配置会怎样？** -> A（课件「四」harness 第二批表格）: `notify_channels` 未配置 -> **明确报错**，不是静默失败--Agent 不会以为发出去了；`channel` 参数缺省 -> 取第一个渠道（大多数场景 LLM 只传 content 就够）。

### Resolve（在 implement 前需用户知悉，Step 5 停点对账）

- **R1（NotifyTools 本节交付形态）**: 类本体本节落地于 `com.oryxos.tool.builtin.NotifyTools`：`@Component` + Spring AI `@Tool` 注解方法 `notify(content, channel)`（20 节 `AnnotatedToolAdapter` 扫描注册，本节不注册进 `ToolRegistry`，18 节 `EmptyToolRegistry` 过渡 Bean 继续顶位）；渠道解析逻辑完整（未配置/未命中明确报错、缺省取第一个）；`Sandbox.enforce` 调用位按 H4 ① 留注释（24 节接线，与 17 节 `ToolExecutor` 的调用位预留同构）。课件示意代码的三处现实适配：包名 `io.oryxos` -> `com.oryxos`（代码库既有约定）；`ToolResult.success("已推送")` -> 17 节已交付的 `ToolResult.ok(...)` 工厂；构造注入 `ProfileContext` 实例 -> 静态 `ProfileContext.current()`（17 节交付形态，课件「实现顺序说明」明言"这里直接用"）。
- **R2（NotifyToolsTest 本节子集）**: harness 第二批三个守点中，"未配置明确报错"与"channel 缺省取第一个"不依赖 Sandbox，本节随 `NotifyTools` 交付即测（mock `NotifyChannelAdapter`）；"enforce 先于 send"（InOrder）依赖 20 节 Sandbox 前向接口 + 24 节接线，归 24 节补齐--本节在测试类 javadoc 与实现调用位双处注明。此拆解依据课件「实现顺序说明」的构建顺序裁定，属交付物清单范围内的时间裁剪，非范围变更。
- **R3（NotifyTarget 与 Profile.ChannelRef 的 config 类型差）**: 课件/技术方案 `NotifyTarget.config` 为 `Map<String, String>`，16 节已交付的 `Profile.ChannelRef.config` 为 `Map<String, Object>`（YAML 通用形态）。`NotifyTools` 从 `Profile.notifyChannels()` 解析时做 `Object.toString()` 收窄转换（null 值剔除），`ChannelRef` 与 `Profile` **零改动**（前序公共接口不因本节动）。
- **R4（WebhookNotifyAdapter 的 RestClient 供给）**: 课件骨架构造注入 `RestClient`（可测性：测试可注入定制实例）。Spring Boot 自动配置只提供 `RestClient.Builder` 不提供 `RestClient` Bean，故本节在 oryxos-tool 新增装配类提供 `RestClient` Bean（`RestClient.create()`，同步阻塞客户端，无连接池调优--核心阶段口径）。oryxos-tool pom 显式声明 `org.springframework:spring-web`（Boot BOM 管版本；spring 全家桶成员，非新第三方依赖）与 `spring-ai-model`（`@Tool`/`@ToolParam` 注解来源，core 已依赖、本模块显式化，18 节 R3 先例）。
- **R5（MockWebServer 测试依赖）**: harness 第一批点名"用 MockWebServer 在本地起一个假 webhook（不算外网依赖，仍是单测层）"。oryxos-tool test scope 新增 `com.squareup.okhttp3:mockwebserver`，属课件 harness 点名的新测试依赖（第 4 步 plan 明列）。注：Boot 3.5 BOM 已移除 okhttp3 版本管理，需显式锁 4.12.0（implement 期实测修正，见验收报告）。
- **R6（报错语义形态）**: "明确报错"以抛 `IllegalStateException`（含清晰中文 message）落地：沿 17 节 `ToolExecutor` 既有统一路径转 `ToolResult.failure` + `tool_invocations` 审计 `success=false`（异常不吞、模型可自纠），与 Q6"不新增审计逻辑"一致。webhook 5xx/4xx 同理：`WebhookNotifyAdapter.send` 不捕获 `RestClientResponseException`，上抛给调用方（课件 harness："异常向上抛、不静默吞掉"）。

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 通知出口：接口中立 + 通用 webhook 实现（Priority: P1）

每个业务 Agent 定义时不必再各自手写"调 http_post 打这个 webhook URL"或自配 IM MCP server--底座提供统一的出站推送出口：一个不携带渠道细节的通知接口 + 一个通用 webhook 实现。发送时 POST `{"content": "..."}` 到目标配置里的 URL；URL 来自配置而非硬编码；webhook 返回错误时异常上抛，绝不静默吞掉。接口签名经得起"接口中立性自查"（思维练习）：换成任何渠道的专用实现，签名一个字不用改。

**Why this priority**: 这是本节 harness 第一批（立即全绿）的全部承载，也是 25 节定时模块与 31 节日报 Demo 把结果"真正交出去"的地基。

**Independent Test**: `WebhookNotifyAdapterTest`（MockWebServer 起本地假 webhook，单测层）：发送后断言收到的 POST body 带 `content`、请求 URL 来自 `NotifyTarget.config` 而非硬编码；webhook 返回 5xx 时异常向上抛、不静默吞掉。

**Acceptance Scenarios**:

1. **Given** 一个 `NotifyTarget`（channelType=webhook、config 含 url），**When** `send(target, content)`，**Then** 该 URL 收到一次 POST，body 为 JSON 且含 `content` 字段。
2. **Given** 两个不同 url 的 `NotifyTarget`，**When** 分别 send，**Then** 各自的 URL 各收到请求--URL 由 Target 决定，不在实现里硬编码。
3. **Given** webhook 返回 5xx，**When** `send`，**Then** 异常向上抛出（调用方/上层审计可见），不静默吞掉。
4. **Given** 任何一档现有或未来的渠道实现，**When** 对照接口签名 `send(NotifyTarget, String)`，**Then** 签名不含任何渠道特有词汇（接口中立性）。

### User Story 2 - `notify` 内置工具：渠道解析 + 默认渠道 + 明确报错（Priority: P1）

Agent 在 ReAct 循环里自己决定要不要推送：大多数时候只传 `content`，底座从当前 Agent Profile 的 `notify_channels` 里解析目标--`channel` 缺省取第一个渠道；`notify_channels` 未配置或指定渠道不存在时**明确报错**（走 ToolExecutor 既有失败审计路径，Agent 不会误以为已发出）。安全校验调用位（enforce 先于 send）先按接口方式接进去，白名单细节 24 节接线、InOrder 顺序断言 24 节补。完整注册（模型可调用）20 节完成。

**Why this priority**: "推送目标配置在 Profile、不暴露在对话里"是本工具的安全设计核心；明确报错语义防止最阴险的静默失败。

**Independent Test**: `NotifyToolsTest`（本节子集，mock `NotifyChannelAdapter`）：`notify_channels` 未配置 -> 明确报错；`channel` 缺省 -> 取第一个渠道；推送内容经 adapter 原样送出。enforce 顺序断言 24 节补（依赖 Sandbox 前向接口）。

**Acceptance Scenarios**:

1. **Given** 当前 Profile 的 `notify_channels` 配了一个 webhook 渠道，**When** 模型只传 `content`（channel 缺省），**Then** 取第一个渠道为目标并推送成功。
2. **Given** 当前 Profile 的 `notify_channels` 配了多个渠道，**When** 模型传 `channel=第二个渠道`，**Then** 取对应渠道推送（未命中则明确报错）。
3. **Given** 当前 Profile 的 `notify_channels` 未配置（空），**When** 调 `notify`，**Then** 明确报错（失败审计留痕、Agent 收到 ERROR 回填），不是静默成功、不是空推送。
4. **Given** 一次 `notify` 调用，**When** 审视执行序列，**Then** 安全校验位在真正发送之前（调用位 19 节就位，白名单本体与 InOrder 断言 24 节接线钉死）。
5. **Given** webhook 发送抛异常（网络错/5xx），**When** `notify` 执行，**Then** 异常沿 ToolExecutor 既有路径转失败结果并落审计（异常不吞）。

## FR Requirements

### Must

- **FR1 出站通知抽象接口**: 提供"把一条内容送到某个通知目标"的中立接口；签名不出现任何具体渠道特有词汇；通知目标 = 渠道类型 + 渠道特定配置（键值对），渠道细节由实现类自行解释（课件「三」接口骨架逐字对齐）。
- **FR2 通用 webhook 实现（核心阶段唯一一档）**: POST JSON body（含 `content` 字段）到 `NotifyTarget.config` 的 `url`；URL 来自配置不硬编码；HTTP 错误响应（5xx 等）异常向上抛、不静默吞掉（课件 harness 第一批守点）。
- **FR3 `notify` 内置工具（渠道解析语义）**: `content` 必传、`channel` 可选；`channel` 缺省取 `notify_channels` 第一个渠道；`notify_channels` 未配置或指定 `channel` 未命中时**明确报错**，绝不静默失败或假装成功（课件 harness 第二批守点 1/2）。
- **FR4 推送目标配置在 Profile**: 消费 16 节已交付的 `notify_channels` 字段（type + 渠道特定配置如 url）；webhook 地址等凭证走环境变量占位（如 `${TEAM_WEBHOOK_URL}`），不落明文、不进对话。
- **FR5 安全校验调用位先行**: `notify` 的执行路径上预留 Sandbox 校验调用位（enforce 先于 send 的位置语义），白名单校验本体与顺序断言由 24 节接线（课件「二」第三 + harness 第二批守点 3 的时间裁定）。
- **FR6 审计零新增**: `notify` 作为 Tool 走 `ToolExecutor` 既有成功/失败审计路径写入 `tool_invocations`，不新增审计逻辑、不新增审计表（技术方案 §6.8）。

### 明确不做（边界）

- **不逐家接专用 API**：企业微信/飞书/钉钉的签名算法、AccessToken 刷新等认证细节，扩展阶段按 `channelType` 新增专用 Adapter（课件「二」第二 + 6.4 路一）。
- **不接非 HTTP 渠道**：邮件（SMTP）、短信（云厂商签名 API）等需要维护连接/凭证的渠道，扩展阶段专用 Adapter（课件 6.1 判断标准）。
- **不实现 Sandbox 本体**：白名单校验机制、`Sandbox`/`SandboxAction`/`ActionType` 类型体系归 23/24 节；本节只留调用位（课件「二」第三）。
- **不做 `@Tool` 注册管道**：`AnnotatedToolAdapter` 扫描注册、schema 生成归 20 节；本节 `NotifyTools` 为带注解的 Spring Bean，但不进 `ToolRegistry`（课件「实现顺序说明」+ 20 节交付物）。
- **不新增审计逻辑/表**：复用 `ToolExecutor` + `tool_invocations` 既有路径（技术方案 §6.8）。
- **不做富文本/卡片等结构化推送**：复杂渠道格式走 MCP 方式二（课件 6.4 路二），`notify` 只统一纯文本推送。
- **不改前序节公共接口**：`Profile.ChannelRef`/`ProfileContext`/`ToolResult`/`OryxTool` 等零改动（本节纯消费方）。

## Success Criteria

可自动化部分由课件"验收 harness"测试套件承载，`mvn test` 全绿即通过；关键回归点：

- `WebhookNotifyAdapterTest`：收到的 POST body 带 `content`；URL 来自 `NotifyTarget.config` 非硬编码；5xx 异常上抛不吞。
- `NotifyToolsTest`（本节子集）：`notify_channels` 未配置明确报错；`channel` 缺省取第一个渠道。
- 前序节全部测试回归绿（16/17/18 节跨节契约证据）。

人工项见课件"五、做完怎么验"：

- `notify` 真把消息送到配置好的**真实** webhook（群里收到）--假 webhook 测协议、真 webhook 验配置（20 节注册后可验）。
- 接口中立性自查（思维练习）：换成企业微信官方 SDK 实现，`NotifyChannelAdapter.send(NotifyTarget, String)` 签名不需要改。

## 依赖与假设

- **前序节交付物（H0 已核对存在）**：16 节 `Profile`（含 `notifyChannels` 字段）/`ProfileLoader`（已解析 `notify_channels` YAML 键）；17 节 `ProfileContext`（静态 ThreadLocal）/`ToolResult`/`ToolExecutor`（异常->失败结果统一路径）/`ToolInvocationAudit`；18 节 `EngineConfiguration`（`EmptyToolRegistry` 过渡 Bean 继续顶位，20 节替换）。
- **后续节依赖（本节预留、不在本节交付）**：20 节 `AnnotatedToolAdapter`（`@Tool` 注册）、Sandbox 前向接口五件；24 节 `WhitelistSandbox` 本体 + enforce 顺序断言；25 节定时模块、31 节 Demo 才是 notify 的真正消费场景。
- **外部依赖**：Spring Boot BOM（spring-web、mockwebserver 版本管理）；Spring AI BOM 1.1.8（`@Tool`/`@ToolParam` 注解，core 已依赖 spring-ai-model）。无网络依赖（MockWebServer 为本地回环假 webhook）。
