# Implementation Plan: 第 18 节 CLI（命令行入口 + 会话层 sessions 表）

**Branch**: `018-lesson18-cli` | **Date**: 2026-08-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/018-lesson18-cli/spec.md`；课件 `docs/class/第18节：CLI 功能概述、实现思路与代码讲解.md`；技术方案对位 §8.4/§8.6/§8.7/§9.2。

## Summary

交付 OryxOS 的"人推"第一入口：**CLI 命令行**（`OryxOsCli` 主入口 + 12 个 `@Command` 子命令，轻/重命令分流）与它踩实的**会话层**（`SessionManager` 完整形态三方法、`sessions` 表、`messages_json` 整体序列化）。chat 是第一个真正"用起来"引擎的运行模式：读 stdin -> 交 `AgentService.process` 统一入口 -> 打印回复，`/quit` 退出。为让 chat 跑通，本节完成 17 节预告的引擎装配（`EngineConfiguration` 把 POJO 链接成 Bean），并以 cli 模块的 `SpringLauncher` 落实课件「第四」坑的解法（重命令启动类显式声明 `@EnableJpaRepositories`/`@EntityScan`）。形态**逐字对齐课件**（chat 骨架、轻命令判断标准、harness 关键测试断言逻辑逐条保真）。

## Technical Context

**固定技术栈句**：JDK 21 + Spring Boot 3.x + Spring AI Alibaba（动手前先跑 `mvn dependency:tree` 确认锁定 BOM 里目标依赖存在）、SQLite + Spring Data JPA。凭证走环境变量占位，不落明文。SQLite 用手工建表脚本，不依赖 `hibernate.ddl-auto=update`。

- **Language/Version**: Java 21（本节无并发逻辑；chat 循环单线程阻塞读 stdin）。
- **Primary Dependencies**:
  - Spring Boot **3.5.16**、Spring AI BOM **1.1.8**（消息类型既有）、**Picocli 4.7.6**（父 POM dependencyManagement 已锁，cli 模块已声明）。
  - `jackson-databind` **2.21.4**：已在依赖树（core -> spring-ai-model 传递，enforcer dependencyConvergence 收敛）；本节在 oryxos-storage **显式声明**（spec R3，不写版本走父 POM 管理），无新工件。
  - SQLite：`sqlite-jdbc` + `hibernate-community-dialects`（storage 既有）。
- **Storage**: SQLite（`.oryxos/oryxos.db`）；`sessions` 追加进手工脚本 `scripts/sql/001-init.sql`（`CREATE TABLE IF NOT EXISTS` 幂等，boot `sql.init.mode=always` 自动执行）；`ddl-auto=none`；仓储测试经 test yaml 的 `spring.sql.init` 执行脚本建表（口径照抄 `LlmCallRepositoryTest`）。
- **Testing**: JUnit 5 + Mockito + AssertJ（`spring-boot-starter-test` 既有）。`SessionManagerTest` mock `SessionRepository` 单测；`SessionRepositoryTest` 真 SQLite。命令分流与 `--help` 属进程级行为，不自动化（课件明言，人工清单）。本节无 `@Tag("integration")` 项。
- **Target Platform**: 企业内网单实例；fat JAR 主入口切换为 `com.oryxos.cli.OryxOsCli`（spec R9）。
- **语法禁区句**：避开 P3C/ASM 解析不了的 Java 18+ 语法形态（如增强 switch 的 `default ->` 写法），静态检查是构建门禁。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design. 原则来源 `.specify/memory/constitution.md`。*

| # | 原则 | 判定 | 证据/说明 |
|---|------|------|-----------|
| I | 固定技术栈与九模块结构 | ✅ | 只在既有 9 模块内加类（core 3 文件改动 + storage 4 新文件 + channel-cli 1 + cli 14 + DDL 追加 + 3 处 pom），不增删模块；JDK 21 + Boot 3.x；Picocli/Jackson 均为锁定 BOM 内既有工件。 |
| II | 核心阶段只做运行时内核 | ✅ | 交付 CLI 入口 + 会话层 + 引擎装配；**不做** IM Channel、serve/gateway 深层功能（桩 + 后续节）、按条拆表、命令分流自动化（见"明确不做"）。 |
| III | 自实现 ReAct 循环 | ✅ | 本节不触碰循环；仅把 17 节 POJO 引擎装配成 Bean 链，循环行为不变（17 节测试零改动回归即证据）。 |
| IV | Spring AI 只用一半 | ✅ | 本节不引任何 Spring AI 自动执行路径；装配注入的是 16 节已关自动执行的 `ProviderService`（经 `ChatInvoker` 端口）。 |
| V | 三档接入·零代码优先 | ✅ N/A | 本节不交付 Tool 体系（§20）；装配中 `ToolRegistry` 为内嵌空实现过渡 Bean（spec R4，20 节替换）。 |
| VI | 审计数据 Day One 落库 | ✅ | 重命令启动类显式 `@EnableJpaRepositories`/`@EntityScan`（课件「第四」坑的解法本体）保证 `llm_calls`/`tool_invocations` 在 CLI 触发的引擎链路里照常落库；`sessions` 表按同一手工建表口径交付。 |
| VII | 每个 User Story 必须可演示 | ✅ | US1 harness 全绿；US2/US3 人工项=12 命令 `--help`、轻命令秒回、`chat` 真模型多轮对话、`Found N JPA repository interfaces` N>0（见验收报告）。 |

## 明确不做（边界）

- **CLI 不含 Agent 逻辑**--不调模型、不执行工具、不做停止判断以外的思考（课件「第一」）。
- **不做 IM Channel**（企业微信/飞书/钉钉/Slack--扩展阶段，§8.4）。
- **serve/gateway 只做桩**--Web Service 26 节细讲，多通道守护进程后续节（spec R6）。
- **会话历史不做按条拆表**--整体序列化存 `messages_json` 一列（课件「三」）。
- **命令分流与 `--help` 不写自动化测试**--进程级行为，人工清单（课件「四」）。
- 另（16/17 节已定，本节延续不做）：认证/限流、SSE/WebSocket、多租户。

## Project Structure（模块落位 + 文件级清单）

模块落位（skill §18 行）：`OryxOsCli` + 12 命令 -> oryxos-cli；`CliChannel` -> oryxos-channel-cli；`SessionManager` 接口 -> oryxos-core；`Session` 实体 + `SessionRepository` -> oryxos-storage。

| 组件 | 模块/包 | 说明 |
|---|---|---|
| `SessionManager` 接口升级（spec R1） | `com.oryxos.core.session` | 17 节最小接口（仅 `save`）升级为三方法：`Session getOrCreate(String channel, String user, String profileName)`、`Optional<Session> get(String sessionId)`、`void save(Session session)` |
| `Session` 域类型增补（spec R1） | `com.oryxos.core.session` | 增补 `channel()`/`userId()` 访问器与携历史恢复构造入口（`restore` 静态工厂）；**保留 17 节两参构造器**（channel/user 为 null，17 节测试零改动） |
| `SessionEntity` + `SessionRepository`（spec R2） | `com.oryxos.storage.entity` / `com.oryxos.storage.repository` | `@Table(name="sessions")`，字段与 001-init.sql 逐列对齐（`session_id` 主键 String、时间戳 ISO-8601 字符串实体层写入--口径照抄 `LlmCallEntity`）；`JpaRepository<SessionEntity, String>` |
| `MessageJsonCodec`（spec R3） | `com.oryxos.storage.session` | Jackson 双向编解码：`List<Message>` <-> JSON 数组；四型消息（system/user/assistant+toolCalls/tool+responses），回填必需字段保真 |
| `JpaSessionManager`（spec R2） | `com.oryxos.storage.session` | `@Component implements SessionManager`；**session_id 公式 `channel:user:profile` 只在此一处**；`getOrCreate` 查无则建、查有则携历史恢复；`save` 查有则更新 `messages_json`/`last_active_at`/`status`，查无则插入 |
| `001-init.sql` 追加 `sessions` 表 | oryxos-storage resources | 9 列照 §9.2 + `profile_name` 索引；`CREATE TABLE IF NOT EXISTS` 幂等 |
| `EngineConfiguration`（spec R4） | `com.oryxos.core.agent` | `@Configuration` 装配 17 节 POJO：`ContextLoader`（@Value workspace-dir）-> `PromptBuilder`（MemoryService=null）-> `ReActLoop`（注入 `ChatInvoker` 端口）+ `ToolExecutor`（注入 `ToolInvocationAudit` 端口）-> `AgentService`（注入 `SessionManager` 端口 + `ProfileRegistry`）；内嵌私有空 `ToolRegistry` Bean（20 节替换） |
| `CliChannel` | `com.oryxos.channel.cli` | `@Component`；`void chat(String profileName)`：`getOrCreate("cli", currentUser(), profileName)` -> Scanner 循环（`> ` 提示符、`/quit` 退出、空行跳过、EOF 正常结束）-> `agentService.process(session, line)` -> println；currentUser = `System.getProperty("user.name", "cli-user")` |
| `SpringLauncher`（spec R5） | `com.oryxos.cli` | 重命令启动类：`@SpringBootApplication(scanBasePackages="com.oryxos")` + **显式** `@EntityScan("com.oryxos.storage.entity")` + `@EnableJpaRepositories("com.oryxos.storage.repository")` + REGEX excludeFilter 排除 `com.oryxos.boot.*`（防 fat JAR 内 boot 主类被重复装配）；静态 `start(String...)` 先 ensureWorkspaceDirs 再 `SpringApplication.run` |
| `OryxOsCli` + 12 个 `@Command` 类 | `com.oryxos.cli.command`（或平铺 `com.oryxos.cli`） | `OryxOsCli`：`@Command(name="oryxos", mixinStandardHelpOptions=true, subcommands={12 个})` + `main`（`System.exit(new CommandLine(new OryxOsCli()).execute(args))`）；命令类见下"12 命令清单" |
| 轻命令 7 个 | oryxos-cli | `InitCommand`（生成 .oryxos 结构+默认 Profile，幂等）、`StatusCommand`（工作区/Profile/DB 状态；未 init 给提示）、`ProfileListCommand`/`ProfileCreateCommand`/`ProfileShowCommand`/`ProfileDeleteCommand`（直操作 `.oryxos/profiles/*.yaml`）、`ProviderListCommand`（SnakeYAML 读 classpath `application.yaml` 首文档列 `oryxos.providers`） |
| 重命令 5 个 | oryxos-cli | `ChatCommand`（`--profile` 默认 default，起 Spring 取 `CliChannel` bean）、`ServeCommand`/`GatewayCommand`（桩：打印"将于第 26 节/后续节交付"退出）、`SessionListCommand`（`SessionRepository.findAll` 列表）、`ToolListCommand`（`ToolRegistry.all()`，空则注明 20 节交付） |
| pom 三处 | oryxos-cli / oryxos-storage / oryxos-boot | cli 增 `oryxos-storage` 依赖（spec R5）；storage 增 `jackson-databind` 显式声明（spec R3）；boot spring-boot-maven-plugin 增 `mainClass=com.oryxos.cli.OryxOsCli`（spec R9） |

**装配纪律（延续 17 节 R5）**: 17 节交付的 POJO 类**不加任何 Spring 注解**（`EngineConfiguration` 是新增配置类，不动它们）；本节新 Spring 触点仅 `EngineConfiguration`（core）、`JpaSessionManager`（storage）、`CliChannel`（channel-cli）、`SpringLauncher`（cli）。core 模块自身测试全为纯单测（已核实无 `@SpringBootTest`），`EngineConfiguration` 不会被 core 单测意外启动。

## Testing strategy（按课件「验收 harness」执行）

测试策略句：测试策略按课件「验收 harness」执行：`SessionManagerTest`、`SessionRepositoryTest`（覆盖关键回归点：**同一三元组历次 getOrCreate 幂等**、**channel/user/profile 任一不同即不同会话**、**messages_json 序列化回读后消息完整**、**模拟重启（新建 context 重查）历史还在**），单测默认跑（本节无集成冒烟--命令分流与 `--help` 属进程级行为，课件明言留人工清单）；实现完成的定义是 `mvn clean verify` 全绿。

**harness 映射表**（测试类 ↔ 覆盖点 ↔ 模块）：

| 测试类 | 覆盖的验收点 | 模块 | 跑法 |
|---|---|---|---|
| `SessionManagerTest` | 同一三元组两次 `getOrCreate` 返回**同一个** Session（幂等）；channel/user/profile 任一不同则是不同 Session；id 生成只此一处（`get` 取回、`save` 持久化语义） | storage（mock `SessionRepository` 单测 `JpaSessionManager`） | 单测默认跑 |
| `SessionRepositoryTest` | 手工建表脚本建出的 `sessions` 表能存能读；`messages_json` 序列化回读后消息完整（user/assistant+toolCalls/tool 各型）；模拟"重启"（新建 context 重查）历史还在 | storage（真 SQLite） | 单测默认跑 |
| `OryxOsApplicationTests`（16 节既有，回归用） | 装配后上下文能起（`EngineConfiguration` 接线不炸、JPA 仓储被扫到） | boot | 单测默认跑（回归证据） |

**课件关键回归测试（写出代码的，断言逻辑逐条保真落地，方法名译英文 + `@DisplayName` 承载课件原文）**：

`sameTripleGetOrCreateAlwaysSameSession`（@DisplayName「同一三元组_历次getOrCreate都是同一个Session」）--

```java
var first  = sessionManager.getOrCreate("cli", "wang", "default");
var second = sessionManager.getOrCreate("cli", "wang", "default");
assertEquals(first.id(), second.id());          // 幂等：多轮对话靠它串起来

var other = sessionManager.getOrCreate("web", "wang", "default");
assertNotEquals(first.id(), other.id());        // channel 不同就是不同会话
```

（断言逐条保真；`SessionManagerTest` 另补 user/profile 维度隔离、`get`/`save` 语义；`SessionRepositoryTest` 承载重启回归。）

## Risks & Mitigations

- **R1~R9（spec 记录）**: 接口升级/实体命名/Jackson 显式化/引擎装配/启动类落位/桩边界/init 模板/轻命令口径/mainClass 切换 -> Step 5 固定停点与用户对账后才 implement。
- **R-double-app（fat JAR 重复装配）**: cli 启动类扫 `com.oryxos` 会把 boot 的 `OryxOsApplication`（fat JAR 内）当普通 `@Configuration` 重复装配 -> REGEX excludeFilter 排除 `com.oryxos.boot.*`；boot 冒烟测试回归验证两套启动路径各自健康。
- **R-roundtrip（消息回读保真）**: `messages_json` 回读只需保证下一轮 Prompt 组装所需字段（文本/toolCalls 的 id-name-arguments/toolResponses 的 id-name-responseText）；metadata 等不保真（核心阶段口径，spec R3 已裁定）。测试覆盖四型消息。
- **R-constructors（Spring AI 构造器）**: `AssistantMessage.builder().content().toolCalls().build()`、`ToolResponseMessage.builder().responses().build()`、`UserMessage(String)`/`SystemMessage(String)` 均已在 17 节 javap 核实（codec 反序列化直接复用）；implement 期若遇新 API 一律先核再用，核实不到 -> 软门禁停下。
- **R-classpath（provider list 轻命令）**: 读 classpath `application.yaml` 需处理多文档 YAML（`---` 分隔）--SnakeYAML `loadAll` 取首文档；fat JAR 内该资源存在（boot 模块打包）。
- **R-P3C/静态门禁**: 实体遵循既有 getter 模式；`Scanner` 关闭（try-with-resources）；`System.exit` 仅在 main；轻命令文件操作异常转清晰错误消息（Picocli 异常处理）；测试方法名英文。

## Pre-Implement Verification Gate（H3）

逐任务执行前，凡涉及第三方 API 的任务先在本地依赖核实方法存在：Picocli 4.7.6（`@Command`/`@Option`/`CommandLine`/`RunLast`）、Jackson `ObjectMapper`（`readTree`/`writeValueAsString`/`JsonNode`）--`mvn dependency:tree` 确认工件在锁；核实不到 -> 软门禁停下问用户。

## 关键设计决定（implement 期逐字执行的对齐锚）

1. **session_id 公式**：`String.join(":", channel, user, profileName)`，只存在于 `JpaSessionManager` 一处（课件：id 拼接只在 SessionManager 内部；分隔符为内部约定，入口侧不感知）。
2. **Session 域类型**：`Session(String id, String profileName)` 保留（channel/user=null）；新增 `Session(String id, String profileName, String channel, String userId)` 与 `static Session restore(String id, String profileName, String channel, String userId, List<Message> messages)`（防御拷贝入列）。
3. **JpaSessionManager 语义**：`getOrCreate` -> `findById(公式 id)`，查有则 `restore`（携历史），查无则 new（不落库，首次 `save` 时插入）；`save` -> `findById`，查有则更新 `messages_json`/`last_active_at`/`status`，查无则插入（channel/user 取自 Session，可能为 null--17 节-era 会话兼容）。
4. **MessageJsonCodec**：JSON 数组每元素 `{"type": "...", ...}`；type ∈ `system|user|assistant|tool`；序列化按 instanceof 分派，反序列化按 type 构造；未知 type 抛清晰异常（数据损坏不静默）。
5. **chat 骨架逐字对齐课件**：`getOrCreate("cli", currentUser(), profileName)` -> `Scanner` 循环 `print("> ")` -> `"/quit".equals(line.trim())` break -> `agentService.process(session, line)` -> `println(reply)`；增量仅空行跳过与 EOF 保护（Edge Cases 已裁定）。
6. **轻重分流判断标准逐字对齐课件**："这个命令要不要调模型/跑引擎？要，就启动 Spring；不要，就直接干文件操作"--`session list`/`tool list` 查数据库与注册表（Spring 管的 Bean），归重命令（spec 已裁定）。
7. **init 幂等**：逐项 `Files.createDirectories`/`writeString(..., CREATE_NEW 语义)`--已存在文件跳过不覆盖；默认 Profile 引 deepseek（与 boot `application.yaml` 全局层默认声明一致）。
8. **serve/gateway 桩**：stdout 打印明确"将于第 26 节交付"/"后续节交付"，exit code 0（命令存在且诚实，不假装成功）。
