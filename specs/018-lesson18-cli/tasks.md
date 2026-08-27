---

description: "Task list for 第 18 节 CLI（命令行入口 + 会话层 sessions 表 + 引擎装配）"
---

# Tasks: 第 18 节 CLI

**Input**: Design documents from `/specs/018-lesson18-cli/`（spec.md + plan.md）；课件 `docs/class/第18节：CLI 功能概述、实现思路与代码讲解.md`。

**Prerequisites**: plan.md（required）、spec.md（required）。前序依赖：**第 16/17 节全部交付物**（H0 已存在性核对：Profile 链/ProviderService(implements ChatInvoker)/ReAct 引擎 POJO 链/审计链两表/001-init.sql/surefire 两档跑法均就位）；基线 `mvn clean verify` 全绿（17 节收尾态，2026-08-26 实测）。

**Tests**: harness 先行/伴随--测试任务与对应实现任务成对出现；`mvn clean verify` 全绿为实现完成定义；本节无 `@Tag("integration")` 项（课件：命令分流与 `--help` 属进程级行为留人工清单）。

**Organization**: 按 spec.md 的依赖序组织（会话域升级 -> 存储底座 -> 引擎装配 -> CliChannel -> CLI 命令 -> 打包收尾）；包根 `com.oryxos`，标识符一律英文 ASCII（宪法 §9）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 不同文件、无未完成依赖，可并行
- **[Story]**: US1（会话层）/ US2（命令行骨架）/ US3（chat 运行模式）/ 共享底座
- 路径相对仓库根；包根：`com.oryxos`

## Path Conventions

- Maven 多模块：`<module>/src/main/java/com/oryxos/<domain>/...`
- 测试：`<module>/src/test/java/com/oryxos/<domain>/...`
- 建表脚本：`oryxos-storage/src/main/resources/scripts/sql/001-init.sql`（追加，不新建文件）
- **装配纪律（延续 17 节 R5）**: 17 节交付的 POJO 类不加 Spring 注解；本节新 Spring 触点仅 `EngineConfiguration`（core）/`JpaSessionManager`（storage）/`CliChannel`（channel-cli）/`SpringLauncher`（cli）

---

## Phase 0: 前置核对（已完成）

- [x] T-R0 H0 依赖检查：16/17 节交付物逐项存在性核对通过；分支 `018-lesson18-cli` 就位（自 17 节分支顶端切出，17 尚未合入 master）；`mvn clean verify` 基线绿（8 模块 SUCCESS）；core 模块测试全为纯单测（无 `@SpringBootTest`，`EngineConfiguration` 落 core 不影响既有测试，已核实）。

## Phase 1: core 会话域升级（US1 前置，17 节最小形态 -> 完整形态）

**Purpose**: 课件点名的改造点（17 节 javadoc 预告"完整形态是第 18 节交付"）。

- [ ] T-C1 `SessionManager` 接口升级（`com.oryxos.core.session`）：保留 `void save(Session session)`，增补 `Session getOrCreate(String channel, String user, String profileName)` 与 `Optional<Session> get(String sessionId)`；javadoc 写明 session_id 由 channel+user+profile 联合生成且拼接只发生在实现内部一处（课件「三」）。
- [ ] T-C2 [P] `Session` 域类型增补：增补 `channel()`/`userId()` 访问器、四参构造器 `Session(String id, String profileName, String channel, String userId)`、静态工厂 `Session restore(String id, String profileName, String channel, String userId, List<Message> messages)`（messages 防御拷贝入列）；**保留两参构造器**（channel/user=null，17 节测试零改动）；javadoc 更新"完整形态已于 18 节交付"。

## Phase 2: storage 会话层（US1 主体，harness 先行/伴随）

**Purpose**: sessions 表三件套 + 消息编解码 + SessionManager JPA 实现；与审计链同构 DIP。

