---

description: "Task list for Provider 抽象 + ReAct Loop implementation"
---

# Tasks: Provider 抽象 + ReAct Loop（可调用工具的多轮对话）

**Input**: Design documents from `/specs/001-provider-react-loop/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md

**Tests**: plan.md 定义了单测策略（stub `ChatModel` / 假 `OryxTool`），故包含关键测试任务；E2E 走 quickstart 手工场景。

**Organization**: 按 spec.md 的 P1→P3 组织，每个 story 独立可测。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 不同文件、无未完成依赖，可并行
- **[Story]**: US1/US2/US3（仅 user story 阶段打标）
- 路径相对仓库根；包根：`com.oryxos`

## Path Conventions

- Maven 多模块：`<module>/src/main/java/com/oryxos/<domain>/...`
- 测试：`<module>/src/test/java/com/oryxos/<domain>/...`
- 配置：`oryxos-boot/src/main/resources/application.yaml`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 依赖对齐（research.md R1/R7：版本选型与冲突排查）

- [x] T001 升级父 pom.xml：Spring Boot 3.4.1→3.5.16，dependencyManagement 导入 spring-ai-bom 1.1.8 与 spring-ai-alibaba-bom 1.1.2.3，删除 TODO(US-1) 注释
- [x] T002 [P] oryxos-provider/pom.xml 添加 spring-ai-starter-model-deepseek、spring-ai-starter-model-openai、spring-ai-alibaba-starter-dashscope
- [x] T003 [P] oryxos-storage/pom.xml 添加 spring-boot-starter-data-jpa、org.xerial:sqlite-jdbc 3.49.1.0、org.hibernate.orm:hibernate-community-dialects（对齐 Boot 管理的 Hibernate 6.6.x）
- [x] T004 [P] 补齐模块间依赖：provider/tool/storage/channel-cli → core；cli → core+channel-cli；boot → 全部（各模块 pom.xml）
- [x] T005 运行 mvn dependency:tree 排查 starter 传递依赖冲突（research.md 遗留风险），随后 mvn clean package 保持 9 模块全绿

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 存储底座 + 核心抽象，所有 story 的前置

**⚠️ CRITICAL**: 本阶段完成前不得开始任何 user story

- [x] T006 编写建表脚本 oryxos-storage/src/main/resources/scripts/sql/001-init.sql（sessions/session_messages/llm_calls/tool_invocations 四表，按 data-model.md）
- [x] T007 [P] 创建 SessionEntity 与 SessionMessageEntity（oryxos-storage/src/main/java/com/oryxos/storage/entity/，(session_id,seq) 唯一约束）
- [x] T008 [P] 创建 LlmCallEntity 与 ToolInvocationEntity（同包，只增不改审计语义）
- [x] T009 [P] 创建 SessionRepository、LlmCallRepository、ToolInvocationRepository（oryxos-storage/src/main/java/com/oryxos/storage/repository/）
- [x] T010 配置 oryxos-boot/src/main/resources/application.yaml：SQLite 数据源 + SQLiteDialect + spring.sql.init 指向 001-init.sql + ddl-auto=none + oryxos.react.{max-iterations:10,invocation-timeout-seconds:60} + oryxos.storage.db-path + oryxos.providers 骨架（contracts/cli-contract.md）
- [x] T011 [P] 创建核心抽象 OryxTool 接口与 ToolProgressListener 接口（oryxos-core/src/main/java/com/oryxos/core/tool/，签名中立无实现特定词）
- [x] T012 创建 AuditRecorder（oryxos-core/src/main/java/com/oryxos/core/audit/AuditRecorder.java，委托 T009 两仓库落库；写库失败不阻断主流程，但必须输出结构化 ERROR 日志（含调用标识与失败原因），quickstart 验收要求该日志计数=0 以闭环 SC-005；llm_calls 请求/响应快照按 data-model.md ≤16KB 截断策略执行）
- [x] T013 存储冒烟测试 oryxos-storage/src/test/java/com/oryxos/storage/StorageSmokeTest.java：上下文加载、四表建成、save/query 往返

**Checkpoint**: mvn test 全绿，SQLite 落库可用，进入 story 阶段

---

## Phase 3: User Story 1 - 多轮对话跑通 (Priority: P1) 🎯 MVP

**Goal**: CLI 启动 chat，与 Agent 多轮问答，上下文保持，会话持久化

**Independent Test**: quickstart 场景 A——3 轮以上上下文依赖问答正确，exit 后 sessions/session_messages 有记录

### Implementation for User Story 1

- [x] T014 [US1] 创建 ProviderProperties 配置类（oryxos-provider/src/main/java/com/oryxos/provider/ProviderProperties.java，oryxos.providers.\<name\>.{type,model,base-url,api-key-env}）
- [x] T015 [US1] 创建 ChatModelFactory（oryxos-provider/.../ChatModelFactory.java，先实现 deepseek 型：DeepSeekChatModel.builder()，apiKey 从 api-key-env 所指环境变量解析）
- [x] T016 [US1] 创建 ProviderRegistry 与 ProviderService（oryxos-provider/.../，程序化构造显式 Map\<name,ChatModel\>——宪法 IV 禁类型扫描；启动校验所有 api-key-env 缺失即 fail-fast 并列出缺失项 FR-013）
- [x] T017 [P] [US1] 创建 AgentLoader（oryxos-core/src/main/java/com/oryxos/core/agent/AgentLoader.java，SnakeYAML 解析 AGENT.md frontmatter→AgentDefinition；name/provider/tools 校验规则见 contracts/agent-definition-schema.md）
- [x] T018 [P] [US1] 创建 ContextLoader（oryxos-core/.../agent/ContextLoader.java，每次组装 prompt 热读 AGENT.md 正文+Bootstrap 文件，不缓存——宪法约束）
- [x] T019 [US1] 创建 SessionManager（oryxos-core/src/main/java/com/oryxos/core/session/SessionManager.java，建会话/追加消息/按 seq 读历史，委托 T007/T009）
- [x] T020 [US1] 创建 PromptBuilder（oryxos-core/src/main/java/com/oryxos/core/react/PromptBuilder.java，system=AGENT.md 正文+Bootstrap，messages=历史，本阶段工具清单为空）
- [x] T021 [US1] 创建 AgentService.process（oryxos-core/src/main/java/com/oryxos/core/agent/AgentService.java，单轮直调 ChatModel；virtual-thread 包 60s 超时 FR-010；空消息/32KB 校验 FR-003；llm_calls 审计经 T012）
- [x] T022 [P] [US1] 实现 InitCommand（oryxos-cli/src/main/java/com/oryxos/cli/InitCommand.java，幂等生成 .oryxos/ 工作区，产物清单见 contracts/cli-contract.md）
- [x] T023 [US1] 实现 OryxosCli 主入口与 ChatCommand（oryxos-cli/.../OryxosCli.java + ChatCommand.java，Picocli；boot 模块 CommandLineRunner 分发；--agent 必填 --session 可选）
- [x] T024 [US1] 实现 CliChannel REPL（oryxos-channel-cli/src/main/java/com/oryxos/channel/cli/CliChannel.java，BufferedReader 读行→AgentService→打印回答；exit/quit/Ctrl-C 退出；错误友好提示）

### Tests for User Story 1

- [x] T025 [P] [US1] 单测：AgentLoader frontmatter 解析/越界引用报错（oryxos-core/src/test/java/com/oryxos/core/agent/AgentLoaderTest.java）
- [x] T026 [P] [US1] 单测：PromptBuilder 组装与 AgentService 校验（空/超 32KB/stub ChatModel 超时）oryxos-core/src/test/java/com/oryxos/core/{react/PromptBuilderTest,agent/AgentServiceTest}.java
- [ ] T027 [US1] 手工验证 quickstart 场景 A（多轮上下文 + exit 后查库）

**Checkpoint**: US1 独立可演示——oryxos chat 多轮对话跑通

---

## Phase 4: User Story 2 - LLM 自主调用工具完成任务 (Priority: P2)

**Goal**: ReAct 循环闭环：模型请求工具→执行→回填→继续推理直到最终回答；一行式进度可见

**Independent Test**: quickstart 场景 B/C——天气提问一次闭环含真实数据；断网/不可达/上限三种异常均优雅结束

### Implementation for User Story 2

- [x] T028 [P] [US2] 创建 ToolRegistry（oryxos-tool/src/main/java/com/oryxos/tool/registry/ToolRegistry.java，名称→OryxTool；注册名校验供 AgentLoader 使用）
- [x] T029 [P] [US2] 创建 Sandbox 接口与 WhitelistSandbox（oryxos-tool/src/main/java/com/oryxos/tool/sandbox/，域名白名单应用层实现，无 SecurityManager）
- [x] T030 [US2] 实现 HttpGetTool（oryxos-tool/src/main/java/com/oryxos/tool/builtin/HttpGetTool.java，java.net.http 客户端，先过 WhitelistSandbox 域名检查，超时 10s）
- [x] T031 [US2] 创建 ToolExecutor（oryxos-core/src/main/java/com/oryxos/core/tool/ToolExecutor.java，按名派发；异常/超时捕获→失败结果对象 FR-008；触发 ToolProgressListener 事件；tool_invocations 审计经 T012）
- [x] T032 [US2] 创建 OryxToolSchemaAdapter（oryxos-core/src/main/java/com/oryxos/core/react/，OryxTool→ToolCallback 仅作 JSON Schema 载体，call 实现抛 UnsupportedOperationException——宪法 IV：永不自动执行）
- [x] T033 [US2] 创建 ReActLoop（oryxos-core/src/main/java/com/oryxos/core/react/ReActLoop.java，直调 ChatModel.call()；hasToolCalls→T031 执行→assistant+ToolResponseMessage 回填→继续；无法解析的 tool call → 构造解析错误反馈消息给模型纠正、计一次迭代 FR-009；上限 max-iterations FR-007；返回最终文本 FR-006；每轮 llm_calls 审计）
- [x] T034 [US2] AgentService 接入 ReActLoop 替换 T021 直调路径（oryxos-core/.../agent/AgentService.java，AGENT.md max_iterations 覆盖全局默认；PromptBuilder 注入工具清单 FR-004）
- [x] T035 [US2] CliChannel 渲染进度事件（oryxos-channel-cli/.../CliChannel.java，注册控制台 ToolProgressListener：🔧 调用工具/✅ 完成(x.xs)/❌ 失败 FR-011）

### Tests for User Story 2

- [x] T036 [P] [US2] 单测：ReActLoop 用脚本化 stub ChatModel（一轮 tool_call→回填→终答；多工具并行请求；无法解析的 tool call→错误反馈+计一次迭代 FR-009；达到上限终止）oryxos-core/src/test/java/com/oryxos/core/react/ReActLoopTest.java
- [x] T037 [P] [US2] 单测：ToolExecutor 失败捕获与审计、HttpGetTool 白名单拦截（oryxos-core+oryxos-tool src/test 各一）
- [ ] T038 [US2] 手工验证 quickstart 场景 B（闭环+进度行+审计 SQL）与场景 C（三种异常路径）

**Checkpoint**: US1+US2 可演示——天气 Agent 一次提问自主完成工具调用并回答

---

## Phase 5: User Story 3 - 模型提供商可配置、可切换 (Priority: P3)

**Goal**: dashscope 与 openai-compatible 两型上线；多 Provider 并存路由无歧义；切换零代码

**Independent Test**: quickstart 场景 D/E——qwen 复刻 Agent 问答一致；llm_calls.provider_name 可核对路由；unset key 启动即报

### Implementation for User Story 3

- [x] T039 [P] [US3] ChatModelFactory 增加 dashscope 型（oryxos-provider/.../ChatModelFactory.java，DashScopeChatModel.builder()，spring-ai-alibaba-starter-dashscope）
- [x] T040 [P] [US3] ChatModelFactory 增加 openai-compatible 型（OpenAiChatModel + base-url 覆盖，同文件）
- [x] T041 [US3] ProviderService 强化启动诊断（未知 type/未注册 provider.name/Agent 绑定查无此名时列出可用清单，US3-AS3）
- [x] T042 [P] [US3] 单测：两家 openai-compatible Provider 并存得到两个独立 ChatModel 且按名解析正确；缺失 api-key-env 启动 fail-fast（oryxos-provider/src/test/java/com/oryxos/provider/ProviderRegistryTest.java）
- [ ] T043 [US3] 手工验证 quickstart 场景 D（qwen 切换，SC-006 <5min 零代码）与场景 E（unset key 启动即报）

**Checkpoint**: 三 story 全部就绪，FR-014/FR-015 可演示

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 宪法合规复查与收尾

- [x] T044 [P] 结构化日志复查：所有 LLM/工具调用日志含 provider/tool、耗时、成败（宪法工程约束，抽查各模块日志语句）
- [ ] T045 全量跑 quickstart 场景 A–E，逐项登记 SC-001~SC-006 实测结果（specs/001-provider-react-loop/quickstart.md 验收对照表）
- [x] T046 一致性检查（AGENTS.md §10）：spec↔plan↔代码漂移核对，重点复查宪法 13 项门禁在代码层面的落点
- [ ] T047 git commit 标记 US-1+US-2 稳定点（宪法 VII：可回退）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，立即开始
- **Foundational (Phase 2)**: 依赖 T001-T005（BOM/依赖就位），BLOCKS 全部 story
- **US1 (Phase 3)**: 依赖 Phase 2；不依赖其他 story
- **US2 (Phase 4)**: 依赖 US1 的 AgentService/PromptBuilder/CliChannel（在其上演进）
- **US3 (Phase 5)**: 依赖 US1 的 Provider 层（T014-T016）；与 US2 无代码耦合，US1 完成后可与 US2 并行
- **Polish (Phase 6)**: 依赖三 story 完成

### Within Each Story

- 接口/配置类 → 服务实现 → 通道接入 → 测试 → 手工 quickstart 验证
- US2 内部：T028/T029 并行 → T030（依赖 T029）→ T031/T032（依赖 T028+T011）→ T033 → T034 → T035

### Parallel Opportunities

- Setup：T002/T003/T004 三模块 pom 并行
- Foundational：T007/T008/T009/T011 四组文件并行
- US1：T017/T018 并行；T022 与 core 服务线并行；T025/T026 测试并行
- US2：T028/T029 并行；T036/T037 测试并行
- US3：T039/T040 并行；T042 与 T041 并行
- Story 级：US1 完成后 US2 与 US3 可两人并行

---

## Parallel Example: User Story 2

```bash
# 工具底座并行：
Task T028: "ToolRegistry in oryxos-tool/.../registry/ToolRegistry.java"
Task T029: "Sandbox + WhitelistSandbox in oryxos-tool/.../sandbox/"

# 测试并行：
Task T036: "ReActLoopTest with scripted stub ChatModel"
Task T037: "ToolExecutor/HttpGetTool tests"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 + Phase 2 → 底座就绪
2. Phase 3 (US1) → quickstart 场景 A 验证 → **可演示的聊天 MVP**
3. commit 稳定点后再进 US2

### Incremental Delivery

1. US1 → 聊天可用（无工具）
2. US2 → Agent 自主调工具（本特性核心价值）
3. US3 → 多 Provider 零 lock-in 承诺兑现
4. Phase 6 → SC 全量验收 + 宪法复查 + commit 标记

### 宪法红线提醒（实施期）

- 禁用 Spring AI 自动 tool 执行：只调 ChatModel.call()，ToolCallback 永不执行（T032/T033）
- Provider 解析只查显式 Map，禁止容器类型扫描（T016）
- 审计只走 AuditRecorder 落库，不允许只打日志（T012/T031/T033）
- 密钥永不进配置文件，只存环境变量名（T015/T016）
