# Feature Specification: 第 18 节 CLI（命令行入口 + 会话层 sessions 表）

**Feature Branch**: `018-lesson18-cli`

**Created**: 2026-08-26

**Status**: Draft

**Input**: 课件 `docs/class/第18节：CLI 功能概述、实现思路与代码讲解.md`--本节是 OryxOS 的"入口层"实现课（CLI 命令行 + 第一个真正用起来的会话层），来源实证以课件为准；技术方案对位 `TechnicalSolution` §8.4（Channel 接入）、§8.6（三种运行模式）、§8.7（命令行工具）、§9.2（sessions 表）。

**Source-of-truth note**: 延续 16/17 节裁定的逐节开发形态--**课件为唯一事实源**，实现形态逐字对齐课件骨架（含 chat 命令骨架、轻命令分流判断标准、验收 harness 的关键测试与方法名字面量如"同一三元组_历次getOrCreate都是同一个Session"）；与 `TechnicalSolution` 的既有差异按 16 节 spec 的裁定继续沿用（Profile 来自 `.oryxos/profiles/` 全局层；`AGENT.md` frontmatter 派生是 29 节的改造点）。17 节交付的 `Session`/`SessionManager` 最小形态（javadoc 明文预告"完整形态是第 18 节交付"）在本节升级为完整形态--属当节课件点名的改造点。

---

## Clarifications

### Session 2026-08-26（逐条采自课件「二、动手前先想清楚几件事」与技术方案 §8.4/§8.7/§9.2，无外部答案外推）

- **Q1: CLI 的职责边界是什么？** -> A（课件「第一」）: CLI 是消息进出的门，不是干活的人。一个 `chat` 命令的活就三步：读用户输入 -> 交给引擎处理 -> 把结果打印出来。它自己不想、不调模型、不执行工具。
- **Q2: 12 个命令怎么分轻重？** -> A（课件「第二」）: 判断标准只有一条--这个命令要不要调模型/跑引擎？要，就启动 Spring（重命令：`chat`/`serve`/`gateway`）；不要，就直接干文件操作（轻命令：`init`、`profile list` 等），**不启动 Spring 上下文，秒回**。
- **Q3: 命令行参数怎么解析？** -> A（课件「第三」）: 用 Picocli，一个命令一个 `@Command` 类，子命令、参数、帮助信息、报错提示全交给它；自己撸 `args[]` 解析既费劲又容易出 bug。
- **Q4: 重命令启动 Spring 的坑在哪？** -> A（课件「第四」）: `scanBasePackages` 只管普通 Bean 的组件扫描，**不会**带动自动配置的 `@EnableJpaRepositories`、`@EntityScan` 跟着扫到别的模块--这两个默认只按主类自己所在的包扫描。CLI 模块和存 Session/审计数据的模块是分开的 Maven 模块，不显式声明 `basePackages`，启动时会得到"Found 0 JPA repository interfaces"，审计写不进去、直接报错退出。
- **Q5: session_id 怎么生成？** -> A（课件「三」chat 逐行讲解）: `channel + user + profile` 三元组联合生成；**id 的拼接只发生在 `SessionManager` 内部这一处**，所有入口（CLI 传 `"cli"`、Web 传 `"web"`、定时传 `"scheduler"`）只提供三元组、不自己拼字符串--两处各拼一遍、格式差一个分隔符，同一个人就会出现两条互不相认的历史。
- **Q6: 会话历史怎么持久化？** -> A（课件「三」）: 对话历史整体序列化成 JSON 存 `messages_json` 一列，核心阶段**不做按条拆表**；`sessions` 表字段照技术方案 9.2；建表走**手工建表脚本**（SQLite 别依赖 `ddl-auto=update` 迁移）。
- **Q7: SessionManager 对外几个方法？** -> A（课件「三」）: 三个--`getOrCreate(channel, user, profileName)`、`get(sessionId)`、`save(session)`。
- **Q8: 三种运行模式什么关系？** -> A（课件「一」+§8.6）: `chat`/`serve`/`gateway` 三个都是运行模式，区别只在"消息从哪进来"（终端/HTTP/多通道），**共享同一份 Profile 配置和同一套 Session 存储**，底下的引擎是同一个。
- **Q9: 为什么会话层归这节交付、且 harness 只测它？** -> A（课件「四」）: CLI 本身是薄壳，值得自动化的是会话层--它是后面所有入口共用的地基，出口径问题最难查（27 节的缝隙③），所以在这就钉死：幂等、隔离、持久化、重启不丢。命令分流与 `--help` 属进程级行为，自动化成本大于收益，留人工清单。

