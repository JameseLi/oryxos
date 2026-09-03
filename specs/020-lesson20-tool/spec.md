# Feature Specification: 第 20 节 Tool 体系（统一抽象、三档接入与内置工具）

**Feature Branch**: `020-lesson20-tool`

**Created**: 2026-09-02

**Status**: Draft

**Input**: 课件 `docs/class/第20节：Tool 体系 原理解析、实现与代码讲解.md`--本节是 OryxOS 的"让 Agent 能动手"实现课（统一工具抽象 + 统一注册表 + 内置工具第一批 + MCP 方式二接入 + `@Tool` 方式三注解注册），来源实证以课件为准；技术方案对位 `TechnicalSolution` §6.1~6.6（Tool 体系）、§6.7（Sandbox 前向接口）、§8.2（Profile `tools` 字段消费）。

**Source-of-truth note**: 延续 16~19 节裁定的逐节开发形态--**课件为唯一事实源**，实现形态逐字对齐课件骨架（`OryxTool` 四能力契约、`ToolResult` 四维度含"是否可重试"、`McpClientService` 失联 WARN 跳过骨架、`http_get` 示例的"第一行过安全校验"硬规矩、harness 断言逻辑与方法名字面量如"某个MCP server失联 不能拖垮启动和其他工具"）；与课件示意代码的既有差异按前序节裁定继续沿用（包根 `com.oryxos` 非 `io.oryxos`；`OryxTool` 为 16/17 节已前向交付的 core 形态--四方法含 `getInputSchema`、记录式命名 `name()`/`description()`、`execute(String)` 接收 JSON 原文，课件「动手前先检查」条款核对**通过**，本节不重定义接口、不改前序方法签名；`ToolResult` 在 17 节形态上演进"是否可重试"维度--17 节 javadoc 已点名由本节演进，属交付物既定改造点）。18 节 `EmptyToolRegistry` 过渡 Bean 由真注册表替换（18 节 javadoc 已预告"第 20 节由 ToolRegistry 真实现替换，届时本类删除此内嵌类"）。

---

## Clarifications

### Session 2026-09-02（逐条采自课件「一、二、三、六」+ 技术方案 §6，无外部答案外推）

