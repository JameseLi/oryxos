# Feature Specification: 第 17 节 ReAct 循环（Agent 的"想-做-看"核心引擎 + tool_invocations 审计）

**Feature Branch**: `017-lesson17-react`

**Created**: 2026-08-26

**Status**: Draft

**Input**: 课件 `docs/class/第17节：ReAct 原理解析、实现与代码讲解.md`--本节是 OryxOS 第二块核心能力（ReAct 循环）的实现课，来源实证以课件为准；技术方案对位 `TechnicalSolution` §4（ReAct/AgentService/ProfileContext）、§8.3（ContextLoader）、§9.2（tool_invocations）。

**Source-of-truth note**: 延续第 16 节裁定的逐节开发形态--**课件为唯一事实源**，实现形态逐字对齐课件骨架（含课件给出的两个关键回归测试与方法名字面量如"达到最大轮数"）；与 `TechnicalSolution` 的既有差异按 16 节 spec 的裁定继续沿用（Profile 来自 `.oryxos/profiles/` 全局层，`AGENT.md` frontmatter 派生是 29 节的改造点，本节不涉及）。课件正文与验收 harness 引用、但未列入"本节交付物"清单的最小类型，按 16 节 spec R1 先例以最小形态引入并列为 Resolve 项（见下），后续节（18/20/22）在其上演进。

---

## Clarifications

### Session 2026-08-26（逐条采自课件「二、动手前先想清楚几件事」与技术方案 §4/§8.3，无外部答案外推）

- **Q1: ReAct 循环的职责边界是什么？** -> A（课件「第一」）: 循环本身只做**调度**--转圈、判断该不该停、把每轮结果攒起来。拼 prompt、调模型、执行工具全部交给专门模块；循环里塞的东西越少越好读、越不容易出 bug。
- **Q2: 为什么自己写循环、不用框架现成的 Agent 封装？** -> A（课件「第二」）: 循环是 Agent 最需要自己掌控的地方（什么时候停、工具失败怎么办、上下文怎么截断），框架黑盒动不了这些。核心阶段自己写这几十行（宪法原则三）。
- **Q3: 三个坑怎么防？** -> A（课件「第三」）: ①不设轮数上限会死循环->最大轮数兜底（默认 10，Profile 可覆盖）；②不管上下文长度会撑爆->会话历史只留最近 N 轮（默认 20，Profile 可覆盖）；③每轮不累积回会话就没法审计、下一轮接不上->LLM 响应与工具结果都必须先存回会话历史。
- **Q4: 停止条件是什么？** -> A（课件「一」）: 模型这一轮没提出调工具 = 它觉得能给最终答复了，循环返回结果；万一一直要调工具停不下来，靠最大轮数强制收尾。
- **Q5: 调 LLM 时为什么要带 sessionId？** -> A（课件「三」逐行讲解）: 上一节 Provider 的 chat 方法签名带了 sessionId，因为 `llm_calls` 审计表按 session 关联；工具执行同理带 sessionId（`tool_invocations` 也要关联 session）。
- **Q6: 工具执行的审计口径？** -> A（课件「配角二」）: 成功要记、失败也要记（`success`/`error_message` 两列），跟上一节 Provider 审计同一个口径；工具执行**只在这一个地方**发生（宪法原则四：关掉 Spring AI 自动执行，执行权收在自家执行器，不能有第二条路）。
- **Q7: PromptBuilder 四部分是什么、顺序如何？** -> A（课件「配角一」）: ①system prompt（角色设定 + 启动信息 + Skill，**末尾附当前日期时间**--模型不知道今天几号，定时场景的"今天"全靠这一行）②长期记忆（跨会话记忆，**没开就跳过**）③会话历史（只留最近 N 轮，默认 20）④当前可用工具列表。长期记忆 ≠ 会话历史：前者跨会话，后者是本次对话的往来记录。
- **Q8: ContextLoader 两条铁律？** -> A（课件「供给者」）: ①每次组装 prompt 重新读文件、**不缓存**（用户改完立即生效）；②Profile 里显式引用的文件缺失要**报错**、Bootstrap 缺失至少 **WARN**--静默跳过会造成"人格悄悄丢了"这种最难查的软故障。
- **Q9: 为什么需要 ProfileContext（ThreadLocal）？** -> A（课件「编排者」）: 工具接口签名不带 Profile，但有些工具执行时要知道"当前是哪个 Agent"（如 notify 读当前 Profile 的 notify_channels）。让统一入口在处理开始时把 Profile 放进 ThreadLocal、结束（finally）清掉；虚拟线程下每请求独占一线程，天然不串，但**用完必须清**。
- **Q10: 三个触发源怎么进循环？** -> A（课件「编排者」+§4.2）: CLI、Web、定时三种触发源最终都调**同一个**统一入口 `process(会话, 消息)`，循环不感知消息来源。
- **Q11: 建表口径？** -> A（课件「配角二」）: `tool_invocations` 的实体、Repository、建表脚本归本节交付，口径跟上一节 `llm_calls` 一致--SQLite 手工建表脚本（含 `success`/`error_message` 列），不依赖 Hibernate 自动迁移。

