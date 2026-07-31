# OryxOS 扩展阶段技术方案：四个方向的关键决策

> 本文是 `docs/TechnicalSolution.md` 的续篇，覆盖 `docs/roadmap/DemandAnalysis.md` 定义的四个方向（FR-G/FR-C/FR-M/FR-R）。
>
> **总原则（从宪法继承，不可协商）**：
> 1. 9 模块骨架不变；新能力 = 新增实现类或**新增边缘模块**，不改 core 已冻结接口；
> 2. 接口先行且中立（签名不出现某一档实现特有的词）；
> 3. 信号驱动升级，不做期货架构；
> 4. Spring AI 仍然"只用一半"——Provider 抽象与 Schema 生成可用，自动 tool 执行永远禁用；
> 5. 一切新能力同步落审计：治理动作、渠道消息、记忆变更、调度触发，全部结构化留痕。
>
> **本文是发展思考草案，决策需评审后冻结。**

---

## 一、方向一：企业治理层

### 决策 G-1：Tool Policy 落在 PromptBuilder 与 ToolExecutor 两个点，不在 ReActLoop

Policy 判定放两个位置，各司其职：

1. **组装期过滤**（`PromptBuilder`）：生成 Tool 列表 Schema 时按 Policy 剔除被 deny 的 Tool——LLM 根本看不见，这是第一道；
2. **执行期拦截**（`ToolExecutor`）：执行前再查一次 Policy（含参数级约束），拒绝则返回标准错误并写审计——防 Prompt 注入绕过第一道，这是 fail-closed 兜底。

数据模型：`AGENT.md` frontmatter 的 `tools` 字段升级为策略对象：

```yaml
tools:
  allow: [read_file, write_file, http_get, notify]
  deny: [shell]
  constraints:
    shell:
      command_prefix: ["ls", "cat"]      # 参数级约束（deny 优先于 constraints）
    http_get:
      domains: ["*.corp.example"]        # 与 Sandbox 白名单的关系：Policy 是 Agent 级，Sandbox 是全局级，两层都查
```

关键边界：**Policy（Agent 级，业务语义）与 Sandbox（全局级，安全语义）是两层，不合并**。Policy 管"这个 Agent 能不能用这个 Tool"，Sandbox 管"任何 Tool 执行的全局红线"。

### 决策 G-2：审计查询只读现有两表，新增 `audit_events` 表承载治理事件

`tool_invocations`、`llm_calls` 两表核心阶段已在写入，查询 API 直接建在现有 schema 上（`oryxos-storage` 加 Repository 查询方法，`oryxos-web` 加查询端点），**不做表结构变更**。

治理层自身产生的新事件（登录、Policy 变更、审批动作、凭证使用、租户变更）入新表：

```sql
CREATE TABLE audit_events (
  id TEXT PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  actor TEXT NOT NULL,           -- 谁（用户/系统/Agent）
  action TEXT NOT NULL,          -- login / policy_update / approval_grant / credential_use ...
  target TEXT,                   -- 作用对象（agent 名、policy id ...）
  detail TEXT,                   -- JSON
  created_at TEXT NOT NULL
);
```

SIEM 导出走"CDC 轮询 + Webhook 推送"的无侵入方案：独立调度任务扫增量（按 `created_at` 水位线），POST 到配置的 Webhook，失败重试+死信记录。不引入消息队列——单实例规模下轮询足够，队列等阶段二再说。

### 决策 G-3：多租户走"存储层隔离 + 上下文透传"，不动业务代码

- **租户模型**：三级（org/dept/project）落在一张 `tenants` 表，Agent 目录、Session、Memory、审计全部加 `tenant_id` 列（SQLite 手动迁移脚本，延续"不依赖 ddl-auto"的约定）。
- **上下文透传**：`TenantContext` 用 **`ScopedValue`**（JDK 21 正式特性）而非 `ThreadLocal`——virtual thread 场景下 `ThreadLocal` 既浪费内存又有池化复用风险，这是 JDK 21 栈的正确答案，也是本项目"用足 JDK 21"原则的体现。
- **隔离粒度**：SQLite 阶段同库不同行（`tenant_id` 过滤强制织入 Repository 层，禁止裸查）；阶段二外置 PG 后按信号升级 schema 级隔离。