- **Q1: `OryxTool` 本节是新造还是沿用？** -> A（课件「二」动手前检查 + 16/17 节交付记录）: 16 节前向交付了 schema 翻译三方法、17 节增补 `execute`，四方法已对齐技术方案 §6.1 终局形态；课件「动手前先检查 `getInputSchema()`」条款核对**通过**。本节沿用不重定义（软门禁②也禁止擅改前序字面量）。
- **Q2: `execute` 入参形态（`JsonNode` vs `String`）？** -> A（17 节交付形态）: 沿用 `execute(String)`（接收 JSON 原文）；课件代码标注「是示意」，不构成改动前序签名的改造点授权。
- **Q3: `ToolResult` 的"是否可重试"怎么演进？** -> A（17 节 javadoc 点名 + 课件「三」MCP 示例 `failure(msg, true)`）: record 增补 `retryable` 分量；既有 `ok`/`failure` 工厂保留并新增带 `retryable` 的重载，缺省 `failure` 为不可重试（如白名单拦截），MCP 适配器显式标记可重试（外部系统瞬时失败值得再调一次）；`toModelText` 零改动。
- **Q4: 三档接入的优先序？** -> A（课件「二」第二）: 能用方式一就不用方式二，能用方式二就不用方式三；方式一最优雅——业务方只描述"想干什么"，工具组合交给 LLM。
- **Q5: 安全校验本节的形态？** -> A（课件「二」第三 +「三」`http_get` 示例 + 技术方案 §6.7）: 中立前向接口（`enforce(SandboxAction)`、动作类型四值 FILE_READ/FILE_WRITE/SHELL_COMMAND/HTTP_REQUEST）+ 校验失败异常 + 一档**默认放行**临时实现；工具执行方法第一行过调用位（硬规矩）；本体 `WhitelistSandbox` 归 23/24 节替换。
- **Q6: MCP server 失联怎么处理？** -> A（课件「三」`McpClientService` 骨架注释逐字）: "外部依赖失联不拖垮自身启动——只 WARN，OryxOS 照常起"；失联 server 的工具一个不注册，其余照常。
- **Q7: `notify` 的注册时点？** -> A（19 节 Resolve R1 预告 + 本节交付物"NotifyTools（19 节）在此完成注册"）: 类本体 19 节已交付，本节随注解扫描收编注册；enforce 顺序断言仍归 24 节（19 节 R2 裁定不变）。
- **Q8: `save_memory`/`recall_memory` 本节做吗？** -> A（技术方案 §6.2"归 Memory 模块，但作为内置 Tool 注册"+ 本节交付物清单未列）: 不做，22 节 Memory 模块交付后注册；本期落注册表的是 6 内置 + `notify` + 扩展 5 = 12 个。
- **Q9: Profile `tools` 未声明时过滤语义？** -> A（17 节 `PromptBuilder.availableTools` 既有实现）: 白名单式 fail-closed——未声明即空集、声明即恰好等于（不多不少）；本节在真注册表上验证该语义，消费端零改动。
- **Q10: 扩展五工具为什么进内置清单？** -> A（课件「六」6.1）: 判断标准"基础工具而非业务能力"——任何稍复杂任务都会用到，对齐 Claude Code/Cursor/OpenAI Assistants 公约数；不是丢给 Plugin Tool 每家各写一遍。
- **Q11: `ask_user` 为什么抽"能否交互"抽象？** -> A（课件 6.3）: 它依赖"当前渠道能不能跟人交互"——CLI 可读终端，定时任务/Web 无状态调用没人等着回答；接口先行，两档实现（终端读行 / 无人值守明确报错），绝不静默卡住。
- **Q12: `web_search` 过不过白名单、用哪个搜索源？** -> A（课件 6.4）: 走网络所以**第一件事过 HTTP 域名校验调用位**，不能因为叫"搜索"就绕过治理；搜索源抽中立接口，核心阶段挂一档免 key 实现（DuckDuckGo Instant Answer API），换 Google/Bing/Tavily 只加实现类；测试中 mock 搜索源不碰网，真搜属人工项。

### Resolve（在 implement 前需用户知悉，Step 5 停点对账）

- **R1（`ToolResult` 演进 = 唯一前序类型改动点）**: `ToolResult(boolean success, String output, String errorMessage)` 增补 `retryable` 分量（17 节 javadoc 点名的既定改造点，唯二触碰前序公共类型处之一，另一处为 R2 过渡 Bean 替换）；`ok`/`failure` 工厂保留、新增带 `retryable` 重载；16~19 节全部既有调用方（`ToolExecutor`/`NotifyTools`/`WebhookNotifyAdapter` 等）回归绿作证。
- **R2（注册表真实现替换过渡 Bean）**: 真注册表实现落 oryxos-tool（模块落位表）；`EngineConfiguration` 装配改为指向真实现、内嵌 `EmptyToolRegistry` 删除（18 节 javadoc 预告"届时本类删除此内嵌类"）。实现类命名 plan 定（接口已在 core，实现类命名不与课件字面量冲突）。
- **R3（`AnnotatedToolAdapter` 扫描管道）**: 交付物点名类；扫描 Spring 容器 `@Tool` 注解方法 → 包装注册；参数 schema 用 Spring AI 注解 schema 生成（原则四"允许的一半"）；**禁用 Spring AI 自动 tool 执行**（原则四红线，配置断言守住，工具调度唯一收口在 OryxOS 执行器）。
- **R4（MCP 客户端 SDK 核实）**: 固定技术栈句硬要求——plan 阶段跑 `mvn dependency:tree` 核实锁定 BOM 中 MCP 客户端依赖存在并在 plan 明列；核实不到即软门禁停下报告。
- **R5（Sandbox 前向接口五件）**: `Sandbox`/`SandboxAction`/`ActionType`/`SandboxViolationException`/`PermissiveSandbox`（默认放行临时装配）落 oryxos-tool sandbox 包（落位表 24 节行同包）；接口签名不出现"白名单/容器/VM"字样（§6.7 中立性自查）；24 节以 `WhitelistSandbox` 替换装配、不改任何调用方。
- **R6（扩展五工具测试归属）**: `edit_file`/`grep`/`glob` 守点并入 `FileToolsTest`；`ask_user`/`web_search` 随工具类交付单测（测试类命名 plan 定）；`OryxToolContractTest` 参数化遍历自动纳入全部新工具（课件 harness"新工具自动纳入契约检查"语义）。
- **R7（`mcp_servers.yaml` 键核对）**: 18 节 init 模板已存在，implement 时逐键对照课件交付物（`name`/`transport`/`command`/`env`），不一致即软门禁停下报告，不擅改模板。

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 统一抽象与统一注册表：三源收编、来源无感知（Priority: P1）