### Resolve（在 implement 前需用户知悉，Step 5 停点对账）

- **R1（最小类型集，延续 16 节 R1 先例）**: 课件正文与验收 harness 引用、但未列入本节交付物清单的最小类型，本节以最小形态引入：
  - `Session`（会话域类型：id、profileName、消息累积）与 `SessionManager` **最小接口**（仅 `save`，课件 AgentService 骨架引用；完整形态 18 节交付：session_id 拼接公式 / 查找OrCreate / `sessions` 表 / 实体）；
  - `ToolResult`（成功标识 + 结果内容 + 错误信息；§6.1 终局的"是否可重试"留 20 节）；
  - `ToolRegistry` **接口**（按名查找 + 列表；实现与内置 Tool 是 20 节交付）；
  - `MemoryService` **最小接口**（仅 `buildContext(Session)`，签名对齐 22 节课件已定死的门面签名；"没开就跳过"= 未装配时第二部分整体缺席）；
  - `ToolInvocationAudit` 端口（core 立接口、storage 落 JPA 实现--与 `LlmCallAudit`/`JpaLlmCallAudit` 同构的 DIP）。
- **R2（LLM 调用端口）**: Maven 依赖方向为 provider -> core（16 节既定，不可反转），而本节循环在 core、ProviderService 在 provider。故 core 立一个**LLM 调用端口接口**（`chat(sessionId, Profile, Prompt) -> ChatResponse` 语义与 16 节 ProviderService.chat 逐字同构），16 节的 `ProviderService` **实现该端口**（仅追加 `implements`，不改任何既有类名/方法签名/字面量--属向后兼容扩展，非破坏性改动）。此为 16 节 spec R1 预告的"§17 改造点"。
- **R3（OryxTool 增补 execute）**: 16 节 `OryxTool` 仅承载 schema 翻译三方法，其 javadoc 已预告"执行由 ToolExecutor（§17）补齐"。本节为其增补 `execute`（JSON 参数入、统一结果类型出），对齐 §6.1 终局四方法形态；20 节在其上完成 Tool 体系。
- **R4（两处语义读法，按最直读法落地、停点确认）**: ①"最近 N 轮"的**轮 = 会话历史里的 1 条消息**（最简截断，课件明言"简单办法顶着，够用就行"；孤儿 tool 消息风险记入 Edge Cases，上下文压缩属扩展阶段）；②"Bootstrap 缺失至少 WARN"读作 **bootstrap 未声明或声明文件缺失 -> WARN 后跳过**（不静默、不阻断），"Skill 引用缺失 -> 报错"按 harness 原文照办。
- **R5（本节不注册 Spring Bean）**: 本节 core 交付类（循环/组装器/执行器/编排者/上下文加载器）做成**纯 POJO 不加 Spring 注解**--其依赖的 ToolRegistry/SessionManager/MemoryService 实现要到 18/20/22 节才存在，现在注册会拖垮 boot 上下文冒烟；课件 harness 本就全是单测（mock + 临时目录），装配留到各实现节接线。
- **R6（tool_invocations 仓储测试）**: 课件明言"口径跟上一节 llm_calls 一致"，而 16 节口径含仓储测试（真 SQLite 执行手工建表脚本、存取两列断言）--本节据此补 `ToolInvocationRepositoryTest`（harness 映射表 5 类之外的第 6 个测试类，同口径依据）。

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - ReAct 循环引擎：想-做-看转圈直到收敛（Priority: P1）

