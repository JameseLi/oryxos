# Implementation Plan: 第 17 节 ReAct 循环（想-做-看引擎 + ProfileContext + tool_invocations 审计）

**Branch**: `017-lesson17-react` | **Date**: 2026-08-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/017-lesson17-react/spec.md`；课件 `docs/class/第17节：ReAct 原理解析、实现与代码讲解.md`；技术方案对位 §4/§8.3/§9.2。

## Summary

打通 OryxOS 第二块核心能力--**自实现 ReAct 循环**（宪法原则三的落地）：接到消息 -> 组装 Prompt -> 经 Provider 调一次 LLM -> 响应无工具调用则收尾、有则经自家 ToolExecutor 执行并回填继续循环，最大轮数兜底防死循环。环绕主循环交付：`PromptBuilder`（四部分组装 + 历史截断 + 日期时间注入）、`ToolExecutor`（执行权唯一收口 + 成败双路审计）、`AgentService`（三源统一入口 + ProfileContext ThreadLocal 卫生）、`ContextLoader`（Bootstrap/Skill 每次重读不缓存）、`tool_invocations` 表（与 `llm_calls` 同口径，手工建表脚本）。形态**逐字对齐课件**（含"达到最大轮数，已停止"等课件字面量与两个关键回归测试的断言逻辑）。

## Technical Context

**固定技术栈句**：JDK 21 + Spring Boot 3.x + Spring AI Alibaba（动手前先跑 `mvn dependency:tree` 确认锁定 BOM 里目标依赖存在）、SQLite + Spring Data JPA。凭证走环境变量占位，不落明文。SQLite 用手工建表脚本，不依赖 `hibernate.ddl-auto=update`。

- **Language/Version**: Java 21（本节纯单线程同步逻辑，不用并发原语；ProfileContext 依赖虚拟线程每请求独立，但代码本身只是 ThreadLocal 存取）。
- **Primary Dependencies**:
  - Spring Boot **3.5.16**、Spring AI BOM **1.1.8**（`spring-ai-model` 已是 core 既有依赖：`Message`/`UserMessage`/`SystemMessage`/`AssistantMessage`/`ToolResponseMessage`/`ChatResponse`/`Generation`）。**本节不新增任何第三方依赖**。
  - SQLite：`sqlite-jdbc` + `hibernate-community-dialects`（storage 既有）。
- **Storage**: SQLite（`.oryxos/oryxos.db`）；`tool_invocations` 追加进手工脚本 `scripts/sql/001-init.sql`（`CREATE TABLE IF NOT EXISTS` 幂等，boot `sql.init.mode=always` 自动执行）；`ddl-auto=none`；仓储测试经 test yaml 的 `spring.sql.init` 执行脚本建表。
- **Testing**: JUnit 5 + Mockito + AssertJ（`spring-boot-starter-test` 既有）。LLM 调用端口 / 审计端口 / 会话管理器全 mock；文件系统用 `@TempDir`；仓储测试走真 SQLite。**本节无集成冒烟**（课件明言"全部不碰网络"），harness 全是单测、秒级跑完。
- **Target Platform**: 企业内网单实例；本节类为纯 POJO（不注册 Spring Bean，见 R5），被后续节（18 CLI / 20 Tool / 22 Memory / 25 Scheduler）装配消费。
- **语法禁区句**：避开 P3C/ASM 解析不了的 Java 18+ 语法形态（如增强 switch 的 `default ->` 写法），静态检查是构建门禁。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design. 原则来源 `.specify/memory/constitution.md`。*

| # | 原则 | 判定 | 证据/说明 |
|---|------|------|-----------|
| I | 固定技术栈与九模块结构 | ✅ | 只在既有 9 模块内加类（core 14 个新文件 + storage 3 个 + provider 1 行 implements + DDL 追加），不增删模块、不加依赖；JDK 21 + Boot 3.x。 |
| II | 核心阶段只做运行时内核 | ✅ | 本节交付 ReAct 循环 + Prompt 组装 + 工具执行收口 + 统一入口 + 上下文加载 + `tool_invocations` 落库；**不做**工具并行/Agent 间委托/流式/上下文压缩（课件「有几样先别做」逐项照搬，见下节）。 |
| III | 自实现 ReAct 循环（NON-NEGOTIABLE） | ✅ | 本节即此原则的落地：主循环约数十行自己写，**不引 Spring AI 的 Agent/ChatClient 循环抽象**；循环只做调度，拼 prompt/调模型/执行工具全部外派专门模块。 |
| IV | Spring AI 只用一半（NON-NEGOTIABLE） | ✅ | 消息/Prompt/ChatResponse 沿用 Spring AI 抽象（协议转换层）；**工具执行只经 `ToolExecutor` 一条路**，无 Spring AI 自动执行路径（16 节已 `internalToolExecutionEnabled(false)`，本节不触碰该开关、不引 ChatClient 自动执行）。 |
| V | 三档接入·零代码优先 | ✅ N/A | 本节不交付 Tool 体系（§20）；仅立 `ToolRegistry` 接口与 `OryxTool.execute` 抽象（§6.1 终局四方法形态的最小前进），不内置任何工具。 |
| VI | 审计数据 Day One 落库（NON-NEGOTIABLE） | ✅ | `tool_invocations` 本节建表+写实体+Repository+JPA 审计实现，成功 `success=true`、失败 `success=false`+`error_message`，与 `llm_calls` 同口径（LLM 调用审计由 16 节 `ProviderService` 内已落地，本节循环带 sessionId 串起两表）。 |
| VII | 每个 User Story 必须可演示 | ✅ | harness 全绿即四个 US 的可执行证据；人工项=Demo 一对话版用真模型跑通一次多轮工具调用（见验收报告）。 |

## 明确不做（边界，逐项照搬课件「有几样先别做」）

- **工具并行调用**--一次响应里多个工具调用按顺序逐个执行。
- **Agent 之间互相委托**。
- **流式输出**。
- **上下文压缩**--先用"只留最近 N 轮"的简单截断顶着，够用就行；压缩留扩展阶段。

另（16 节已定，本节延续不做）：fallback/hedge/熔断、成本看板、认证限流。

## Project Structure（模块落位 + 文件级清单）

模块落位（skill §17 行）：`ReActLoop`/`PromptBuilder`/`ToolExecutor`/`AgentService`/`ProfileContext`/`ContextLoader` -> oryxos-core；`ToolInvocation` + `ToolInvocationRepository` -> oryxos-storage。

| 组件 | 模块/包 | 说明 |
|---|---|---|
| `ChatInvoker`（LLM 调用端口，spec R2） | `com.oryxos.core.chat` | `ChatResponse chat(String sessionId, Profile profile, Prompt prompt)`--签名与 16 节 `ProviderService.chat` 逐字同构；core 立端口破 provider->core 依赖方向 |
| `Session`（会话域类型）+ `SessionManager`（最小接口 `save`）（spec R1） | `com.oryxos.core.session` | Session：id/profileName/消息累积（`append(userMessage)`/`append(ChatResponse)`/`appendToolResult(call, result)`）；18 节在其上加 session_id 公式与 `sessions` 表 |
| `ToolResult`（成功标识+内容+错误信息）+ `ToolRegistry`（接口：按名查找+全量）（spec R1） | `com.oryxos.core.tool` | `OryxTool` 增补 `ToolResult execute(String input)`（spec R3，§6.1 终局四方法的前进）；20 节补"是否可重试"与实现 |
| `ToolExecutor` | `com.oryxos.core.tool` | 执行权唯一收口：注册表查找 -> 沙箱调用位（注释留 24 节接线）-> 执行 -> 成败双路审计 |
| `ToolInvocationAudit`（审计端口，spec R1） | `com.oryxos.core.audit` | 与 `LlmCallAudit` 同构 DIP；`record(sessionId, toolName, arguments, result, elapsedMillis, success, errorMessage)` |
| `MemoryService`（最小接口 `String buildContext(Session)`，spec R1） | `com.oryxos.core.memory` | 签名对齐 22 节课件门面；未装配=第二部分整体缺席 |
| `ReActLoop` | `com.oryxos.core.react` | 主循环：调度/停止/累积；`MAX_ITERATIONS` 默认 10（Profile settings 可覆盖）；达到上限返回含"达到最大轮数"的收尾语 |
| `PromptBuilder` | `com.oryxos.core.react` | 四部分组装 + 截断（默认 20，Profile 可覆盖）+ 日期时间（注入 `Clock` 可测）；工具按 Profile.tools 过滤 |
| `AgentService` + `ProfileContext` + `ContextLoader` | `com.oryxos.core.agent` | 编排者（取 Profile -> set ThreadLocal -> run -> save -> finally clear）；上下文供给者（每次重读、Skill 缺失报错、Bootstrap 缺失 WARN） |
| `ToolInvocationEntity` + `ToolInvocationRepository` + `JpaToolInvocationAudit` | `com.oryxos.storage` | `tool_invocations` 表三件套，与 `LlmCall` 链同构 |
| `001-init.sql` 追加 `tool_invocations` 表 + 索引 | oryxos-storage resources | `CREATE TABLE IF NOT EXISTS` 幂等；boot sql.init 自动执行 |
| `ProviderService implements ChatInvoker` | oryxos-provider | 对 16 节唯一触碰点：仅追加 implements（既有 chat 方法签名即端口签名），不改任何既有字面量 |

**装配纪律（spec R5）**：本节 core 交付类一律**不加 Spring 注解**（`ToolRegistry`/`SessionManager`/`MemoryService` 的实现要到 18/20/22 节才存在，现在注册会拖垮 boot 上下文冒烟）；唯一 Spring 触点是 storage 的 `JpaToolInvocationAudit`（`@Component`，依赖 Repository bean，boot 可解析）。

## Testing strategy（按课件「验收 harness」执行）

测试策略句：测试策略按课件「验收 harness」执行：`ReActLoopTest`、`PromptBuilderTest`、`ToolExecutorTest`、`AgentServiceTest`、`ContextLoaderTest`（覆盖关键回归点：**转满最大轮数强制停**、**处理抛异常时 finally 清 ProfileContext**、**无缓存重读**、**四部分顺序**、**历史超 N 轮截断**、**成败双路审计**），单测默认跑（本节无集成冒烟--课件明言全部不碰网络）；实现完成的定义是 `mvn clean verify` 全绿。

**harness 映射表**（测试类 ↔ 覆盖点 ↔ 模块）：

| 测试类 | 覆盖的验收点 | 模块 | 跑法 |
|---|---|---|---|
| `ReActLoopTest` | 无工具调用一轮收尾；有工具调用则执行并回填进下一轮；**转满最大轮数强制停**（恰好 N 轮）；每轮响应和工具结果都累积进 Session | core | 单测默认跑 |
| `PromptBuilderTest` | 四部分顺序正确；历史超 N 轮被截断；system prompt 末尾含当前日期时间 | core | 单测默认跑 |
| `ToolExecutorTest` | 成功写审计 `success=true`；失败也写 `success=false` 带原因，异常不吞 | core | 单测默认跑 |
| `AgentServiceTest` | 处理期间 `ProfileContext` 可取到当前 Profile；**处理抛异常时 finally 也把它清掉**；结束后 Session 被持久化 | core | 单测默认跑 |
| `ContextLoaderTest` | 改文件后下一次 build 立即读到新内容（无缓存回归）；Skill 引用缺失报错、Bootstrap 缺失 WARN | core | 单测默认跑 |
| `ToolInvocationRepositoryTest`（spec R6，同口径） | 手工脚本建出的 `tool_invocations` 能存能读，`success`/`error_message` 两列真实存在；含 `JpaToolInvocationAudit` 成败两路落库 | storage | 单测默认跑（真 SQLite） |

**两个最值钱的回归测试（课件写出代码，断言逻辑逐条保真落地，方法名译英文 + `@DisplayName` 承载课件原文）**：

1. `modelKeepsRequestingTools_stopsAtMaxIterations`（@DisplayName「模型一直要调工具_转满最大轮数强制停」）-- `when(chatInvoker.chat(any(), any(), any())).thenReturn(responseWithToolCall(httpGetCall))` 每轮都带工具调用；`profileWithMaxIterations(10)`；断言 `verify(chatInvoker, times(10)).chat(any(), any(), any())` 且 `assertTrue(reply.contains("达到最大轮数"))`。（坑一回归）
2. `exceptionDuringProcess_profileContextStillCleared`（@DisplayName「处理中抛异常_ProfileContext也必须被清掉」）-- `when(reActLoop.run(any(), any(), any())).thenThrow(new RuntimeException("boom"))`；断言 `assertThrows(RuntimeException.class, () -> agentService.process(session, "hi"))` 且 `assertNull(ProfileContext.current())`。（ThreadLocal 泄漏回归--单请求测试永不报错、只在并发复用时串号，必须显式钉死）

## Risks & Mitigations

- **R1/R2/R3（spec 记录）**: 最小类型集 + LLM 调用端口 + `OryxTool.execute` 增补 -> Step 5 固定停点与用户对账后才 implement；`ProviderService implements ChatInvoker` 为向后兼容扩展（16 节 spec R1 预告的 §17 改造点）。
- **R4（spec 记录）**: 截断语义（轮=消息条数）与 Bootstrap/Skill 缺失读法 -> 停点确认；孤儿 tool 消息风险已记 Edge Cases（核心阶段接受，压缩属扩展）。
- **R5（spec 记录）**: 本节不注册 Spring Bean -> boot 上下文冒烟保持绿；装配在 18/20/22 节接线时补。
- **R6（spec 记录）**: `ToolInvocationRepositoryTest` 为 harness 表外第 6 个测试类 -> 依据课件"口径跟上一节 llm_calls 一致"（16 节口径含仓储测试）。
- **R-API（H3）**: Spring AI 1.1.8 真实 API vs 课件示意骨架（`Response resp`/`resp.hasToolCalls()`/`resp.toolCalls()`/`resp.text()`）-> 已 javap 核实映射：`ChatResponse.getResult().getOutput()`（`AssistantMessage`）、`hasToolCalls()`/`getToolCalls()`、`getText()`（经 `AbstractMessage`）、`AssistantMessage$ToolCall(id/type/name/arguments)`、`ToolResponseMessage$ToolResponse(id/name/responseText)`+Builder、`ChatResponse(List<Generation>)`/`Generation(AssistantMessage)` 公开构造器（测试可直接构造）、`AssistantMessage.builder().content().toolCalls().build()`。implement 期若再遇新 API 一律先核再用，核实不到 -> 软门禁停下。
- **R-loop-shape**: 课件骨架的 `session.append(resp)`/`appendToolResult(result)` 为示意签名 -> 落地为 `append(ChatResponse)`（内部取 `getResult().getOutput()`）与 `appendToolResult(ToolCall, ToolResult)`（需 call 的 id/name 配对构造 ToolResponseMessage；ToolResult 按 §6.1 终局不含 id/name）。
- **R-P3C/静态门禁**: 新类注释只写"为什么"；record 防御拷贝防 EI_EXPOSE_REP2；测试方法名英文。

## Pre-Implement Verification Gate（H3）

逐任务执行前，凡涉及第三方 API 的任务先在本地依赖核实方法存在（本节核心第三方面仅 Spring AI 1.1.8 抽象层，已全部预核见上）；`mvn dependency:tree` 确认 core 既有 `spring-ai-model` 无版本漂移。核实不到 -> 软门禁停下问用户。

## 关键设计决定（implement 期逐字执行的对齐锚）

1. **课件字面量保真**：最大轮数收尾语返回含 **"达到最大轮数"**（课件原句"达到最大轮数，已停止"整体作为返回常量）；两个关键回归测试的断言逻辑逐条保真（`times(10)`、`contains("达到最大轮数")`、`assertNull(ProfileContext.current())`）。
2. **默认值**：`MAX_ITERATIONS=10`、历史截断 `20`--Profile `settings.maxIterations/maxHistoryTurns` 为 null 或未配 settings 时取默认；配了 <=0 按非法值报错（Edge Case）。
3. **日期时间格式**：system prompt 末尾追加 `当前时间：yyyy-MM-dd HH:mm:ss`（`Clock` 注入，默认 `systemDefaultZone()`；定时场景的"今天"全靠这一行）。
4. **system prompt 组成**：`identity`（agentName + prompt，未配置则跳过该段）+ `ContextLoader.load(profile)`（Bootstrap+Skill 文本）+ 末行日期时间；长期记忆为**独立的第二条 SystemMessage**（四部分顺序可测：system、[memory]、history 依次）。
5. **工具失败语义**：执行异常/未知工具 -> 捕获转失败 `ToolResult`（`success=false`+原因）+ 审计 `success=false` 带 `error_message` + 失败文本（`"ERROR: " + 原因`）回填模型继续对话；LLM 调用异常不吞、上抛（16 节已审计）。
6. **沙箱调用位**：`ToolExecutor` 在注册表查找之后、`tool.execute` 之前留显式注释锚点（"Sandbox.enforce 接线点，24 节交付"），本节无代码。
