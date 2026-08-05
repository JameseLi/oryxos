# Feature Specification: 第 16 节 Agent Provider（对接 LLM 的薄适配层 + Profile 解析）

**Feature Branch**: `016-lesson16-provider`

**Created**: 2026-08-02

**Status**: Draft

**Input**: 课件 `docs/class/第16节：Agent Provider 原理解析、实现与代码讲解.md`——本节是 OryxOS 第一块核心能力（对接 LLM）的实现课，来源实证以课件为准。

**Source-of-truth note**: 本节是逐节开发流程的第一节代码课。实现形态**逐字对齐课件**（用户已明确指示「按第 16 节文档内容重新实现」，并推翻仓库此前 US-1+US-2 合并实现所采用的 `TechnicalSolution §11.1`「`.oryxos/profiles/` 取消、Profile 由 AGENT.md frontmatter 派生」形态）。本特性因此按课件引入 `.oryxos/profiles/` 全局层 + 独立 `Profile`/`ProfileLoader`/`ProfileRegistry`，与 `TechnicalSolution §8.2/§11.1` 的既有设计存在**已确认的形态差异**：该项偏离经用户显式裁定，本特性范围内以课件为唯一事实源；后续节是否会回对齐 `TechnicalSolution` 留待对应节课件裁定。

---

## Clarifications

### Session 2026-08-02（逐条采自课件，无外部答案外推）

- **Q1: Profile 从哪里加载？** → A（课件「第零步」）: 启动时扫 `.oryxos/profiles/` 下所有 YAML，用 SnakeYAML 解析成 `Profile`；逐个做合法性校验；坏文件记错误日志、**不阻断**其余加载。
- **Q2: Profile 的合法性校验本节做哪些？** → A（课件「第零步」）: 本节只校验「Profile 引用的 `provider.name` 能在全局层（`application.yaml` 的 `oryxos.providers`）找到同名项」这一条；Profile 的其余字段（tools/skills/channels/…）的校验规则留给后续各节各自补。
- **Q3: 配置分几层，各管什么？** → A（课件「第一步」）: 全局层（`application.yaml`）声明接了哪些 provider、凭证从哪个环境变量读（解决「连不连得上」）；Profile 层（每个 YAML）声明具体用哪个 provider/model/温度（解决「这个 Agent 怎么用」）。Profile 引用的 `provider.name` 找不到同名全局项 → 直接报错，不许悄悄用错或留空跑过。
- **Q4: 多 provider 时怎么区分谁是谁？** → A（课件「坑一」）: 维护一张 **provider name → ChatModel 显式映射表**，按 name 取模型；禁止靠「扫描容器里所有 ChatModel」区分（Bean 类型相同、name 未必对得上）。
- **Q5: 要不要让 Spring AI 自动执行工具？** → A（课件「坑二」/宪法 IV）: **必须关闭** Spring AI 的自动工具执行；Provider 只把工具 schema 翻译发给模型，工具是否真正执行由 OryxOS 自己后续节的循环+执行器掌控。本节的 `chat` 调用请求里「自动执行」开关必须为 `false`。
- **Q6: 调用失败要不要审计？** → A（课件「第三步/第四步」）: LLM 调用**成功和失败都要**落 `llm_calls`：成功 `success=true`、失败 `success=false`+`error_message` 记原因。只记成功不记失败 = 一次真实事故在系统里没痕迹，属易漏缺陷。
- **Q7: 配置里的 API key 怎么处理？** → A（课件「第一步」/宪法工程约束）: 全局层 YAML 里 `${ENV_VAR}` 占位，运行时从环境变量解析；代码与配置文件里**不得出现真实 key**。
- **Q8: 建表用 Hibernate 自动迁移吗？** → A（宪法工程约束/课件「第四步」）: 否。SQLite 的 `ALTER TABLE` 很弱，`hibernate.ddl-auto=update` 只能首次建表用；本节 `llm_calls` 表用**手工建表脚本**，审计仓储测试里**执行该脚本**建表，不靠 Hibernate 自动建。

### Resolve（在 implement 前需用户知悉的 2 项，已在 Step 5 停点对账）

- R1: 课件的 `chat(sessionId, Profile, Prompt)` 形参 `Prompt`、以及工具适配器输入 `OryxTool.getInputSchema()` 在课件「本节交付物」清单内未单列类型，但课件正文与验收 harness 均引用它们。本特性据此**引入最小可用类型**：OryxOS 自有 `Prompt`（消息列表 + 本轮可用工具清单）、`OryxTool`（工具名 + 描述 + `getInputSchema()`）——皆为只承载本节所需成员的最小定义；后续节（ReAct/Tool 体系）会在此基础上扩展，届时的接口演进由对应节课件裁定（预见为 §17/§20 的改造点）。
- R2: 课件「验收 harness」列出 `ProviderSmokeIT` 为集成冒烟（真调一次模型），运行依赖真 API key；本特性按课件将其打 `@Tag("integration")`、日常 `mvn test` 与 CI 跳过，手动 `mvn test -Dgroups=integration` 跑——需在根 pom 的 `maven-surefire-plugin` 配 `excludedGroups=integration` 才能落地此「两档跑法」，这是课件测试策略句的直接要求、随本节一并建立。

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Provider 薄适配层：按名路由一次 LLM 调用（Priority: P1）