### Resolve（在 implement 前需用户知悉，Step 5 停点对账）

- **R1（SessionManager/Session 升级为完整形态）**: 课件明确"SessionManager 和 sessions 表，这节一并交付"，属 17 节 javadoc 预告、本节课件点名的改造点。`SessionManager` 接口从最小形态（仅 `save`）升级为三方法完整形态；域类型 `Session`（留在 core）增补 `channel`/`userId` 与"携历史恢复"构造入口，**保留 17 节两参构造器向后兼容**（17 节测试零改动）。`AgentService.process` 签名不变。
- **R2（实体命名与 DIP 落位）**: JPA 实体命名 `SessionEntity`（storage 既有 `LlmCallEntity`/`ToolInvocationEntity` 先例；课件"Session 实体"按此形态落地，域类型 `Session` 留 core 不动）。`SessionManager` 的 JPA 实现命名 `JpaSessionManager` 放 oryxos-storage，与审计链（core 立接口、storage 落 JPA）同构 DIP；session_id 拼接公式（`channel:user:profile`）只存在于该实现这一处。
- **R3（messages_json 序列化用 Jackson）**: 序列化器用 `jackson-databind`（该工件已在依赖树中：core -> spring-ai-model 传递引入，版本 2.21.4 经 BOM 统一收敛，enforcer dependencyConvergence 已在锁）；在 oryxos-storage **显式声明**（不写版本，走父 POM dependencyManagement），属"把既有传递依赖提升为显式依赖"，不引入新工件。自写四型消息编解码（system/user/assistant+toolCalls/tool+responses）；消息 metadata 等非回填必需字段不保真（核心阶段口径，回读只保证 Prompt 组装所需字段完整）。
- **R4（引擎装配接线）**: 17 节 spec R5 预告"装配留到 18/20/22 节接线"，本节是 CLI 的接线点：新增 `EngineConfiguration`（core.agent，`@Configuration`）把 17 节 POJO（`ContextLoader`/`PromptBuilder`/`ReActLoop`/`ToolExecutor`/`AgentService`）接成 Bean（依赖一律按 core 立的端口接口注入：`ChatInvoker` 由 16 节 `ProviderService` 提供、`ToolInvocationAudit`/`SessionManager` 由 storage 的 JPA 实现提供）。**过渡件**：`ToolRegistry` 以配置类内嵌的空实现 Bean 顶位（20 节替换为真注册表）、`MemoryService` 传 null（22 节接线）。boot 上下文冒烟（16 节 `OryxOsApplicationTests`）自动成为装配回归。
- **R5（重命令启动类放 oryxos-cli）**: 新增 `SpringLauncher`（cli 模块）：`@SpringBootApplication(scanBasePackages = "com.oryxos")` + **显式** `@EntityScan`/`@EnableJpaRepositories`（basePackages 指向 storage 包）--即课件「第四」坑的解法本体。用 REGEX excludeFilter 排除 `com.oryxos.boot.*`，防止 fat JAR 里 boot 的 `OryxOsApplication` 被当作普通 `@Configuration` 重复装配。oryxos-cli pom 增加 `oryxos-storage` 依赖（fat JAR 本就聚合，此处为模块自足编译/单测）。
- **R6（12 命令计数口径与桩边界）**: 12 个子命令 = `init`/`status`/`chat`/`serve`/`gateway`/`profile list`/`profile create`/`profile show`/`profile delete`/`provider list`/`tool list`/`session list`（profile 四个子命令各计一个，与 §8.7 列表逐项对齐）。`serve`（Web Service 26 节）与 `gateway`（多通道守护进程，后续节）本节为**桩命令**：打印"将于第 N 节交付"清晰提示后正常退出，不吞异常不假装成功。`tool list` 走重命令路径打印注册表内容（当前为空，输出注明工具体系 20 节交付）。
- **R7（init 模板口径）**: 按技术方案 §8.1 生成 `.oryxos/` 完整结构 + 16 节裁定的 `profiles/` 全局层：`agents/`、`memory/MEMORY.md`、`mcp_servers.yaml`（注释占位）、`sessions/`、`logs/`、`profiles/`、Bootstrap 三件（`AGENTS.md`/`SOUL.md`/`USER.md` 简明模板）、默认 Profile `profiles/default.yaml`（引用全局层示例 provider deepseek，与 boot `application.yaml` 默认声明一致，用户可改）。`oryxos.db` 不由 init 创建（轻命令不碰 JDBC；首次重命令启动时由数据源自动建库）。
- **R8（provider list 为轻命令）**: 读 classpath 的 `application.yaml` 首个 YAML 文档（SnakeYAML，core 既有依赖），解析 `oryxos.providers` 列出 name/type/model；不启动 Spring（列表不调模型，符合课件分流判断标准）。
- **R9（fat JAR 主入口切换）**: boot pom 的 spring-boot-maven-plugin 显式声明 `mainClass = com.oryxos.cli.OryxOsCli`（CLI 是整个程序的 main 入口，课件「一」明言；不声明则 repackage 在 `OryxOsCli` 与 `OryxOsApplication` 两个 main 间无法裁决）。`OryxOsApplication` 保留（boot 模块冒烟测试与 `java -jar` 直启 Spring 的备用入口），非删除。

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 会话层：三元组身份 + 幂等 + 持久化不丢（Priority: P1）