内置工具、业务方 Java 注解方法、外部 MCP server 暴露的工具，三种来源五花八门；如果执行循环分来源区别对待，代码会很快乱掉。本故事先立统一抽象：任何来源的工具都包装成同一种对象注册进统一注册表，ReAct 循环只跟统一抽象打交道、完全不感知来源。每个工具都必须具备完整契约（工具名、自然语言描述、参数结构说明、执行入口），任何一个工具漏实现参数结构说明，Provider 翻译 Function Calling 时直接卡死--契约检查参数化遍历注册表全部工具，新工具自动纳入、漏了立刻红。

**Why this priority**: 课件「二」第一："这个抽象是整个 Tool 体系的地基，得第一个定下来。"后续所有工具（内置/MCP/注解）都挂在它上面。

**Independent Test**: `OryxToolContractTest`（参数化遍历注册表，三件套非空）+ `ToolRegistryTest`（三种来源工具都以统一身份注册进同一注册表）。

**Acceptance Scenarios**:

1. **Given** 注册表里注册的每一个工具（无论来源），**When** 参数化契约测试遍历，**Then** 工具名、描述、参数结构说明三件套全部非空。
2. **Given** 内置工具、注解 Bean 工具、MCP 工具三种来源，**When** 启动装配完成，**Then** 全部以统一抽象身份出现在同一注册表里，可按名查找、可全量列出。
3. **Given** 执行循环（ReAct/ToolExecutor）持有统一注册表，**When** 调用任意工具，**Then** 循环代码不出现任何来源判断分支（17 节 `ToolExecutor`/`PromptBuilder` 零改动即是证据）。
4. **Given** Agent Profile 声明了 `tools` 工具名单，**When** 组装可用工具集，**Then** 过滤结果**恰好等于**声明列表--多一个（没过滤干净）和少一个（过滤过头）都是错；未声明 `tools` 时可用工具为空集（17 节既有白名单语义，fail-closed）。

### User Story 2 - 内置工具第一批：文件三件 + shell + HTTP 两件（Priority: P1）

Agent 要真正能干事，先得有"读写文件、跑命令、调 API"的手。本故事交付六个内置工具：`read_file`/`write_file`/`list_dir`（文件三件）、`shell`（跑命令带超时）、`http_get`/`http_post`（发请求）。每个工具两条铁律：正常能跑通；越界会被拦--文件操作过路径校验、Shell 过命令校验、HTTP 过域名校验，执行前统一过一层安全校验，校验不过直接拦下、请求根本发不出去（课件「三」`http_get` 示例："方法体里第一件事就是过安全校验"，这是硬规矩）。

**Why this priority**: 这六个是"最短链路"的主体（课件「一」）；`http_get` 跑通即落地 Demo 一对话版里"真的去查天气"的动作。

**Independent Test**: `FileToolsTest` / `ShellToolsTest` / `HttpToolsTest`--各自"正常能跑通 + 越界会被拦"两条（课件正文 `http_get` 两个用例即模板）。

**Acceptance Scenarios**:

1. **Given** 白名单内的路径/命令/域名，**When** 调用对应工具，**Then** 执行成功、返回真实结果（`http_get` 取回响应体、`shell` 返回命令输出、文件三件正确读写列）。
2. **Given** 白名单外的路径/命令/域名（如 `https://evil.example.com`），**When** 调用对应工具，**Then** 被安全校验拦下、抛校验异常、真实 IO 根本不发生。
3. **Given** 任何一次工具执行（成功或失败），**When** 审视执行链路，**Then** 走 17 节 `ToolExecutor` 既有审计路径写 `tool_invocations`，零新增审计逻辑。