ReAct 循环（后续节）每转一圈都要通过 Provider 调一次大模型。上层把「这次用哪个 provider 的哪个 model」写在一份配置里、把「要发给模型的内容」递进来；Provider 隐藏各家模型协议差异，挑对模型发出去、把回话原样拿回。换模型只改配置，上层一行不动。

**Why this priority**: Provider 是把「大脑（LLM）」接进系统的工程封装，是 OryxOS 五大核心能力之首、后续 ReAct 循环的地基。本节交付的「能稳定调通一次」是整个系统跑起来的前提。

**Independent Test**: 配置一个真实可用的 provider（key 走环境变量），按名路由发起一次调用，拿到非空响应且 `llm_calls` 多一条 `success=true` 记录（真模型需手动跑集成冒烟；日常由 mock 单测覆盖路由与不开自动执行）。

**Acceptance Scenarios**:

1. **Given** 全局层声明了多家 provider、各自绑定显式的 provider name，**When** 上层以某个 provider name 发起调用，**Then** 请求被路由到该 name 对应的那一家，其余 provider 一次都没被碰（不串台）。
2. **Given** 上层以一个不存在的 provider name 发起调用，**When** 路由查表，**Then** 立刻报出清晰错误（不悄悄用错、不留空跑过去）。
3. **Given** 一次带工具 schema 的调用，**When** 请求发给模型，**Then** 翻译过的 schema 随请求带上，且请求里「自动执行工具」开关为关闭。
4. **Given** 模型调用成功，**When** 调用返回，**Then** 留一条审计记录（所属会话、provider、model、token 用量、耗时、success=true）。
5. **Given** 模型调用失败（超时/限流/模型报错），**When** 异常被捕获，**Then** 先留一条审计记录（success=false + 原因），再把异常继续上抛给上层；这次事故在系统里有痕迹。
6. **Given** 全局层以 `${ENV}` 形式声明 provider 凭证，**When** 加载/启动，**Then** 真实 key 从环境变量解析、在代码与配置文件里搜不到明文 key。

---

### User Story 2 - Profile 解析加载（Priority: P2）

组织 OryxOS 第一个消费 Profile 的能力。把一份 YAML 配置变成运行时可用的配置对象并按名索引，供上层按 name 拿到「这次用哪家 provider、哪个 model、什么温度」。

**Why this priority**: Profile 是 Provider 路由与后续各能力共享的配置载体；没有它，Provider 无从「按名取模型」。它是 P1 路由的输入侧前置。

**Independent Test**: 在 `.oryxos/profiles/` 放若干 YAML（含合法、引用不存在 provider 的、坏文件、带 `${ENV}` 占位的），加载后合法的全部按名可查、坏的不阻断其余、引用不存在的给清晰报错。

**Acceptance Scenarios**:

1. **Given** 一份字段齐全的合法 Profile YAML，**When** 启动扫描加载，**Then** 解析出的对象各字段一一对齐 YAML、按 name 可查。
2. **Given** 一份 Profile 的 `provider.name` 在全局层找不到同名项，**When** 加载校验，**Then** 该 Profile 给出清晰的校验错误（指向具体文件与缺失的 provider name）。
3. **Given** `.oryxos/profiles/` 里混入一份无法解析的坏文件，**When** 扫描加载，**Then** 该坏文件记错误日志、**不阻断**其余合法 Profile 的加载。
4. **Given** Profile 的某字段值写成 `${ENV_VAR}` 占位，**When** 解析，**Then** 该字段被从环境变量解析为实际值（`${ENV}` 占位从环境变量解析）。

---

## Edge Cases

- Profile YAML 文件为空或顶层非对象：记错误日志、不阻断其余加载。
- 同名 Profile 重复出现（两个文件 name 相同）：明确报冲突（本节按启动扫单条注册路径处理；运行时 `register()` 路径留 29 节补，本节不做）。
- 全局层某 provider 的 `api-key` 环境变量缺失：按宪法工程约束给清晰报错，而不是推迟到首次调用才报。
- 一次调用耗时极长/卡住：本节不做超时与熔断（见「明确不做」），由上层处理。
- 模型返回里带「想调某工具」的请求：Provider 原样交回上层，本节不解析也不执行。

## Requirements *(mandatory)*

### Functional Requirements

**Provider 路由与调用**