用户在终端里和 Agent 多轮对话。第二轮开始时，底座必须能把这轮消息和上一轮接成同一段历史--靠的是会话身份：channel（消息从哪进来）+ user（谁在说话）+ profile（和哪个 Agent 说）三元组唯一确定一个会话。同一三元组历次进入拿到的是同一个会话（幂等）；任一元素不同就是不同会话（隔离）。会话历史整体序列化存进 `sessions` 表，进程重启后（新建上下文重查）历史还在。会话 id 的拼接只发生在 `SessionManager` 内部这一处--两处各拼一遍、格式差一个分隔符，同一个人就会出现两条互不相认的历史（27 节的缝隙③，现在钉死）。

**Why this priority**: 会话层是后面所有入口（CLI/Web/定时）共用的地基，出口径问题最难查；且它是本节 harness 的全部承载--CLI 本身是薄壳。

**Independent Test**: `SessionManagerTest`（mock 仓储的单测）：同一三元组两次 `getOrCreate` 返回同一个 Session；channel/user/profile 任一不同则不同；`get` 能取回、`save` 持久化。`SessionRepositoryTest`（真 SQLite 执行手工建表脚本）：`sessions` 表能存能读；`messages_json` 序列化回读后消息完整；模拟"重启"（新建 context 重查）历史还在。

**Acceptance Scenarios**:

1. **Given** 同一三元组（channel、user、profile），**When** 两次 `getOrCreate`，**Then** 返回 id 相同的 Session（幂等：多轮对话靠它串起来）。
2. **Given** channel 不同（其余相同），**When** 两次 `getOrCreate`，**Then** 返回不同 id 的 Session（channel 不同就是不同会话）；user、profile 不同同理。
3. **Given** 任何入口（CLI/Web/定时），**When** 需要会话，**Then** 只向 `SessionManager` 提供三元组，id 拼接只发生在 `SessionManager` 内部一处。
4. **Given** 一个已累积多轮历史的会话，**When** `save` 后 `get`，**Then** 取回的 Session 携带完整历史（user/assistant/tool 各型消息回读一致，可供下一轮 Prompt 组装）。
5. **Given** 进程重启（数据库文件还在），**When** 新上下文按同一三元组 `getOrCreate`，**Then** 拿到的是带历史的老会话，不是空的新会话。
6. **Given** `sessions` 表，**When** 建表，**Then** 由手工建表脚本创建（字段照 9.2：session_id 主键、profile_name、channel、user_id、messages_json、status、三个时间戳），不依赖 Hibernate 自动迁移。

---

### User Story 2 - 命令行骨架：12 个子命令 + 轻/重分流（Priority: P2）

用户装好 OryxOS 后敲 `oryxos init` 初始化工程、`oryxos profile list` 看有哪些 Agent、`oryxos provider list` 看接了哪些模型、`oryxos status` 看状态、`oryxos serve` 把服务跑起来。所有操作通过子命令完成，一个命令一个 `@Command` 类。命令按"要不要调模型/跑引擎"分两类：轻命令不启动 Spring、直接文件操作、秒回（`init`、`profile` 四连、`provider list`、`status`）；重命令才启动 Spring 上下文（`chat`、`serve`、`gateway`，以及要查数据库/注册表的 `session list`、`tool list`）。重命令的启动类显式声明 JPA 实体/仓储扫描范围，否则审计表写不进去、启动直接报错。

**Why this priority**: CLI 是用户亲手操作 OryxOS 的第一个入口；轻重分流是一开始就要定的，不然要么全都慢，要么后面改起来伤筋动骨。

**Independent Test**: 命令分流与 `--help` 属进程级行为（课件明言自动化成本大于收益，留人工清单）；本 story 的自动化承载在 US1 会话层与装配回归（boot 上下文冒烟验证重命令启动类的 JPA 扫描声明有效）。

**Acceptance Scenarios**:

1. **Given** 打包出的可执行 JAR，**When** 无参数运行，**Then** 显示用法帮助（Picocli 自带）。
2. **Given** 12 个子命令，**When** 各自执行 `--help`，**Then** 帮助信息正常显示（Picocli 自带，人工项）。
3. **Given** 轻命令（`init`、`profile list/create/show/delete`、`provider list`、`status`），**When** 执行，**Then** 不启动 Spring 上下文、秒回。
4. **Given** 重命令（`chat`/`serve`/`gateway`/`session list`/`tool list`），**When** 执行，**Then** 启动 Spring 上下文，且启动日志里 "Found N JPA repository interfaces" 的 N > 0。
5. **Given** `oryxos init`，**When** 在空目录执行，**Then** 生成 `.oryxos/` 完整工作区结构（agents/、memory/MEMORY.md、mcp_servers.yaml、sessions/、logs/、profiles/ 含默认 Profile、Bootstrap 三件）；再次执行不破坏已有内容。
6. **Given** `serve`/`gateway`，**When** 本节执行，**Then** 打印"将于第 26 节/后续节交付"的清晰提示后正常退出（桩命令，不假装成功）。
7. **Given** 三种运行模式（chat/serve/gateway），**When** 切换模式，**Then** 共享同一份 Profile 配置和同一套 Session 存储，数据不丢。

---

### User Story 3 - chat 运行模式：终端交互 + 引擎接线（Priority: P2）

用户敲 `oryxos chat`（或 `oryxos chat --profile weather`），终端出现提示符，一问一答；输入 `/quit` 退出。它的活就三步：读用户输入 -> 交给引擎处理（统一入口，里面跑的就是 ReAct 循环）-> 把结果打印出来。会话按三元组（channel="cli"、当前系统用户、profile）取用，多轮自然接续。chat 是第一个真正"用起来"引擎的入口--17 节交付的纯 POJO 引擎在本节接成可运行的整体。