- [ ] T-ST1 手工建表脚本 `001-init.sql` **追加** `sessions` 表（9 列照 §9.2：`session_id` TEXT PRIMARY KEY、`profile_name` TEXT NOT NULL、`channel`、`user_id`、`messages_json`、`status` TEXT NOT NULL、`created_at`/`last_active_at`/`archived_at` TEXT NOT NULL 级别照既有口径定），含 `profile_name` 索引；`CREATE TABLE IF NOT EXISTS` 幂等；脚本头注释更新"第 18 节"说明。
- [ ] T-ST2 [P] `SessionEntity`（`com.oryxos.storage.entity`，`@Table(name="sessions")`，字段与脚本逐列对齐；`session_id` 主键 String（应用层赋值，不 autoincrement）；时间戳 ISO-8601 字符串实体层写入--口径照抄 `LlmCallEntity`） + `SessionRepository`（`com.oryxos.storage.repository`，`JpaRepository<SessionEntity, String>`）。
- [ ] T-ST2T `SessionRepositoryTest`（`com.oryxos.storage`，单测真 SQLite，经 test yaml `spring.sql.init` 执行脚本建表，口径照抄 `LlmCallRepositoryTest`）：①手工脚本建出的 `sessions` 存一条后可读回（各列对齐）；②**messages_json 序列化回读后消息完整**（经 `MessageJsonCodec` 往返：user/assistant+toolCalls/tool 各型，断言条数与文本、toolCalls 的 id/name/arguments、toolResponses 的 id/name/responseText 一致）；③**模拟"重启"（新建 context 重查）历史还在**（文件型 datasource + `@DirtiesContext` 令下一测试方法拿新 context 重读同一 DB 文件，断言历史仍在）。harness 先行/伴随。
- [ ] T-ST3 [P] `MessageJsonCodec`（`com.oryxos.storage.session`，包私有类：`String serialize(List<Message>)` / `List<Message> deserialize(String)`；Jackson `ObjectMapper`；四型分派 system/user/assistant(content+toolCalls)/tool(responses)；未知 type 抛清晰异常） + storage pom 增 `com.fasterxml.jackson.core:jackson-databind`（不写版本，spec R3 既有传递依赖显式化）。
- [ ] T-ST4 `JpaSessionManager`（`com.oryxos.storage.session`，`@Component implements SessionManager`，构造注入 `SessionRepository` + `MessageJsonCodec`）：**session_id 公式 `String.join(":", channel, user, profileName)` 只在此一处**；`getOrCreate` -> `findById`，查有则 `Session.restore(...)` 携历史返回，查无则 new（不落库，首次 save 时插入）；`get` -> `findById` 查有 restore / 查无 empty；`save` -> `findById`，查有则更新 `messages_json`/`last_active_at`/`status`，查无则插入（channel/user 取自 Session，可 null--17 节-era 会话兼容）。
- [ ] T-ST4T `SessionManagerTest`（`com.oryxos.storage`，单测 Mockito mock `SessionRepository`）：①**课件关键回归** `sameTripleGetOrCreateAlwaysSameSession`（@DisplayName「同一三元组_历次getOrCreate都是同一个Session」：断言逻辑逐条保真--两次 getOrCreate 同 id；`"web"` channel 不同则 id 不同）；②user/profile 维度隔离（任一不同 id 不同）；③`get` 查有携历史恢复/查无 empty；④`save` 查无插入完整列、查有只更新历史与时间戳（channel/user 不被抹掉）；⑤`getOrCreate` 查有时返回带历史的老会话。harness 先行/伴随。

## Phase 3: 引擎装配（US3 前置，17 节 R5 预告的 18 节接线点）

**Purpose**: 让 chat 拿得到能跑的引擎--17 节 POJO 链接成 Bean（spec R4）。