- **FR-001**: 系统必须维护一份「provider name → 对应大模型」的**显式映射**，按 provider name 取模型；不得靠「扫描容器里所有同类型模型」区分多 provider。
- **FR-002**: 上层以 provider name 发起一次调用时，请求必须被路由到该 name 对应的那家；其余 provider 不得被碰。
- **FR-003**: 上层以不存在的 provider name 发起调用时，必须立刻报出清晰错误（不悄悄用错、不留空跑过）。
- **FR-004**: 一次带工具 schema 的调用，翻译过的 schema 必须随请求带上；请求里「自动执行工具」开关必须为关闭（禁用 Spring AI 自动工具执行，执行权由 OryxOS 自行掌控）。
- **FR-005**: 工具格式适配**只翻译、不执行**：把工具参数说明翻译成模型看得懂的格式，产物里不含任何执行逻辑。

**审计（llm_calls）**

- **FR-006**: 每次大模型调用都必须留 `llm_calls` 记录，至少含：所属会话、provider、model、token 用量、耗时、成功/失败、失败原因。
- **FR-007**: 调用**成功**记 `success=true`；调用**失败**（超时/限流/模型报错）记 `success=false` + `error_message`，并把异常继续上抛，**不得**只记成功不记失败。
- **FR-008**: `llm_calls` 表必须含 `success` 与 `error_message` 两列，由手工建表脚本创建（不依赖 Hibernate 自动迁移）。

**Profile 解析**

- **FR-009**: 启动时必须扫 `.oryxos/profiles/` 下所有 YAML，解析成承载全部字段（name/description/identity/provider(name/model/temperature)/tools/skills/mcp_servers/channels/notify_channels/schedules/settings）的配置对象。
- **FR-010**: Profile 必须按 name 进内存索引、可按 name 查找。
- **FR-011**: Profile 加载必须做合法性校验：本节至少校验「`provider.name` 能在全局层找到同名项」，找不到给清晰报错。
- **FR-012**: 解析失败的坏 Profile 必须记错误日志、**不阻断**其余合法 Profile 的加载。
- **FR-013**: Profile YAML 中的 `${ENV}` 占位必须从环境变量解析为实际值。
- **FR-014**: Profile 里的 `provider` 段只声明「用哪个 provider/model/温度」；provider 的凭证（API key）只存在于全局层、Profile 里不含明文凭证。

**配置与密钥**

- **FR-015**: 全局层（`application.yaml`）声明本实例接了哪些 provider、每家凭证从哪个环境变量读（`${ENV}` 占位）。
- **FR-016**: 代码与配置文件里**不得出现真实 API key**（`grep` 明文 key 前缀搜不到）。
- **FR-017**: 全局层只管「连接」（provider 存不存在、key 有没有）；Profile 层管「调用参数」（用哪个 model、什么温度）；两层职责不混。

### Non-Functional Requirements

- **NFR-001（技术栈）**: JDK 21 + Spring Boot 3.x + Spring AI Alibaba（锁定 BOM 里的 connector）；SQLite + Spring Data JPA；Picocli 不在本节；YAML 解析用 SnakeYAML。
- **NFR-002（依赖审慎）**: 用到 provider 的 connector（如 deepseek/dashscope/openai 兼容）前先以 `mvn dependency:tree` 确认在锁定 BOM 里能下载、能解析，不照教程名字假设一定可用（课件「坑三」）。
- **NFR-003（自动化分级）**: 路由/校验/审计/翻译均跑单测（mock 大模型，不花一分钱、不依赖网络）；真调一次的集成冒烟打 `@Tag("integration")`，日常与 CI 跳过，手动跑。
- **NFR-004（静态门禁）**: `mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD/Spotless/Checkstyle）为实现完成定义；避开 P3C/ASM 解析不了的 Java 18+ 语法形态。
- **NFR-005（接口中立）**: 新增对外接口签名里不出现某一档实现特有的词；审计仓储、沙箱等接口先保留中立（本节只落 llm_calls）。

### Open Questions

- OQ1（已在 Clarification R1 记录，停点确认）: 课件默认依赖的最小类型 `Prompt` / `OryxTool` 本节引入的最小形态是否可接受（后续节扩展）。
- OQ2（已在 Clarification R2 记录，停点确认）: 为落地「两档跑法」需在根 pom surefire 配 `excludedGroups=integration`，随本节建立是否可接受。

### Dependencies & Assumptions

- 本节为逐节开发流程第一节代码课，**无前序代码课交付物**依赖（Step 1.2 依赖检查：前序无）。
- 外部依赖：项目已锁定 Spring AI BOM（1.1.8）+ Spring AI Alibaba BOM（1.1.2.3）+ Spring Boot 3.5.16 + sqlite-jdbc 3.49.1.0 + hibernate-community-dialects；deepseek / openai / dashscope connector 在锁定 BOM 内经既有依赖树核实可解析（Step 1.1 已 grep 既有编译产物确认 `ChatModel.call(Prompt)→ChatResponse`、`response.getMetadata().getUsage()`、`ToolCallingChatOptions.builder().toolCallbacks(..).internalToolExecutionEnabled(false)`、`ToolCallback`/`ToolDefinition`/`ToolMetadata` 均 1.1.8 可用）。
- 假设：核心阶段内网、单实例；本节不感知未来多 provider fallback / 熔断 / 成本看板。