用户对 Agent 说"看看今天天气，帮我决定穿什么"。Agent 不是一句话答完：模型先提出要调天气工具，底座执行工具、把结果回填给模型，模型看到结果再给穿搭建议。这"想一步、做一步、看结果"的循环就是 ReAct，是整个 Agent 最关键的一段代码；CLI、Web、定时三种触发源共用它。

**Why this priority**: ReAct 是 Agent 的大脑循环，区别于"你问一句我答一句"的 chatbot 的关键；没有它，工具体系、定时任务、Web 接口全都无从谈起。它是本节的灵魂交付。

**Independent Test**: 用 mock 的 LLM 服务与工具执行器驱动循环：无工具调用一轮收尾；有工具调用则执行并回填进下一轮；每轮都调工具时转满最大轮数强制停（恰好 N 轮、返回含"达到最大轮数"的提示）；每轮 LLM 响应与工具结果都累积进会话。

**Acceptance Scenarios**:

1. **Given** 用户发来一条消息，**When** 循环开始，**Then** 消息先被追加进会话历史，之后每轮才基于完整历史组装 Prompt。
2. **Given** 某轮模型响应没有提出调工具，**When** 循环判断停止条件，**Then** 立即返回该轮响应文本，循环结束。
3. **Given** 某轮模型响应提出调工具，**When** 循环处理，**Then** 工具逐个（按序、不并行）经执行器执行，每个结果都回填进会话历史，随后进入下一轮组装。
4. **Given** 模型每一轮都要求调工具、永不收敛，**When** 循环转满最大轮数（默认 10，Profile 可覆盖），**Then** 强制停止并返回含"达到最大轮数"的提示（恰好 N 轮 LLM 调用，一轮不多）。
5. **Given** 循环的任意一轮，**When** LLM 响应或工具结果产生，**Then** 都必须先累积回会话历史（含向 LLM 调用与工具执行传 sessionId 供审计按会话关联），事后可审计、下一轮接得上。
6. **Given** LLM 调用抛出异常，**When** 异常传到循环，**Then** 循环不吞异常、向上传播（Provider 层已按 16 节口径落失败审计）。

---

### User Story 2 - Prompt 组装与上下文供给：每轮发给模型的内容（Priority: P2）

每轮循环开始前，把"要发给模型的内容"拼好：这个 Agent 是谁（角色设定 + 启动信息 + Skill，末尾带上当前日期时间--模型不知道今天几号）、记住了什么（长期记忆，没开就跳过）、聊到哪了（会话历史，只留最近 N 轮防撑爆）、手上有什么工具可用。文件内容每次重新读不缓存，用户改完立即生效。

**Why this priority**: Prompt 组装质量直接决定模型行为；上下文截断（坑二）与日期时间注入是定时场景（25 节）的前置。它是循环的输入侧，P1 之后最优先。

**Independent Test**: 用临时目录放 Bootstrap/Skill 文件、内存会话与假工具注册表驱动组装器：四部分顺序正确；历史超 N 轮被截断；system prompt 末尾含当前日期时间；改文件后下一次组装立即读到新内容；Skill 引用缺失报错、Bootstrap 缺失 WARN。

**Acceptance Scenarios**:

1. **Given** 一份带角色设定、Bootstrap、Skill 的 Profile 与已有历史的会话，**When** 组装 Prompt，**Then** 四部分按固定顺序就位：system prompt（角色 + 启动信息 + Skill，末尾当前日期时间）→ 长期记忆（未启用则整体缺席）→ 会话历史 → 可用工具列表。
2. **Given** 会话历史超过 N 轮（默认 20，Profile 可覆盖），**When** 组装，**Then** 只保留最近 N 轮、更早的丢弃。
3. **Given** 任何一次组装，**When** 生成 system prompt，**Then** 末尾附当前日期时间。
4. **Given** 用户在两次组装之间修改了 Bootstrap/Skill 文件，**When** 下一次组装，**Then** 读到的是新内容（无缓存）。
5. **Given** Profile 显式引用的 Skill 文件不存在，**When** 加载上下文，**Then** 报错（不静默跳过）。
6. **Given** Bootstrap 未声明或声明的文件不存在，**When** 加载上下文，**Then** 至少记 WARN 后继续（不阻断，但不静默）。