### 决策 G-4：SSO 用 Spring Security 的 OIDC 客户端能力，不自己实现协议

`oryxos-web` 引入 `spring-boot-starter-oauth2-client`，对接企业 IdP（Entra ID/Okta/Keycloak/阿里云 IDaaS 均是标准 OIDC）。RBAC 的角色映射从 IdP 的 group claim 派生，本地 `role_bindings` 表存覆盖规则。**只做客户端，不做 IdP，不存密码。**

---

## 二、方向二：渠道与触达

### 决策 C-1：IM Channel = 新模块 + `ChannelAdapter` 实现，底层调 Web Service 的 Agent 接口

沿用 `TechnicalSolution.md` 已定结构：新增 `oryxos-channel-feishu`、`oryxos-channel-wecom` 等模块（模块总数突破 9 是**加模块不是改结构**，宪法管的是"不多不少的核心骨架"，边缘扩展是宪法明示的扩展方式）。

每个 IM Channel 模块内部三层：

1. **协议层**：回调验签、消息加解密、事件去重（消息 id 幂等表）；
2. **适配层**：IM 消息模型 ↔ OryxOS 消息模型互转，实现 `ChannelAdapter`；
3. **接入层**：拿到消息后调 `AgentService.process`（与 CLI/Web/cron 同一入口，ReAct 不感知来源的契约不破）。

身份映射基础版：`im_users` 表存 IM 用户 id ↔ OryxOS 用户/租户映射，未映射用户落入"默认租户 + viewer"（治理层落地后收紧为必须显式映射）。

### 决策 C-2：出站 Notify 复用 Channel 配置，但不复用 Channel 代码

`NotifyChannelAdapter` 新增 `FeishuNotifyAdapter` 等实现，与入站 Channel **共享同一份应用凭证配置**（`mcp_servers.yaml` 同级的 `channels.yaml`），但代码上是独立 Adapter——入站管"消息怎么进来"，出站管"结果送到哪"，方向相反、分开建模的原则不破坏。

### 决策 C-3：SSE 用 Spring MVC `SseEmitter`，不上 WebFlux

`POST /api/agents/{name}/invoke` 增加 `stream=true` 参数，返回 `text/event-stream`。ReAct 循环每轮迭代产出事件（`iteration` / `tool_call` / `tool_result` / `final`），经 `SseEmitter` 推送。virtual thread + 同步 MVC 模型不变，**不引入 WebFlux**（宪法约束）。事件流同时是 Web 管理台"Tool 调用时间线"的数据源。

### 决策 C-4：Agent 目录 API 闭环 = 上传 + 校验 + 热生效，三件套

原 7.3 节 15 端点归为一个资源域：

- **上传**：`POST /api/agents` 接收 tar.gz（含 `AGENT.md` + 可选 skills/scripts），落 `.oryxos/agents/<name>/`，**上传即校验**（frontmatter schema、Tool 白名单存在性、cron 表达式合法性），校验失败整体拒绝不留半截目录；
- **热生效**：`AgentLoader` 增加 `register/unregister` 运行时方法，`ContextLoader` 不缓存契约天然支持内容即时生效；cron 定义变化时 `AgentScheduler` 动态增删触发器（沿用 `ThreadPoolTaskScheduler` + `CronTrigger` 动态注册，不用 `@Scheduled`）；
- **安全闸**：API 创建的 Agent 默认 `tools` 为空集，必须显式授权（与 FR-G01 的 Policy 联动）——**API 能创建 Agent，但默认创建的是一个没有任何 Tool 的 Agent**。

### 决策 C-5：Web 管理台前后端分离，构建产物打进 fat JAR

前端（React/Vue 择一，按团队熟悉度）构建产物放 `oryxos-web` 的 `static/` 下，单二进制部署的形态不破。管理台只消费既有 API，不开后门端点。

---

## 三、方向三：记忆与知识资产化

### 决策 M-1：情景记忆 = Session 内的一张新表，不是 MEMORY.md 的新区

情景记忆（任务过程中的事实：改了什么文件、做了什么决策、错误与绕行）是会话级结构化数据，放文件里是反模式。新增 `episodic_memory` 表：