### User Story 3 - MCP 方式二接入：外部工具协议化收编（Priority: P1）

业务方用任何语言写 MCP server 暴露工具，OryxOS 作为 MCP Client 在启动时连接所有配置的外部 server（配置在 `.oryxos/mcp_servers.yaml`：`name`/`transport`/`command`/`env`），调 `tools/list` 拿工具列表，把每个 MCP 工具包装成统一抽象注册进注册表。执行时通过 MCP 协议原样转发参数、结果包装成统一结果返回。**外部依赖的可用性不是自己的可用性**：某个 server 失联只记 WARN 跳过它的工具，其余 server 照常注册、OryxOS 启动不炸（31 节日报 Agent 硬依赖此能力）。

**Why this priority**: 零代码档（方式一）复用的就是 MCP server；方式二收编不通，方式一与 31 节 Demo 全部落空。

**Independent Test**: `McpToolAdapterTest` / `McpClientServiceTest`（mock MCP 客户端，不碰网）：listTools 返回的工具被包装注册；execute 转发参数原样、结果包成统一结果；连接失败只 WARN、其余工具照常注册、启动不炸。

**Acceptance Scenarios**:

1. **Given** 配置文件里声明了一个可连接的 MCP server，**When** 启动连接完成，**Then** 该 server `tools/list` 返回的每个工具都以统一抽象身份注册进注册表，名称/描述/参数说明直接映射 `tools/list` 返回。
2. **Given** 模型决定调用某个 MCP 工具并给出 JSON 入参，**When** 工具执行，**Then** 参数原样经 MCP 协议转发给对应 server，结果成功时包成成功统一结果、失败时包成**可重试**的失败统一结果。
3. **Given** 某个 MCP server 连接失败（如连接拒绝），**When** 启动继续，**Then** 只记 WARN、该 server 的工具一个都不注册，**其余 server 的工具照常注册**，OryxOS 正常启动不抛异常。
4. **Given** `.oryxos/mcp_servers.yaml`，**When** 查看其结构，**Then** 支持 `name`/`transport`/`command`/`env` 四个键（18 节 `init` 已生成模板，本节消费；键名如有出入按软门禁停下报告）。

### User Story 4 - 方式三注解注册 + `notify` 收编（Priority: P1）

Java 业务方写一个带 `@Tool` 注解的方法挂进工程，启动时自动扫描、生成参数结构说明、包装成统一抽象注册进注册表（写法与内置工具完全一致，进程内直接调用，不走 MCP 协议）。注册管道用 Spring AI 的注解 schema 生成能力（原则四"允许的一半"），但**绝不启用 Spring AI 的自动 tool 执行**--工具调度与执行完全由 OryxOS 自己的 `ToolExecutor` 控制，否则 tool 会被调两次。同时完成 19 节遗留接线：`NotifyTools` 在本节随注解扫描完成注册收编，模型真正可调用。

**Why this priority**: 三档接入的最后一档管道；`notify` 收编是 19 节 `NotifyToolsTest` 第二批守点里"注册"依赖的兑现（19 节 spec Resolve R1 预告"注册动作归 20 节"）。

**Independent Test**: `ToolRegistryTest`（注解来源工具与 `notify` 均以统一身份注册进注册表、契约三件套自动纳入）；Spring AI 自动执行禁用由配置断言守住（原则四）。

**Acceptance Scenarios**:

1. **Given** 一个带 `@Tool` 注解的 Spring Bean 方法，**When** 启动装配完成，**Then** 该方法以注解名注册进注册表，参数结构说明由注解自动生成，调用时进程内直接执行 Java 方法。
2. **Given** 19 节交付的 `NotifyTools`（带注解的 Spring Bean），**When** 启动装配完成，**Then** `notify` 出现在注册表中、可通过契约三件套检查，19 节"注册动作归 20 节"的预留兑现。
3. **Given** 一次含工具调用的对话，**When** 审视 LLM 调用与工具执行次数，**Then** 每个工具调用只被执行一次（由 `ToolExecutor` 执行），不存在 Spring AI 自动执行路径（原则四红线）。