---

### User Story 3 - 工具执行与审计：执行权唯一收口 + 成败都落库（Priority: P2）

模型这一轮说要调 `http_get`。底座从工具注册表里找到它，过一道沙箱/白名单检查（检查本体后续节交付，此处留调用位），执行、把结果包成统一结果类型回填给模型；同时无论成败都往 `tool_invocations` 审计表记一条--成功 `success=true`，失败 `success=false` 带原因。工具执行只发生在这一个地方，绝无第二条执行路径。

**Why this priority**: 执行权收口是宪法原则四的落点；审计成败都落库是"可审计"卖点在工具侧的地基（宪法原则 VI）。与 US2 并列，互不依赖。

**Independent Test**: 用假注册表（可成功/可抛错的假工具）+ mock 审计端口驱动执行器：成功写审计 `success=true`；失败也写 `success=false` 带原因、异常不吞；仓储测试用真 SQLite 执行手工建表脚本存取 `tool_invocations` 两列断言。

**Acceptance Scenarios**:

1. **Given** 模型请求调用注册表里存在的工具，**When** 执行器处理，**Then** 从注册表按名找到工具、执行、结果包成统一结果类型返回（供循环回填）。
2. **Given** 工具执行成功，**When** 调用结束，**Then** 审计表多一条 `success=true` 记录（含会话标识、工具名、入参、结果、耗时）。
3. **Given** 工具执行抛异常或工具名不存在，**When** 执行器捕获，**Then** 审计表多一条 `success=false` + 原因的记录，异常不被静默吞掉（原因留痕），失败信息回填给模型继续对话。
4. **Given** 任何一次工具执行，**When** 执行前，**Then** 经过沙箱/白名单检查调用位（本节为预留接线点，检查本体后续节交付）。
5. **Given** `tool_invocations` 表，**When** 建表，**Then** 由手工建表脚本创建、含 `success` 与 `error_message` 两列（不依赖 Hibernate 自动迁移）。

---

### User Story 4 - 统一处理入口：编排 + ProfileContext + 会话持久化（Priority: P2）

三种触发源（CLI、Web、定时）最终都调同一个统一入口。它很短：按会话所属 Profile 名取 Profile → 放进 ThreadLocal 上下文 → 跑循环 → 持久化会话 → finally 清掉上下文。ThreadLocal 让工具执行时能知道"当前是哪个 Agent"而不用改工具接口签名；用完必须清，否则虚拟线程复用时下一个请求会拿到别人的 Profile。

**Why this priority**: 编排层把三源归一（"OS"的最小体现）并守住 ThreadLocal 卫生；它是 US1 的壳，依赖 US1 但不依赖 US2/US3 细节。

**Independent Test**: mock 循环与会话管理器驱动入口：处理期间 ThreadLocal 里可取到当前 Profile；循环抛异常时 finally 也把 ThreadLocal 清掉（`current()` 为 null）；正常结束后会话被持久化。

**Acceptance Scenarios**:

1. **Given** 一个属于某 Profile 的会话，**When** 统一入口处理一条消息，**Then** 处理期间经 ThreadLocal 上下文可取到该 Profile。
2. **Given** 处理过程中抛出异常，**When** 异常向上传播，**Then** finally 仍把 ThreadLocal 清掉（`current()` 返回 null）--防串号回归钉死。
3. **Given** 处理正常完成，**When** 循环返回，**Then** 累积完历史的会话经会话管理器持久化，最终响应返回给调用方。
4. **Given** 会话所属 Profile 名在注册表里不存在，**When** 入口取 Profile，**Then** 报清晰错误（不悄悄用错）。

---

## Edge Cases

