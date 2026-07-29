# Research: Provider 抽象 + ReAct Loop

**Date**: 2026-07-29 | **Feature**: 001-provider-react-loop

所有 Technical Context 未知项已解析；关键决策均有官方文档或 Maven Central 实证。

## R1. Spring AI 生态版本选型

**Decision**: Spring Boot **3.5.16** + Spring AI **1.1.8** + Spring AI Alibaba **1.1.2.3**
（`spring-ai-bom` / `spring-ai-alibaba-bom` 双 BOM 导入）。

**Rationale**:
- Maven Central 实证：Spring AI 2.0.0 已 GA，但绑定 Spring Boot 4.x——违反宪法
  原则 I（Spring Boot 3.x），不可用。1.1.8 是仍支持 Boot 3.x 的最新稳定线。
- Spring AI Alibaba 1.1.2.3 是其 1.x 最新稳定版（2.0.0-M1.1 为里程碑版，不采用），
  版本约定与 Spring AI 1.1.x 配对，JDK 17+ 满足。
- Boot 3.4.1 → 3.5.16：宪法约束是"Spring Boot 3.x"，3.5.x 合规且为 3.x 最新维护线
  （3.4.x 止于 3.4.13）。pom.xml 中预留的 TODO(US-1) 注释随本次实施移除。

**Alternatives considered**:
- 留守 3.4.x + Spring AI 1.0.9 + Alibaba 1.0.0.x：旧线，Alibaba 1.0.0.x 已停更，否决。
- Spring AI 2.0.0 + Boot 4.x：违宪，否决。

## R2. "禁用自动 tool 执行"的实现路径（宪法原则 IV 落地）

**Decision**: OryxOS **直接调用 `ChatModel.call(Prompt)`**，不使用 `ChatClient`；
`ToolCallback` 仅用于把工具 JSON Schema 随请求发给模型；tool call 的执行、回填、
循环控制全部由 `ReActLoop` + `ToolExecutor` 完成，回填消息手工构造
`ToolResponseMessage` 加入会话历史。

**Rationale**: Spring AI 官方文档（Tool Calling → Adding Tools to ChatModel）明确：
> "Calling `ChatModel` directly sends the tool definitions to the AI model, but tool
> calls in the response are **not** executed automatically."

即 ChatModel 直连路径天然满足宪法"禁用自动执行"，无需对抗框架默认行为。
请求构造：`ToolCallingChatOptions.builder().toolCallbacks(callbacks).build()` +
`new Prompt(messages, options)`；响应判空 `chatResponse.hasToolCalls()` 驱动循环。

**Alternatives considered**:
- `ChatClient` + `AdvisorParams.toolCallingAdvisorAutoRegister(false)` + 手工
  `ToolCallingManager.executeToolCalls()`：官方也支持，但 `ToolCallingManager` 本身
  仍是 Spring AI 的执行器（绕过 OryxOS 的 Sandbox/审计埋点），且 ChatClient 链路
  引入 advisor 心智负担，否决。
- 完全手写 HTTP 协议：重复造轮子，违"复用现成 connector"原则，否决。

## R3. 多 Provider 显式映射（宪法高发错误点 #2）

**Decision**: `ProviderRegistry` 依据 `ProviderProperties`
（`oryxos.providers.<name>.{type, model, base-url, api-key-env}`）**程序化构造**
每家 Provider 的 `ChatModel`，形成代码内显式 `Map<String, ChatModel>`；
`ProviderService.resolve(agentProviderName)` 查表返回，查无此名启动即报错。

**Rationale**: 同一类型可配置多家（如 Kimi 与豆包都走 OpenAI 兼容协议），Spring
自动配置只会产生一个 `OpenAiChatModel` Bean——按类型注入必然歧义，这正是宪法
高发错误清单第 2 条禁止的"扫描容器 ChatModel"。程序化构造彻底绕开 Bean 机制。

**Alternatives considered**: `Map<String, ChatModel>` Bean 注入（Bean 名当 key）——
依赖 Bean 命名约定、同类型多实例仍需手工定义，不如构造表直白，否决。

## R4. Provider 连接器策略

**Decision**: 三型 `ChatModelFactory`——
- `deepseek`：`DeepSeekChatModel`（`spring-ai-deepseek` 核心包，官方连接器）
- `dashscope`：`DashScopeChatModel`（`spring-ai-alibaba-dashscope` 核心包，通义）
- `openai-compatible`：`OpenAiChatModel` + 自定义 `base-url`（Kimi/智谱/混元/豆包/
  OpenAI 本家一律走此型）

**实施实证修订（2026-07-29）**：依赖从 `*-starter` 收敛为 **connector 核心包**。
starter 带入的 autoconfigure 与程序化构造路线冲突——alibaba dashscope 自动配置
带 `@ConditionalOnDashScopeEnabled(matchIfMissing=true)`，无 `spring.ai.dashscope.api-key`
即拒绝启动，`spring.autoconfigure.exclude` 亦无法根治；排除 autoconfigure jar 后
问题消失，且更贴合宪法原则四"只用 provider 抽象与协议转换类"。

**Rationale**: 国内主流模型商全部提供 OpenAI 兼容端点，一个兼容型即覆盖 README
承诺的"十余家"中的长尾；专用 starter 只保留两家有官方连接器的，降低维护面。
密钥仅经 `api-key-env` 指定的环境变量名解析，配置里永不出现密钥本体。

## R5. SQLite 持久化与 DDL 管理

**Decision**: `sqlite-jdbc` 3.49.1.0 + `hibernate-community-dialects`（版本对齐 Boot
3.5 管理的 Hibernate 6.6.x，`SQLiteDialect`）；`ddl-auto=none`，建表走手写脚本
`scripts/sql/001-init.sql` + `spring.sql.init.mode=always`。

**Rationale**: 宪法工程约束——`ddl-auto=update` 只允许首次建表、演进必须手动脚本；
干脆从第一天就脚本化，JPA 实体与脚本对齐即可，根除"自动迁移魔术"。

## R6. 60 秒调用超时的实现

**Decision**: `AgentService.process` 把 `ReActLoop` 提交到 virtual-thread
`ExecutorService`，`Future.get(60, SECONDS)` 超时后返回兜底文案（FR-010）；
单轮内部各迭代之间检查已耗时，提前熔断。

**Rationale**: virtual thread 是宪法指定的并发手段；阻塞式 HTTP 调用无法可靠
中断，超时语义定为"放弃等待、会话保留"（spec Edge Case 已定义），实现最简单可靠。

## R7. CLI REPL 与进度渲染

**Decision**: Picocli 子命令 + `BufferedReader` 读 stdin 的朴素 REPL；
`ToolExecutor` 触发 `ToolProgressListener` 事件，`CliChannel` 注册控制台实现，
打印 `🔧 调用工具: <name> ...` → `✅ 完成 (x.xs)` / `❌ 失败`（FR-011）。

**Rationale**: 核心阶段不引 JLine 等额外依赖；监听器接口中立（未来 Web/SSE 复用
同一事件源），符合"接口中立"约束。

## 遗留风险（实施期验证）

- ~~Spring AI 1.1.8 + Boot 3.5.16 的精确兼容性~~ **已验证**：全量构建 + 38 项单测 +
  假密钥真实 401 往返通过。
- ~~DashScope/DeepSeek starter 传递依赖冲突~~ **已消解**：不再使用 starter，只依赖
  connector 核心包（见 R4 修订），`dependency:tree` 无版本阴影。