- [ ] T-E1 `EngineConfiguration`（`com.oryxos.core.agent`，`@Configuration`）：`@Bean ContextLoader`（`@Value("${oryxos.workspace-dir:.oryxos}")` 构造）；`@Bean ToolRegistry`（内嵌私有静态空实现，javadoc 注明 20 节替换）；`@Bean PromptBuilder(contextLoader, toolRegistry, null)`（MemoryService=null，22 节接线）；`@Bean ToolExecutor(toolRegistry, toolInvocationAudit)`（注入 `ToolInvocationAudit` 端口 bean）；`@Bean ReActLoop(promptBuilder, chatInvoker, toolExecutor)`（注入 `ChatInvoker` 端口 bean）；`@Bean AgentService(profileRegistry, reActLoop, sessionManager)`（注入 `SessionManager` 端口 bean）。17 节 POJO 本身零改动。

## Phase 4: CliChannel（US3 主体）

**Purpose**: chat 的读-转交-打印壳（§8.4：读 stdin 写 stdout、维护当前 Session、每输入调 `AgentService.process`、`/quit` 退出）。

- [ ] T-CH1 `CliChannel`（`com.oryxos.channel.cli`，`@Component`，构造注入 `AgentService` + `SessionManager`）：`void chat(String profileName)`--`sessionManager.getOrCreate("cli", currentUser(), profileName)`（currentUser = `System.getProperty("user.name", "cli-user")`）；try-with-resources `Scanner(System.in)` 循环：`print("> ")` -> 读行 -> `"/quit".equals(line.trim())` break -> 空行 continue -> `agentService.process(session, line)` -> `System.out.println(reply)`；`hasNextLine()` 为 false（EOF）正常退出；引擎异常打印 `处理失败: <原因>` 后继续循环（不吞--stdout 可见，异常链不打断交互）。javadoc 注明"CLI 只管进出不管干活"。

## Phase 5: CLI 主入口 + 12 命令（US2 主体）

**Purpose**: `OryxOsCli` 主入口 + 12 个 `@Command` 类 + 轻/重分流 + 重命令启动类。

- [ ] T-L1 `SpringLauncher`（`com.oryxos.cli`，重命令启动类，spec R5）：`@SpringBootApplication(scanBasePackages="com.oryxos")` + **显式** `@EntityScan("com.oryxos.storage.entity")` + `@EnableJpaRepositories("com.oryxos.storage.repository")` + `@ComponentScan` excludeFilters REGEX `com\.oryxos\.boot\..*`（防 fat JAR 内 boot 主类重复装配）；静态 `ConfigurableApplicationContext start(String... args)`：先 ensureWorkspaceDirs（口径照抄 boot 主类）再 `SpringApplication.run(SpringLauncher.class, args)`。
- [ ] T-L2 [P] `OryxOsCli`（`com.oryxos.cli`，`@Command(name="oryxos", version=..., mixinStandardHelpOptions=true, subcommands={...12 个...})`，`Runnable` 打印用法帮助；`main`：`System.exit(new CommandLine(new OryxOsCli()).execute(args))`）。
- [ ] T-L3 [P] 轻命令 7 个（`com.oryxos.cli.command`，全部不启动 Spring、直接文件操作，异常转清晰错误消息）：
  - `InitCommand`：生成 `.oryxos/` 结构（`agents/`、`memory/MEMORY.md`、`mcp_servers.yaml` 注释占位、`sessions/`、`logs/`、`profiles/default.yaml` 默认 Profile（引 deepseek，与 boot application.yaml 全局层一致）、`AGENTS.md`/`SOUL.md`/`USER.md` 简明模板）；幂等（已存在跳过不覆盖）；`oryxos.db` 不创建（首次重命令启动自动建）。
  - `StatusCommand`：工作区路径/是否已 init/Profile 数/DB 文件是否存在；未 init 给"请先 oryxos init"提示。
  - `ProfileListCommand`（列 `.oryxos/profiles/*.yaml` 文件名）、`ProfileCreateCommand`（`--name` 必填，写默认模板 YAML；已存在报错拒绝覆盖）、`ProfileShowCommand`（打印 YAML 原文；不存在报错）、`ProfileDeleteCommand`（删除；不存在报错）。
  - `ProviderListCommand`：SnakeYAML `loadAll` 读 classpath `application.yaml` 首文档，列 `oryxos.providers` 的 name/type/model；读不到给清晰提示。