- 模型一轮响应里带多个工具调用：按顺序逐个执行（核心阶段不做并行）。
- 模型请求的工具名在注册表里不存在：按失败路径处理并留审计（原因"未知工具"），不中断对话。
- 工具执行抛出任意运行时异常：捕获、审计 `success=false` 带原因、失败信息回填模型；LLM 调用异常则不吞、直接上抛（Provider 层已审计）。
- 会话历史正好 N 轮：不截断；截断可能产生孤儿 tool 结果消息（其所属 assistant 工具调用消息被截掉）--核心阶段按简单截断接受此边界，上下文压缩/智能裁剪属扩展阶段。
- Profile 未配 `settings`：最大轮数/历史轮数用默认值（10/20）；配了非法值（<=0）按校验错误处理。
- Profile 的 `tools` 为空：可用工具为空子集（组装进 Prompt 的工具列表为空，不默认放开全部）。
- Profile 未声明 `bootstrap` 或声明的文件缺失：WARN 后继续；`skills` 引用缺失：报错。
- 长期记忆未装配（22 节前）：第二部分整体缺席，不产生空段。
- 达到最大轮数时的收尾提示含"达到最大轮数"字样（课件字面量）。

## Requirements *(mandatory)*

### Functional Requirements

**ReAct 循环（US1）**

- **FR-001**: 收到用户消息必须先追加进会话历史，再开始循环；每轮组装 Prompt 必须基于完整会话历史。
- **FR-002**: 每轮迭代必须"组装 Prompt → 经 Provider 端口调一次 LLM（带 sessionId 供审计按会话关联）→ 响应先累积回会话历史"，顺序不可颠倒。
- **FR-003**: LLM 响应无工具调用时必须立即返回该轮响应文本并结束循环（停止条件）。
- **FR-004**: LLM 响应带工具调用时必须逐个按序执行（核心阶段不并行），每个工具结果都累积回会话历史后进入下一轮。
- **FR-005**: 循环必须受最大轮数上限约束：默认 10、Profile 的 settings 可覆盖；达到上限强制收尾，返回文本必须含"达到最大轮数"字样，且 LLM 调用次数恰好等于上限（一轮不多）。
- **FR-006**: 每轮 LLM 响应与工具结果都必须累积进会话历史（可审计、下一轮接得上）；调用 LLM 与执行工具时都必须传 sessionId。
- **FR-007**: 循环自身不得直接调模型或执行工具（调模型经 Provider 端口、执行经工具执行器）；LLM 调用异常不吞、向上传播。

**Prompt 组装与上下文（US2）**

- **FR-008**: 每轮 Prompt 必须按固定顺序组装四部分：①system prompt ②长期记忆 ③会话历史 ④可用工具列表。
- **FR-009**: system prompt 必须含角色设定 + 启动信息（Bootstrap）+ Skill 内容，**末尾附当前日期时间**。
- **FR-010**: 长期记忆未启用（未装配）时第二部分必须整体缺席（不产生空段、不报错）。
- **FR-011**: 会话历史必须按"最近 N 轮"截断：默认 20、Profile 可覆盖；system prompt 不参与截断。
- **FR-012**: 上下文文件（Bootstrap/Skill）每次组装 Prompt 必须重新读取、不得缓存（修改后下一次组装立即生效）。
- **FR-013**: Profile 显式引用的 Skill 文件缺失必须报错；Bootstrap 未声明或文件缺失必须至少记 WARN（不静默、不阻断）。
- **FR-014**: 可用工具列表必须按 Profile 的 tools 字段从注册表过滤（空字段 = 空子集）。

**工具执行与审计（US3）**

- **FR-015**: 工具执行器必须从工具注册表按名查找工具并执行，结果包成统一结果类型返回；执行权只在此一处（无第二条执行路径，Spring AI 自动执行已按 16 节关闭）。
- **FR-016**: 执行前必须预留沙箱/白名单检查调用位（本节不实现检查本体，注明后续节接线）。
- **FR-017**: 每次工具调用无论成败都必须写 `tool_invocations` 审计记录：成功 `success=true`；失败（执行异常/未知工具）`success=false` + `error_message` 记原因；异常不得被静默吞掉。
- **FR-018**: `tool_invocations` 表必须由手工建表脚本创建（含 `success` 与 `error_message` 两列），不依赖 Hibernate 自动迁移。
- **FR-019**: 工具失败的结果文本必须回填给模型继续对话（失败是业务语义，不是中断对话的异常）。

**统一入口与编排（US4）**