**Why this priority**: 它是"看得见摸得着"的第一个东西--到这一步能在终端里真正跟自己搭的 Agent 说上话；与 Provider、ReAct 一起撑起 Demo 一的完整体验。

**Independent Test**: 进程级交互行为（stdin/stdout 循环）留人工清单（课件明言）；本 story 的自动化承载在装配回归：重命令启动的 Spring 上下文能装配出引擎链（boot 冒烟测试全绿即证据）。

**Acceptance Scenarios**:

1. **Given** 用户执行 `oryxos chat`，**When** 进入交互，**Then** 每输入一行就交给引擎处理、最终回复打印到屏幕，然后回到循环读下一句。
2. **Given** 交互中输入 `/quit`，**When** 处理，**Then** 跳出循环、命令结束（这是 CLI 唯一"自己判断"的逻辑）。
3. **Given** `--profile` 选项，**When** 未指定，**Then** 默认用名为 default 的 Profile。
4. **Given** chat 会话，**When** 取用 Session，**Then** channel 固定传 `"cli"`、user 取当前系统用户、profile 为所选 Profile 名--只提供三元组，不自己拼 id。
5. **Given** chat 全程，**When** 观察，**Then** CLI 不含任何 Agent 智能--不调模型、不执行工具、不想（这些全在引擎里）。
6. **Given** 标准输入关闭（EOF），**When** 读不到下一行，**Then** 正常退出循环，不抛栈。

---

## Edge Cases

- 空行输入：跳过不发给引擎（省一次无意义的 LLM 调用；`/quit` 判断本身 trim 后比较）。
- 标准输入 EOF（管道喂入、Ctrl+D）：读不到下一行时正常结束循环，不抛 `NoSuchElementException` 栈。
- `getOrCreate` 与 `save` 之间进程崩溃：本次对话历史丢失、下次拿到空会话--核心阶段接受（无 WAL 之外的额外保障），不入错误路径。
- `messages_json` 为空历史：新会话序列化为空数组/空串，回读得到零消息的会话，不报错。
- `profile create` 目标文件已存在：报错拒绝覆盖（不静默丢用户已有 Profile）。
- `profile show`/`profile delete` 目标不存在：报清晰错误。
- `init` 在已有工作区上执行：保留已有文件，只补缺失项（幂等）。
- `status`/`session list` 在未 `init` 的工作区执行：给出"请先 oryxos init"的清晰提示，不抛栈。
- 会话历史消息含工具调用（assistant 带 toolCalls、tool 结果消息）：序列化回读后 id/name/arguments/responseText 完整一致，下一轮 Prompt 组装可用。
- 三元组中含分隔符字符（如 profile 名带 `:`）：核心阶段接受可能的理论碰撞（分隔符公式是内部约定，入口侧 Profile 名本身受命名约束），不做转义。

## Requirements *(mandatory)*

### Functional Requirements

**会话层（US1）**

- **FR-001**: `SessionManager` 必须对外提供三个方法：`getOrCreate(channel, user, profileName)`、`get(sessionId)`、`save(session)`。
- **FR-002**: 同一三元组（channel、user、profile）历次 `getOrCreate` 必须返回 id 相同的 Session（幂等）；channel/user/profile 任一不同必须返回不同 id 的 Session（隔离）。
- **FR-003**: `session_id` 由 channel + user + profile 联合生成，拼接逻辑只存在于 `SessionManager` 实现内部一处；所有入口只提供三元组。
- **FR-004**: `get(sessionId)` 必须能取回携带完整历史的 Session；`save(session)` 必须把累积完历史的会话持久化（新会话插入、既有会话更新历史与最后活跃时间）。
- **FR-005**: 会话历史必须整体序列化成 JSON 存 `messages_json` 一列；回读后各型消息（用户消息、助手消息含工具调用、工具结果消息、系统消息）的回填必需字段完整一致。核心阶段不做按条拆表。
- **FR-006**: `sessions` 表必须由手工建表脚本创建，字段照技术方案 9.2（`session_id` 主键、`profile_name`、`channel`、`user_id`、`messages_json`、`status`、`created_at`、`last_active_at`、`archived_at`），不依赖 Hibernate 自动迁移。
- **FR-007**: 进程重启后（数据库文件还在），新上下文按同一三元组 `getOrCreate` 必须拿到带历史的老会话。