### User Story 5 - 扩展交付：五个基础工具补齐业界水准（Priority: P2）

对着主流 Agent 产品的公约数一比，规划的最短链路还缺五样"日常高频、缺了就明显笨手笨脚"的基础工具：`edit_file`（把文件里**唯一出现**的旧文本换成新文本；找不到报错、出现多次也报错、报错时文件一个字不动）；`grep`（按正则搜内容返回 `文件:行号:内容`；读二进制/非 UTF-8 文件跳过而不是中断；返回条数有上限、超限截断并注明）；`glob`（按通配找文件路径，同样的条数上限）；`ask_user`（中途向用户提问/请求确认；渠道无人值守时**明确报错绝不静默卡住**，经"能否交互"的中立抽象决定行为）；`web_search`（搜索引擎检索；走网络所以和 `http_get` 一样第一件事过 HTTP 域名校验，不能因为叫"搜索"就绕过治理；搜索源经中立抽象、核心阶段挂一档免 key 实现）。五个工具全部复用既有统一抽象、注解管道与安全校验调用位--加工具的成本恒定在"写一个方法/一个类"。

**Why this priority**: 对齐业界的基础工具面（课件「六」），但不阻塞 US1~US4 的地基地位；`web_search` 是日报/调研类 Agent"上网查"的第一入口。

**Independent Test**: 三个文件工具守点并入 `FileToolsTest`；`ask_user`/`web_search` 各随工具类交付单测（测试类命名 plan 定）；`OryxToolContractTest` 参数化遍历自动把新工具纳入契约检查。

**Acceptance Scenarios**:

1. **Given** 文件中一段唯一出现的旧文本，**When** `edit_file` 替换，**Then** 仅该处被替换；旧文本不存在或出现多次时**报错且文件零改动**。
2. **Given** 目录下含二进制文件的搜索，**When** `grep` 执行，**Then** 二进制/非 UTF-8 文件被跳过、不中断整次搜索；命中以 `文件:行号:内容` 形式返回；结果超过上限时截断并注明。
3. **Given** `**/*.yaml` 类通配模式，**When** `glob` 执行，**Then** 返回匹配的文件路径列表，超上限截断并注明。
4. **Given** CLI 渠道有人在场，**When** `ask_user` 提问，**Then** 从终端读一行回答带回；**Given** 定时任务/Web 等无人值守渠道，**When** `ask_user` 提问，**Then** 明确报错"不支持交互"，绝不静默卡住。
5. **Given** 一次 `web_search` 调用，**When** 审视执行序列，**Then** 域名白名单校验位在真实网络请求之前（与 `http_get` 同一调用位语义，本体 24 节接线）；搜索结果渲染成模型好读的文本。

---

## FR Requirements

### Must