- [ ] T-L4 [P] 重命令 5 个（`com.oryxos.cli.command`，经 `SpringLauncher.start()` 起上下文后取 bean）：
  - `ChatCommand`：`@Option(names="--profile", defaultValue="default")`；起 Spring 取 `CliChannel` bean 调 `chat(profileName)`；退出码 0。
  - `ServeCommand`：桩--打印"Web Service 将于第 26 节交付"后正常退出（不起 Spring）。
  - `GatewayCommand`：桩--打印"多通道守护进程将于后续节交付"后正常退出。
  - `SessionListCommand`：起 Spring 取 `SessionRepository`，列出 id/profile/channel/user/last_active_at；空表打印"暂无会话"。
  - `ToolListCommand`：起 Spring 取 `ToolRegistry`，`all()` 为空时打印"暂无注册工具（内置 Tool 体系第 20 节交付）"。
- [ ] T-L5 [P] `OryxOsCliTest`（`com.oryxos.cli`，单测 picocli 级**轻量**冒烟：`new CommandLine(new OryxOsCli()).getSubcommands().keySet()` 恰含 12 个子命令名；`--help` exit code 0 且输出含 "Usage"（进程内验证注册完整性，不启 Spring、不碰文件系统--进程级分流行为仍留人工清单））。harness 先行/伴随。

## Phase 6: 打包与装配回归（US2/US3 收口）

- [ ] T-P1 pom 三处：oryxos-cli pom 增 `oryxos-storage` 依赖（spec R5，模块自足编译）；oryxos-storage pom 增 `jackson-databind`（若 T-ST3 未含）；oryxos-boot pom spring-boot-maven-plugin 显式 `<mainClass>com.oryxos.cli.OryxOsCli</mainClass>`（spec R9，fat JAR 主入口）。
- [ ] T-P2 装配回归：`mvn clean verify` 全绿（含 16 节 boot 冒烟 `OryxOsApplicationTests`--`EngineConfiguration` 接线后上下文仍能起，即 chat 链路装配证据；含 17 节全部测试零改动回归）。

## Phase 7: 节级收尾 DoD（六项证据）

- [ ] T-D1 `mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD/Spotless/Checkstyle）；贴关键输出。
- [ ] T-D2 harness 映射表每个测试类存在且非空；课件关键回归测试 `sameTripleGetOrCreateAlwaysSession` 对号（方法名英文 + @DisplayName 承载课件原文「同一三元组_历次getOrCreate都是同一个Session」）。
- [ ] T-D3 课件「本节交付物」逐项 ls/grep 存在性核对（代码 5 项 / 测试 2 项 / 表 1 项 / 约定 2 项）。
- [ ] T-D4 前序节（16/17 节）全部测试回归绿（跨节契约证据：17 节测试零改动即 `Session` 两参构造器兼容的证明）。
- [ ] T-D5 H4 六条全局不变量逐条自查：①涉外 IO 首行过 `Sandbox.enforce`（本节无涉外工具；CLI 命令为本地文件操作，Sandbox 未就位--调用位在 ToolExecutor，24 节接线）②LLM 调用成败都落 `llm_calls`、工具执行成败都落 `tool_invocations`（16/17 节链路，本节装配不触碰）③grep 无明文 key ④`session_id` 只在 `SessionManager` 内拼接（本节落点：`JpaSessionManager` 一处，grep 验证）⑤无 Reactor/`CompletableFuture`/自建线程池 ⑥无 Spring AI 自动工具执行路径。
- [ ] T-D6 验收报告 + 课件「做完怎么验」剩余人工项清单（`oryxos chat` 真模型多轮对话、轻命令秒回/重命令才起 Spring、`Found N JPA repository interfaces` N>0、三模式共享存储、12 命令 `--help`）+ 变更总结（给 reviewer 的导读，git status/diff 实测三段结构）。