```sql
CREATE TABLE episodic_memory (
  id TEXT PRIMARY KEY,
  session_id TEXT NOT NULL,
  tenant_id TEXT NOT NULL,
  kind TEXT NOT NULL,            -- file_change / decision / error_workaround / fact
  content TEXT NOT NULL,
  created_at TEXT NOT NULL
);
```

写入由 Agent 经新增的 `record_episode` 内置 Tool 触发（第 10 个内置 Tool，走 `OryxTool` 注册老路）；**注入策略**：任务进行中全量注入（任务内相关），Session 结束后只保留摘要指针——这正是"受控沉淀"的原料池。

### 决策 M-2：受控沉淀 = 提议队列 + 人审 + 生效留痕，三段式

```
Agent（任务结束，经 propose_memory / propose_skill 两个新内置 Tool 提议）
  → proposals 表（PENDING）
    → 管理台/CLI 审批（APPROVED / REJECTED，写 audit_events）
      → 批准的内容写入 MEMORY.md（追加，带来源标注）或 Skill 资产库
        → 下一轮 ReAct 立即可见（不缓存契约）
```

关键设计：**提议内容在批准前绝不进入任何 prompt 组装路径**——`ContextLoader`/`MemoryService` 只读已生效内容，`proposals` 表与 prompt 路径物理隔离。这是与 Hermes 全自动沉淀的主动分歧在代码层面的兑现。

### 决策 M-3：向量检索 = `LongTermMemoryStore` 第三档实现，pgvector 优先

`PgVectorMemoryStore` 新增实现类：归档区记忆切片 embedding（经现有 Provider 体系的 embedding 模型，不引新依赖族），召回时语义 TopK 与关键词结果合并去重后注入。选型理由：阶段二状态外置的目标就是 PostgreSQL，pgvector 让向量能力**搭状态外置的便车**，不为记忆单独养一个 Milvus/Qdrant。SQLite 阶段的过渡：`SqliteMemoryStore`（第二档）先承接结构化，向量档与 FR-R06 同期落地。embedding 调用的 token 成本入 `llm_calls` 审计，成本透明不破。

### 决策 M-4：Skill 资产库 = 受控注册中心，复用 Agent 目录形态

- 存储：`skill_registry` 表（name、version、签名、来源、审核状态、内容 hash）+ 文件内容落 `.oryxos/skill-registry/`；
- 签名：内部用 HMAC（密钥走 FR-G08 凭证管理），不做公钥体系（企业内流转够用，按信号升级）；
- 引用：Agent 目录 `AGENT.md` frontmatter 增加 `skills: [{name, version}]`，`ContextLoader` 解析时校验签名与审核状态，**未通过审核的 Skill 拒绝注入**；
- 兼容：SKILL.md 格式兼容 agentskills.io，社区优质 Skill 经企业审查后可导入（`IndustryResearch.md` 5.7 节既定策略）。

---

## 四、方向四：生产级运行时与分布式

### 决策 R-1：可观测性 = Micrometer 指标 + 既有审计表，双轨不重复

- **指标轨**：`oryxos-boot` 引入 Micrometer + Prometheus registry，暴露 `/actuator/prometheus`。指标清单：`llm_calls_total{provider,model,status}`、`llm_tokens_total{direction}`、`tool_invocations_total{tool,status}`、`tool_duration_seconds`、`react_iterations`（直方图）、`active_sessions`。数据源走应用内埋点（Micrometer API），**不从审计表反算**（指标是低开销计数器，审计是明细，双轨各管各的）。
- **审计轨**：FR-G02/G03 的查询与统计直接 SQL 聚合两表。
- 链路追踪（SkyWalking/OTel）按信号，指标先行。

### 决策 R-2：Provider fallback 在 `ProviderService` 显式映射层内实现

Profile 增加 `fallback: [provider-b, provider-c]` 声明。调用链：`ProviderService.invoke` 捕获主 Provider 异常 → 按声明顺序切换 → 每次切换写 `llm_calls`（`failover_from` 字段记录来源）。熔断用 Resilience4j（轻量、同步模型友好、与 virtual thread 兼容），**不引入 Sentinel 到核心**——Sentinel 是阶段二多实例时的选择，信号驱动。ReActLoop 对 fallback 无感知（切换发生在 Provider 层之下）。