- **FR1 统一工具抽象契约**: 任何来源的工具实现同一接口，约定四能力：工具名、自然语言描述、参数结构说明（JSON Schema）、执行入口（接收 JSON 输入返回统一结果）；接口签名不出现任何来源特有词汇（内置/MCP/注解）（课件「三」+ 技术方案 §6.1；16/17 节已前向交付，本节核对沿用、不重定义）。
- **FR2 统一结果四维度**: 统一结果含成功标识、结果内容、错误信息、**是否可重试**四个维度；"是否可重试"在 17 节 `ToolResult` 形态上演进（17 节 javadoc 点名的既定改造点，本节唯二触碰前序公共类型处之一）；既有 `ok`/`failure` 工厂与 `toModelText` 消费端零破坏。
- **FR3 统一注册表**: 三种来源的工具全部包装成统一抽象注册进同一注册表；按名查找/全量列出接口语义不变（17 节接口）；18 节空注册表过渡 Bean 由真实现替换。
- **FR4 注解扫描注册（方式三）**: Spring 容器内带 `@Tool` 注解的方法，启动时自动扫描、生成参数结构说明、包装注册；**不启用 Spring AI 自动 tool 执行**（原则四红线，工具调度与执行唯一收口在 OryxOS 自己的执行器）。
- **FR5 MCP 接入（方式二）**: 启动时读 `.oryxos/mcp_servers.yaml`（`name`/`transport`/`command`/`env`），连接所有配置的 server，调 `tools/list` 把每个工具包装注册；执行时经 MCP 协议（JSON-RPC）转发、参数原样、结果包成统一结果（失败标记可重试）。
- **FR6 MCP 失联隔离**: 单个 server 连接失败只 WARN 并跳过它的全部工具，其余 server 照常注册、自身启动不失败--"外部依赖失联不拖垮自身启动"（课件 harness 守点）。
- **FR7 内置工具第一批六个**: `read_file`/`write_file`/`list_dir`/`shell`/`http_get`/`http_post`；每个工具"正常能跑通 + 越界会被拦"两条都要有测试（课件 harness）。
- **FR8 安全校验调用位先行**: 提供 Sandbox 中立前向接口（动作类型：文件读/文件写/Shell 命令/HTTP 请求四值）+ 校验失败异常 + 一档"默认放行"临时实现（接口签名不出现"白名单"字样，经得起接口中立性自查）；所有涉外 IO 工具执行方法**第一行**过校验调用位，白名单本体与装配替换归 24 节（技术方案 §6.7，H4①）。
- **FR9 Profile 工具过滤（不多不少）**: 每个 Agent 按 Profile `tools` 字段过滤出可用工具子集，断言子集**恰好等于**声明列表；未声明即空集（17 节 `PromptBuilder.availableTools` 既有白名单语义，本节在真注册表上验证，消费端零改动）。
- **FR10 `notify` 收编注册**: 19 节交付的 `NotifyTools` 在本节随注解扫描完成注册，模型真正可调用（19 节 Resolve R1 预告的兑现）。
- **FR11 扩展五工具**: `edit_file`（唯一匹配替换，零/多处命中报错且文件不动）、`grep`（`文件:行号:内容`、二进制跳过、条数上限截断注明）、`glob`（通配找路径、同上限）、`ask_user`（交互能力中立抽象 + 终端实现/无人值守报错实现两档，绝不静默卡住）、`web_search`（搜索源中立抽象 + 一档免 key 实现，首行过 HTTP 校验调用位）--全部复用统一抽象与注解管道（课件「六」）。

### 明确不做（边界）

- **不做 Tool Policy**: 哪个 Agent 能用哪些工具的 allow/deny 规则归扩展阶段；本期用"白名单 + Profile 的 `tools` 字段限定"顶住（课件「三」+ 技术方案 §6.7 要点二）。
- **不做按需加载**: 工具太多时的按需加载归扩展阶段（课件「三」）。
- **不把自己变成 MCP server 对外暴露**: OryxOS 本期只做 MCP Client（课件「三」）。
- **不做容器级沙箱**: 白名单本体（`WhitelistSandbox` 及配置键）归 23/24 节；本节只交付中立前向接口 + 默认放行临时实现（技术方案 §6.7）。
- **不做一次响应多工具并行调**: 归扩展阶段（课件「三」）。
- **不交付 `MemoryTools`**: `save_memory`/`recall_memory` 归 Memory 模块（22 节）交付后注册；本期"九个内置工具"中实际落注册表的是 6 + `notify` + 扩展 5 = 12 个（技术方案 §6.2"归 Memory 模块，但作为内置 Tool 注册"+ 本节交付物清单未列）。
- **不做方式一（零代码档）的新机制**: 方式一 = Agent 目录 + 复用 MCP server，其承载件（`AGENT.md` 解析、`mcp_servers.yaml`、`skills/`）分别归 17 节 `ContextLoader`、本节方式二、后续节；本节不新增方式一专属代码（技术方案 §6.3）。
- **不改前序节公共接口**: 除 FR2 既定演进点（`ToolResult` 增补维度）与 18 节 javadoc 预告的过渡 Bean 替换外，`OryxTool`/`ToolExecutor`/`PromptBuilder`/`Profile`/`NotifyTools` 等零改动。