**命令行骨架（US2）**

- **FR-008**: 必须提供 CLI 主入口，注册 12 个子命令：`init`、`status`、`chat`、`serve`、`gateway`、`profile list`、`profile create`、`profile show`、`profile delete`、`provider list`、`tool list`、`session list`；一个命令一个 `@Command` 类，参数解析交给 Picocli。
- **FR-009**: 轻命令（`init`、`profile` 四连、`provider list`、`status`）不得启动 Spring 上下文，直接完成文件/配置操作。
- **FR-010**: 重命令（`chat`、`serve`、`gateway`、`session list`、`tool list`）必须启动 Spring 上下文；其启动类必须显式声明 `@EnableJpaRepositories(basePackages=...)` 与 `@EntityScan(basePackages=...)`（组件扫描声明带不动这两个，不显式声明则 JPA 仓储数为 0、审计写不进去）。
- **FR-011**: `init` 必须生成 `.oryxos/` 完整工作区结构：`agents/`、`memory/MEMORY.md`、`mcp_servers.yaml`、`sessions/`、`logs/`、`profiles/`（含默认 Profile）、`AGENTS.md`、`SOUL.md`、`USER.md`；重复执行幂等不破坏已有内容。
- **FR-012**: `profile list/create/show/delete` 直接操作 `.oryxos/profiles/` 下的 YAML 文件；`provider list` 列出全局层已声明的 provider（不启动 Spring）。
- **FR-013**: `serve`/`gateway` 本节为桩命令：打印"将于第 26 节/后续节交付"的清晰提示后正常退出。
- **FR-014**: 三种运行模式必须共享同一份 Profile 配置与同一套 Session 存储（同一工作区）。
- **FR-015**: `session list` 必须列出 `sessions` 表中的会话（id、profile、channel、user、最后活跃时间）；`tool list` 必须列出当前注册表中的工具（本节注册表为空属预期，输出注明工具体系后续节交付）。

**chat 运行模式（US3）**

- **FR-016**: `chat` 命令必须提供 `--profile` 选项（默认 `default`），交互循环：读一行 -> 交给引擎统一入口处理 -> 打印最终回复 -> 读下一行。
- **FR-017**: 输入 `/quit`（trim 后相等）必须退出循环结束命令；空行跳过不发给引擎；标准输入 EOF 时正常退出不抛栈。
- **FR-018**: chat 会话取用必须以三元组进行：channel 固定 `"cli"`、user 取当前系统用户、profile 为所选 Profile 名；不得在 CLI 侧拼接 session_id。
- **FR-019**: CLI 不得包含 Agent 逻辑：不调模型、不执行工具、不做停止判断以外的思考。
- **FR-020**: chat 必须经重命令启动的 Spring 上下文取得引擎（17 节 POJO 在本节接成 Bean 链），处理链路 = 统一入口 `process(会话, 消息)`。

### Non-Functional Requirements