### 决策 R-3：`ContainerSandbox` 用 Java 进程 API + OCI runtime，不引 Docker Java 客户端重依赖

`Sandbox` 接口不变（契约：接口中立，不出现"容器"字样——已实现）。`ContainerSandbox` 实现经 `ProcessBuilder` 调 `runc`/`crun`（rootless 模式），mount namespace 隔离文件系统、cgroups v2 限资源、seccomp 收系统调用。选 runc 而非 Docker API：少一个守护进程依赖，企业最小化安装面更友好；Docker/Podman 环境可经配置切换命令前缀复用。

### 决策 R-4：无状态化改造清单先行，分布式是它的自然结果

按依赖排序的三步（每步独立可发布）：

1. **Session 外置**（FR-R05）：`SessionRepository` 新增 Redis 实现（接口已在 storage 层），`.oryxos/sessions/` 文件保留为本地降级档；
2. **存储外置**（FR-R06）：审计/租户/资产库迁 PostgreSQL，`oryxos-storage` 增加 PG profile（JPA 方言切换 + 手动 DDL 脚本，SQLite 保留为单机档）；
3. **调度与消费**（FR-R07/R08）：cron 触发器改租约制（`schedules` 表加 `lease_owner`/`lease_until`，触发前先抢租，天然替换进程内 `ReentrantLock`）；IM 消息消费走"消息 id 幂等表 + 数据库行锁抢占"，不引 MQ（信号不够，轮询+行锁在多实例小规模下足够）。

K8s 部署物：Deployment（多副本）+ Service + Ingress + HPA，全部是无状态标准件——这正是"像装一个 Spring Boot 应用一样自然"的终态。

---

## 五、风险与对策（增量）

| 风险 | 影响 | 对策 |
| --- | --- | --- |
| 治理链路拖慢 Agent 调用 | 每轮 ReAct 多两次 Policy 判定 | 判定逻辑纯内存查表（Profile 加载时编译为判定树），目标 ≤5ms，FR 已写死 |
| 审计表膨胀 | 高频 Agent 场景 SQLite 写压力 | 查询 API 强制分页；FR-R06 外置 PG 是根本解；提供归档任务（老数据转对象存储） |
|  embedding 成本失控 | 归档区大时向量化烧钱 | 懒向量化（只在新内容入库时算）；批量调用；成本入审计可观测 |
| IM 协议变更 | 飞书/企微回调协议升级 | 协议层独立成模块内包，适配改动不溢出；契约测试钉住消息模型互转 |
| 多租户过滤漏网 | Repository 裸查绕过 `tenant_id` | 架构测试（ArchUnit）强制所有 Repository 方法经租户织入层 |
| 容器 Sandbox 在 macOS 开发机不可用 | 开发体验断裂 | `WhitelistSandbox` 保留为开发档，`ContainerSandbox` 仅 Linux 生产启用，配置切换 |

---

## 六、四个方向的模块落点总览

| 方向 | 新增模块 | 改动模块 | 新增实现类（接口不变） |
| --- | --- | --- | --- |
| 治理 | — | `oryxos-web`（认证/查询端点）、`oryxos-storage`（新表）、`oryxos-core`（Policy 判定） | `TenantContext`、`PolicyEnforcer` |
| 渠道 | `oryxos-channel-feishu`、`oryxos-channel-wecom`、`oryxos-channel-dingtalk`、`oryxos-admin-ui` | `oryxos-web`（SSE、Agent 目录 API） | `FeishuChannelAdapter`、`FeishuNotifyAdapter` 等 |
| 记忆 | — | `oryxos-memory`、`oryxos-core`（两个新内置 Tool） | `SqliteMemoryStore`、`PgVectorMemoryStore`、`ProposalService` |
| 运行时 | — | `oryxos-boot`（Micrometer）、`oryxos-provider`（fallback）、`oryxos-tool`（ContainerSandbox）、`oryxos-storage`（Redis/PG） | `ContainerSandbox`、`RedisSessionRepository`、`PgRepositories` |

核心验证：**这张表里 core 的改动只有"Policy 判定 + 两个新内置 Tool + Scheduler 租约"，全部是新类，不是改既有类**——扩展阶段如果做到"核心引擎基本不动、边缘持续生长"，就说明核心阶段的接口预留是合格的。