## Success Criteria

可自动化部分由课件"验收 harness"测试套件承载，`mvn test` 全绿即通过；关键回归点：

- `OryxToolContractTest`：**参数化遍历注册表每个工具**，工具名/描述/参数结构说明三件套非空--任何一个工具漏实现参数说明立刻红（课件 harness 最值钱测试之一，新工具自动纳入）。
- `ToolRegistryTest`：三种来源工具都以统一身份注册进来；按 Profile `tools` 字段过滤后子集**恰好等于**声明列表、不多不少。
- `FileToolsTest` / `ShellToolsTest` / `HttpToolsTest`：各自"正常能跑通 + 越界会被拦"两条（正文 `http_get` 两个用例即模板）。
- `McpToolAdapterTest` / `McpClientServiceTest`：mock MCP 客户端--listTools 返回的工具被包装注册；execute 转发参数原样、结果包成统一结果；**某个 MCP server 失联不能拖垮启动和其他工具**（连接失败只 WARN、好 server 照常注册、启动不炸）。
- 扩展五工具守点：`edit_file` 唯一匹配语义（零/多处命中报错文件不动）、`grep`/`glob` 上限与跳过语义、`ask_user` 无人值守明确报错、`web_search` 校验位先于网络请求--并入上述测试类或随工具类新设（plan 定）。
- 前序节全部测试回归绿（16/17/18/19 节跨节契约证据；`ToolResult` 演进与过渡 Bean 替换的兼容性由回归证明）。

人工项见课件"五、做完怎么验"：

- 方式一真跑一次：写一份 SKILL.md + 连一个真实 MCP server，Agent 能读懂意图并调用外部工具完成任务（依赖真模型和真 server，测不了）。
- 方式三真跑一次：`@Tool` 示例工具在 `tool list` 里可见、Agent 能调通。
- 每次工具调用都写进 `tool_invocations`（17 节 ToolExecutorTest 已覆盖，跑真链路时顺带目检一眼）。
- `oryxos tool list` 能看到当前注册了哪些工具（18 节命令在真注册表替换后自然生效，冒烟确认即可）。

## 依赖与假设

- **前序节交付物（H0 已核对存在）**：16 节 `Profile`（含 `tools` 字段，本节只消费）/`ProfileLoader`/`ProfileRegistry`；17 节 `OryxTool`（四方法含 `getInputSchema`，课件「动手前先检查」条款核对通过）/`ToolResult`/`ToolRegistry` 接口/`ToolExecutor`/`PromptBuilder`（含 `availableTools` 过滤）；18 节 `EmptyToolRegistry` 过渡 Bean（本节替换，18 节 javadoc 预告）/`ToolListCommand`（消费注册表，真实现替换后自然生效）/`init` 的 `mcp_servers.yaml` 模板（本节消费）；19 节 `NotifyTools`（本节注册收编）及其 Resolve R4/R5 先例（spring-web/spring-ai-model 显式依赖、mockwebserver 测试依赖）。
- **后续节依赖（本节预留、不在本节交付）**：23/24 节 Sandbox 本体（`WhitelistSandbox` + 配置键 + enforce 顺序断言，替换本节默认放行临时实现）；22 节 `MemoryTools` 注册；25 节定时模块与 31 节 Demo 是 MCP 接入与 `notify` 的真正消费场景。
- **假设**：`mcp_servers.yaml` 键名以 18 节 init 模板与课件交付物（`name`/`transport`/`command`/`env`）为准，implement 时逐键核对，不一致即触发软门禁停下报告；MCP 客户端 SDK 选型在动手前跑 `mvn dependency:tree` 核实锁定 BOM 中存在，核实不到按软门禁停（固定技术栈句硬要求）。
- **外部依赖**：Spring Boot BOM；Spring AI BOM（`@Tool`/`@ToolParam` 注解 schema 生成，core 已依赖 spring-ai-model）；MCP 客户端 SDK（版本由 BOM 锁定，implement 前核实）。无网络依赖（MCP 连接在测试中 mock，不碰网）。