- **FR-020**: 三种触发源最终必须调同一个统一入口 `process(会话, 消息)`；入口内部依次：取 Profile → 放入 ThreadLocal 上下文 → 跑循环 → 持久化会话 → 返回响应。
- **FR-021**: 处理期间必须能经 ThreadLocal 上下文取到当前 Profile；无论正常结束还是抛异常，finally 都必须清掉上下文（`current()` 返回 null）。
- **FR-022**: 处理正常完成后必须把累积完历史的会话经会话管理器持久化。
- **FR-023**: 会话所属 Profile 名不存在时必须报清晰错误（不悄悄用错）。

### Non-Functional Requirements

- **NFR-001（技术栈）**: JDK 21 + Spring Boot 3.x + Spring AI（锁定 BOM 1.1.8）；SQLite + Spring Data JPA；`tool_invocations` 用手工建表脚本（宪法工程约束），不引新第三方依赖。
- **NFR-002（纯单测 harness）**: 本节交付物全部不碰网络：LLM 服务、工具、文件系统分别以 mock / 假实现 / 临时目录替代，harness 全是单测、秒级跑完；无集成冒烟项（课件明言"全部不碰网络"）。
- **NFR-003（静态门禁）**: `mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD/Spotless/Checkstyle）为实现完成定义；避开 P3C/ASM 解析不了的 Java 18+ 语法形态。
- **NFR-004（接口中立）**: 新立端口接口签名不出现某一档实现特有词汇（记忆端口不出现 Markdown/SQLite/Mem0；工具注册表不出现 MCP/注解扫描字样；沙箱只留调用位不出现白名单实现词）。
- **NFR-005（标识符纪律）**: 标识符一律英文 ASCII；测试方法名英文（课件 harness 的中文方法名译成语义等价英文名落地，课件原文进 `@DisplayName` 以便对号）。
- **NFR-006（装配克制）**: 本节 core 交付类不注册 Spring Bean（其依赖实现 18/20/22 节才存在），boot 上下文冒烟保持绿；`ProviderService implements LLM 调用端口` 是对 16 节唯一触碰点且为向后兼容扩展。

### Open Questions

- OQ1（已记录于 R1/R2/R3，停点确认）: 最小类型集（Session/SessionManager/ToolResult/ToolRegistry/MemoryService/ToolInvocationAudit/LLM 调用端口）与 `OryxTool.execute` 增补是否可按最小形态落地。
- OQ2（已记录于 R4，停点确认）: "轮=消息条数"截断语义与"Bootstrap 缺失 WARN / Skill 缺失报错"的读法。
- OQ3（已记录于 R5，停点确认）: 本节不注册 Spring Bean、装配留待后续节的接线策略。

### Dependencies & Assumptions

- **前序（16 节交付物，已存在性核对）**: `Profile`/`ProfileLoader`/`ProfileRegistry`（含 `Settings.maxIterations/maxHistoryTurns`、`Bootstrap.files`、`skills` 字段）、`ProviderService.chat(sessionId, Profile, Prompt)`（本节将 implements core 端口）、`LlmCallAudit` 审计链与 `001-init.sql` 手工建表模式、`Prompt`/`OryxTool` 最小类型、surefire `integration` 两档跑法配置。
- **本地依赖核实（H3 前置）**: Spring AI 1.1.8 `AssistantMessage.ToolCall(id/type/name/arguments)`、`hasToolCalls()`、`ChatResponse.getResult().getOutput()`、`ToolResponseMessage.ToolResponse(id/name/responseText)` 及其 Builder、`UserMessage(String)`/`SystemMessage(String)` 均已在本地仓库 javap 核实。
- **假设**: Memory 模块（22 节）未交付--长期记忆仅留端口、未装配即跳过；Tool 体系（20 节）未交付--注册表仅接口、无内置工具，测试用假实现；SessionManager 完整形态（18 节）未交付--本节仅 `save` 最小接口；核心阶段内网、单实例、虚拟线程每请求独立。

---

## Success Criteria

1. 课件"验收 harness"五个测试类 + 同口径仓储测试全部落地且 `mvn test` 全绿（关键回归：最大轮数强制停恰好 N 轮、ThreadLocal 异常清理、无缓存重读、四部分顺序、截断、成败双路审计）。
2. `mvn clean verify` 全绿（含全部静态门禁）。
3. "本节交付物"逐项存在性核对通过（代码/测试/表/约定四类）。
4. 前序（16 节）全部测试回归绿。