- **NFR-001（技术栈）**: JDK 21 + Spring Boot 3.x + Spring AI Alibaba（锁定 BOM，动手前 `mvn dependency:tree` 确认目标依赖存在）、SQLite + Spring Data JPA、Picocli 4.7.6（父 POM 已锁）。凭证走环境变量占位，不落明文。`sessions` 用手工建表脚本，不依赖 `hibernate.ddl-auto=update`。除 R3 声明的 `jackson-databind` 显式化（既有传递依赖，无新工件）外不新增第三方依赖。
- **NFR-002（harness 形态）**: 课件 harness 两个测试类全部落地：`SessionManagerTest`（mock 仓储单测：幂等/隔离/取回/持久化）、`SessionRepositoryTest`（真 SQLite 执行手工建表脚本：存取/序列化回读完整/模拟重启历史还在）。命令分流与 `--help` 属进程级行为，不写自动化测试（课件明言留人工清单）。
- **NFR-003（静态门禁）**: `mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD/Spotless/Checkstyle）为实现完成定义；避开 P3C/ASM 解析不了的 Java 18+ 语法形态。
- **NFR-004（启动速度）**: 轻命令不加载 Spring 上下文，目标亚秒级返回（人工项验证）。
- **NFR-005（标识符纪律）**: 标识符一律英文 ASCII；测试方法名英文（课件 harness 的中文方法名译成语义等价英文名落地，课件原文进 `@DisplayName` 以便对号）。
- **NFR-006（兼容纪律）**: 17 节测试零改动（`Session` 两参构造器保留）；`AgentService`/`ReActLoop` 等 17 节公共签名不动；boot 冒烟测试保持绿并升级为装配回归证据。

### Open Questions

- OQ1（已记录于 R1/R2，停点确认）: `SessionManager` 升级三方法 + `Session` 增补字段（保留 17 节构造器）与 `SessionEntity`/`JpaSessionManager` 落位。
- OQ2（已记录于 R3，停点确认）: `jackson-databind` 在 storage 显式声明（既有传递依赖显式化）作为 messages_json 序列化器。
- OQ3（已记录于 R4/R5，停点确认）: 引擎装配 `EngineConfiguration`（含空 ToolRegistry 过渡 Bean）与 cli 模块 `SpringLauncher`（REGEX 排除 boot 包）。
- OQ4（已记录于 R6/R7/R8/R9，停点确认）: 12 命令口径与 serve/gateway 桩、init 模板内容、provider list 轻命令读 classpath yaml、boot mainClass 切换。

### Dependencies & Assumptions

- **前序（16/17 节交付物，已存在性核对）**: `Profile`/`ProfileLoader`/`ProfileRegistry`（`.oryxos/profiles/` 全局层、`ProfileConfiguration` 装配）、`ProviderService`（implements `ChatInvoker`）、`ReActLoop`/`PromptBuilder`/`ToolExecutor`/`AgentService`/`ProfileContext`/`ContextLoader`（17 节 POJO）、`ToolRegistry`/`SessionManager`/`MemoryService` 端口、`LlmCall`/`ToolInvocation` 审计链与 `001-init.sql` 手工建表模式、surefire `integration` 两档跑法。
- **本地依赖核实（H3 前置）**: Picocli 4.7.6 `@Command`/`@Option`/`CommandLine` 已在父 POM dependencyManagement 锁定；`jackson-databind` 2.21.4 已在依赖树（core -> spring-ai-model 传递）且 enforcer dependencyConvergence 收敛通过；Spring AI 1.1.8 消息类型（`UserMessage`/`SystemMessage`/`AssistantMessage`+`ToolCall`/`ToolResponseMessage`+`ToolResponse`）公开构造器已由 17 节核实。
- **假设**: Memory 模块（22 节）未交付--装配时 MemoryService 传 null（第二部分缺席）；Tool 体系（20 节）未交付--注册表为空实现 Bean；Web Service（26 节）未交付--`serve` 为桩；核心阶段内网、单实例；工作区目录由 `oryxos.workspace-dir` 配置（默认 `.oryxos`）。

---

## Success Criteria

1. 课件"验收 harness"两个测试类落地且 `mvn test` 全绿（关键回归：**同一三元组幂等**、**channel 不同即不同会话**、**messages_json 回读完整**、**模拟重启历史还在**）。
2. `mvn clean verify` 全绿（含全部静态门禁）。
3. "本节交付物"逐项存在性核对通过（代码/测试/表/约定四类）。
4. 前序（16/17 节）全部测试回归绿（`Session` 两参构造器兼容即跨节契约证据）。
