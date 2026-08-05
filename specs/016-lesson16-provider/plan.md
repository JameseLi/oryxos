# Implementation Plan: 第 16 节 Agent Provider（对接 LLM 的薄适配层 + Profile 解析）

**Branch**: `016-lesson16-provider` | **Date**: 2026-08-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/016-lesson16-provider/spec.md`；课件 `docs/class/第16节：Agent Provider 原理解析、实现与代码讲解.md`。

## Summary

打通 OryxOS 第一块核心能力——**Provider 薄适配层**：按 `Profile` 里写的 provider name 在显式映射表里取对应大模型，把工具 schema 翻译成模型看得懂的格式、**关闭自动执行**后发给模型、把回话原样返回；调用**成功与失败都落 `llm_calls` 审计表**。同时交付 Profile 解析加载（`.oryxos/profiles/` YAML → 内存索引），本节是整个系统第一个消费 Profile 的模块。形态**逐字对齐课件**（经用户裁定推翻仓库此前合并实现采用的「Profile 由 AGENT.md frontmatter 派生」形态，本特性以课件为唯一事实源）。

## Technical Context

**固定技术栈句**：JDK 21 + Spring Boot 3.x + Spring AI Alibaba（动手前先跑 `mvn dependency:tree` 确认锁定 BOM 里目标依赖存在）、SQLite + Spring Data JPA。凭证走环境变量占位，不落明文。SQLite 用手工建表脚本，不依赖 `hibernate.ddl-auto=update`。

- **Language/Version**: Java 21（virtual thread 配置已就位，本节不主动用并发原语）。
- **Primary Dependencies**:
  - Spring Boot **3.5.16**、Spring AI BOM **1.1.8**（`spring-ai-model` 抽象层：`ChatModel`/`Prompt`/`Message`/`ToolCallingChatOptions`/`ToolCallback`/`ToolDefinition`/`ToolMetadata`；connector：`spring-ai-deepseek`/`spring-ai-openai`；不引 `*-starter`/autoconfigure，ChatModel 由本节程序化构造显式映射）。
  - Spring AI Alibaba BOM **1.1.2.3**（`spring-ai-alibaba-dashscope`）。
  - SQLite：`sqlite-jdbc` 3.49.1.0 + `hibernate-community-dialects`（对齐 Boot 管理的 Hibernate 6.6.x）。
  - YAML 解析：SnakeYAML（Spring Boot 自带）。
- **Storage**: SQLite（`.oryxos/oryxos.db`）；`llm_calls` 由手工脚本 `scripts/sql/001-init.sql` 建表，`ddl-auto=none`；仓储测试**执行该脚本**建表，不靠 Hibernate 自动建。
- **Testing**: JUnit 5 + Mockito + Spring Boot Test。mock `ChatModel` 测路由/审计/不开自动执行；`LlmCallRepositoryTest` 走真 SQLite 跑脚本建表后存读；`ProviderSmokeIT` 打 `@Tag("integration")` 真调一次模型。
- **Target Platform**: 企业内网 Linux/macOS，单 fat JAR；本节无独立入口，被后续节调用。
- **Constraints**: 单条消息上限 32KB（后续节消费，本节不守）；核心阶段无认证（内网）；非流式。
- **语法禁区句**：避开 P3C/ASM 解析不了的 Java 18+ 语法形态（如增强 switch 的 `default ->` 写法），静态检查是构建门禁。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design. 原则来源 `.specify/memory/constitution.md`。*

| # | 原则 | 判定 | 证据/说明 |
|---|------|------|-----------|
| I | 固定技术栈与九模块结构 | ✅ | 只在既有 9 模块内加类，不增删模块；JDK 21 + Boot 3.x；不引 GraalVM/WebFlux/响应式；不动模块边界。core 已依赖 `spring-ai-model`（仅抽象层，pom 既定）。 |
| II | 核心阶段只做运行时内核 | ✅ | 本节只做 Provider 薄适配层 + Profile 解析 + `llm_calls` 审计落库；**不做** ReAct 循环、Tool 执行、Memory、Web、SSE/WebSocket/认证/限流/熔断/fallback/hedge（见「明确不做」草案）。 |
| III | 自实现 ReAct 循环 | ✅ N/A | 本节不实现 ReAct 循环（§17），不引 Spring AI Agent 抽象； Provider 只做一次调用。 |
| IV | Spring AI 只用一半（NON-NEGOTIABLE） | ✅ | 用 `ChatModel`/`Prompt`/`Message`/`ToolCallingChatOptions`/`ToolCallback`/`ToolDefinition`（协议转换+schema 生成）；**禁用自动 tool 执行**——请求构建时 `internalToolExecutionEnabled(false)`，并以直接 `ChatModel.call(Prompt)` 路径双保险（官方语义：不自动执行 tool call，1.1.8 已验证）。 |
| V | 三档接入·零代码优先 | ✅ N/A | 本节不交付 Tool 体系（§20），仅交付工具**适配器**（schema 载体）；Agent 目录加载属 §17。 |
| VI | 审计数据 Day One 落库（NON-NEGOTIABLE） | ✅ | `llm_calls` 表本节就建+写，成功失败都落（含 provider/model/token/耗时/success/error_message）；`tool_invocations` 不在本节。 |
| VII | 每个 User Story 必须可演示 | ✅ | 本节 harness 全绿 + 集成冒烟真调一次拿到真响应 = 可演示成果；真模型冒烟由人工项承载。 |

## 明确不做（边界，逐项照搬课件「有几样先别做」）

- fallback（一家挂了换另一家）、hedge racing（同时发几家抢最快的）、熔断——本节故障直接把错误抛给上层。
- 成本看板——本节只落 `llm_calls` 这张表，够审计用；成本聚合/Web 看板放后面。
- 工具的**执行**——本节适配器只翻译 schema，不执行（执行权留给后续节循环+执行器）。
- ReAct 循环、Memory、Tool 体系、Web Service、定时——均不在本节。

## Project Structure（模块落位 + 文件级清单）

模块落位（skill §16 行）：

| 组件 | 模块 | 说明 |
|---|---|---|
| `Profile` / `ProfileLoader` / `ProfileRegistry` | oryxos-core | 第 16 节第一个消费 Profile 的能力 |
| `Prompt`（OryxOS 域，最小形态，spec R1） / `OryxTool`（最小接口，spec R1） / `LlmCallAudit`（审计门面接口，DIP） | oryxos-core | core 已依赖 `spring-ai-model`，`Prompt` 复用 Spring AI `Message` 不另造消息抽象 |
| `ProviderService` / `ToolSchemaAdapter`（工具格式适配器）/ `ProviderRegistry`（provider name→ChatModel 显式映射）/ `ProviderProperties`（绑 `oryxos.providers` 全局层）/ `ChatModelFactory`（程序化构造 ChatModel） | oryxos-provider | 依赖 core；不引 `*-starter`/autoconfigure |
| `LlmCall` 实体（`LlmCallEntity`）+ `LlmCallRepository` + `JpaLlmCallAudit`（`LlmCallAudit` 实现，走 Repository） | oryxos-storage | 仅 `llm_calls` 表 |
| 手工建表脚本 `oryxos-storage/src/main/resources/scripts/sql/001-init.sql`（仅 `llm_calls` 表） | oryxos-storage | 后续节按需追加表，或本脚本仅含此表 |
| `application.yaml` 的 `oryxos.providers` 全局层 | oryxos-boot | 课件示例形态（list：name + `api-key: ${ENV}`），由本节写入 |
| Profile YAML 示例（`.oryxos/profiles/*.yaml` 的 `provider` 段） | .oryxos（运行时工作区，gitignored） | 示例供 harness/冒烟用；不入库 | 

包名约定（全仓 English ASCII，宪法 §9）：`com.oryxos.core.profile`、`com.oryxos.core.prompt`、`com.oryxos.core.tool`、`com.oryxos.core.audit`、`com.oryxos.provider`（既有包）、`com.oryxos.storage.entity`/`com.oryxos.storage.repository`/`com.oryxos.storage.audit`。

## Testing strategy（按课件「验收 harness」执行）

测试策略句：测试策略按课件「验收 harness」执行：`ProfileLoaderTest`、`ProviderServiceTest`、`ToolSchemaAdapterTest`、`LlmCallRepositoryTest`、`ProviderSmokeIT`（覆盖关键回归点：**双 provider 按名路由不串台**、**调用失败留 audit success=false**、**带 schema 调用关闭自动执行**），单测默认跑、集成冒烟打 `@Tag("integration")` CI 跳过；实现完成的定义是 `mvn clean verify` 全绿。

**harness 映射表**（测试类 ↔ 覆盖点 ↔ 模块）：

| 测试类 | 覆盖的验收点 | 模块 | 跑法 |
|---|---|---|---|
| `ProfileLoaderTest` | 合法 YAML 全字段解析；引用不存在 provider 报错清晰；坏文件不阻断其余加载；`${ENV}` 占位从环境变量解析 | core | 单测默认跑 |
| `ProviderServiceTest` | 双 provider 按名路由不串台；未知名抛异常；成功/失败都落审计；自动执行关闭 | provider | 单测默认跑 |
| `ToolSchemaAdapterTest` | `OryxTool` schema 翻译成 Spring AI 格式后字段一一对齐；只翻译、产物里不含任何执行逻辑 | provider | 单测默认跑 |
| `LlmCallRepositoryTest` | 手工脚本建出的 `llm_calls` 能存能读，`success`/`error_message` 两列真实存在 | storage | 单测默认跑（真 SQLite） |
| `ProviderSmokeIT` | 真调一次模型，断言非空响应且 `llm_calls` 多一条 `success=true` | provider | `@Tag("integration")` 个人工手动跑 |

**三个最值钱的测试方法（课件写出代码，断言逻辑逐条保真落地，方法名译英文 + `@DisplayName` 承载课件原文）**：

1. `routesByProviderName_twoProvidersDoNotCrossover` —— mock 两家 ChatModel，按名调 kimi，断言 `verify(kimi, times(1)).call(any())` 且 `verify(deepseek, never()).call(any())`。（坑一回归）
2. `callFails_auditRecordsSuccessFalse` —— `when(chatModel.call(any())).thenThrow(RuntimeException("connect timeout"))`，断言 `assertThrows` 抛出**且** `verify(audit).record(eq("s-1"), eq("deepseek"), any(), isNull(), eq(false), contains("timeout"), anyLong())`。（失败审计回归，最易漏）
3. `callWithToolSchema_disablesAutoExecution` —— 带 tool 的调用，`ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class)` 捕获传给 `ChatModel.call` 的请求，断言其 `ToolCallingChatOptions.isInternalToolExecutionEnabled()==false` 且 `getToolCallbacks()` 非空。（课件的 `Request.class`/`autoExecuteTools()`/`tools()` 为示意 API，按 1.1.8 真实 `Prompt`+`ToolCallingChatOptions` 落地断言，断言逻辑保真）

## Risks & Mitigations

- **R1（spec 记录）`: Prompt/OryxTool 最小类型本节引入** → 后续 §17/§20 扩展时若需改公共接口，由对应节课件明列为「改造点」裁定（本节先建只承载本节所需成员的最小定义，不越界加方法）。
- **R2（spec 记录）`: surefire `excludedGroups=integration`** → 为落地「两档跑法」随本节在根 pom 配置；这是课件测试策略句的直接要求，影响全仓跑法（日常与 CI 跳 integration），见 Step 5 停点对账。
- **R-API（H3 pre-implement gate）`: Spring AI 1.1.8 直 `ChatModel.call(Prompt)` 是否真的不自动执行 / `ToolCallingChatOptions.internalToolExecutionEnabled(false)` 是否正是关断开关 / `ToolCallback` 的抽象方法集** → implement 期逐条核 1.1.8 jar（既有代码已证 `ChatModel.call(Prompt)→ChatResponse`、`response.getMetadata().getUsage().getPromptTokens()/getCompletionTokens()`、`ToolCallingChatOptions.builder().toolCallbacks(..).internalToolExecutionEnabled(false)` 可用；ToolCallback 的 `call`/`getToolDefinition` 等 method 签名在写前用 `javap`/反射核）。核实不到 → 软门禁停下。
- **R-MAP form**: 课件 application.yaml 全局层为 **list 形态**（`- name: deepseek / api-key: ${ENV}`），与仓库此前 `map 形态`（`oryxos.providers.<name>.type/api-key-env`）不同；本节按课件 list 形态实现 `ProviderProperties` 与 `ProfileLoader` 的 provider 存在性校验。
- **R-build transient**: Phase 0 已把 `spring.sql.init` 从主 `application.yaml` 摘除（脚本随本节重建后由 §16 决定是否恢复 `spring.sql.init.mode` 生产执行；`LlmCallRepositoryTest` 自行执行脚本建表，不依赖 boot sql.init）。

## Pre-Implement Verification Gate（H3）

逐任务执行前，凡涉及第三方 API 的任务先在本地依赖核实方法存在：
- `mvn dependency:tree` 确认 `spring-ai-deepseek`/`spring-ai-openai`/`spring-ai-alibaba-dashscope` 在锁定 BOM 内可解析、版本对。
- 反射/`javap` 核 `ToolCallback`、`ToolDefinition`、`ToolCallingChatOptions`、`ChatModel.call(Prompt)` 在 1.1.8 jar 的确切签名（避免照课件示意 API 写出编译不过的代码）。
核实不到 → 软门禁停下问用户。