# OryxOS 扩展阶段 AI 编程指南：四个方向的 Spec-Kit 实施拆解

> 本文是 `docs/AiProgrammingGuide.md` 的续篇，把 `docs/roadmap/DemandAnalysis.md` 的 FR 拆成可执行的 user story，沿用核心阶段验证过的协作模式：Spec-Kit 全流程（constitution → specify → plan → tasks → implement）、每个 story 有可演示成果、结束必跑一致性检查。
>
> 前置状态：US-1（Provider）+ US-2（ReAct Loop）已完成，US-3/4/5 按核心阶段计划推进中。**本文的 US-6 起全部假设核心阶段已验收（三个每日 Demo 双路径跑通）。**

---

## 一、总排期：按依赖不按重要性

```
US-6  审计查询 + 可观测 + Tool Policy   ─┐（无前置，彼此可并行）
US-7  飞书/企微 Channel + SSE          ─┘
        │
US-8  Agent 目录 API 闭环 + Web 管理台   ← 依赖 US-7 的 SSE（时间线展示）
        │
US-9  多租户 + SSO/RBAC + 凭证管理       ← 依赖 US-7 带来的真实多用户场景
        │
US-10 受控沉淀 + 情景记忆 + Skill 资产库 ← 依赖 US-9 的角色语义（谁能批准）
        │
US-11 Provider 韧性 + 容器 Sandbox       ← 按生产信号插入，建议治理层之后
US-12 状态外置 + 分布式调度              ← 信号驱动，最后做
```

排序逻辑与核心阶段一脉相承：

- **US-6/US-7 先行**：全是"核心阶段已留口子、工程无前置"的项，且最快产生企业可感知价值（审计能查、指标能看、飞书里能 @）；
- **US-9 在 US-8 后**：先有"人能上来操作系统"（管理台 + 渠道），多租户/SSO 才有服务的对象；
- **US-10 在 US-9 后**：受控沉淀的"审批"动作需要角色与权限语义，倒挂会把审批做成假动作；
- **US-12 殿后**：分布式是信号驱动的承诺，"一个部门走向全公司"的信号不出现就不启动。

每个 US 仍按"4 周节奏、每周 3 小时"的约束拆 task，超出的砍范围不加时间——核心阶段的时间盒纪律延续。

---

## 二、US-6：审计查询 + 可观测 + Tool Policy（FR-G01/G02/G03、FR-R01）

**目标**：让"day one 落库的审计数据"长出查询、统计、指标三张面，同时落地最轻的治理能力。

**范围**：审计查询/统计 API（分页、过滤、聚合）；Micrometer + `/actuator/prometheus`；Profile 的 `tools` 字段升级为 allow/deny/constraints 策略对象，`PromptBuilder` 组装期过滤 + `ToolExecutor` 执行期拦截。

**验收 Demo**：配 `hr-assistant` 禁止 `shell` 后对话验证 LLM 看不到该 Tool、注入绕过尝试被拦截；Prometheus 抓到 `react_iterations` 直方图；API 查出"过去 24h 各 Agent 的 token 成本排行"。

**Spec-Kit 要点**：spec 里写清 Policy 与 Sandbox 的两层边界（Agent 级 vs 全局级），plan 里钉死"两表结构不变、只加查询"的约束。

**AI 协作提示**：
- Policy 判定必须 fail-closed（判定异常 = 拒绝），AI 容易写成 try-catch 后放行——tasks 里加显式检查项；
- 聚合 SQL 写在 Repository 层，不要让 AI 把统计逻辑摊到 Controller。

---

## 三、US-7：飞书/企业微信 Channel + SSE（FR-C01/C02/C03、FR-C04 基础版）

**目标**：第一个真实 IM 渠道跑通，验证 `ChannelAdapter` 插件机制与"所有 Channel 底层调同一 `AgentService.process`"的契约。

**范围**：`oryxos-channel-feishu` + `oryxos-channel-wecom` 两个新模块（协议层/适配层/接入层三段式）；`channels.yaml` 配置；`SseEmitter` 流式端点（`iteration`/`tool_call`/`tool_result`/`final` 四类事件）；`im_users` 身份映射基础版。

**验收 Demo**：飞书群 @每日天气 Agent 收到流式卡片回复；同一 Agent 同时被 CLI 和飞书触发，两条 Session 各自独立、审计可区分渠道来源。

**Spec-Kit 要点**：contracts 阶段先冻结"IM 消息模型 ↔ OryxOS 消息模型"的互转契约，再写协议层——协议细节（验签、加密）是 AI 最容易凭记忆写错的地方，**必须对着飞书/企微官方文档现查现写，禁止凭训练记忆实现验签逻辑**。

**AI 协作提示**：
- 消息去重（回调重推）用消息 id 幂等表，AI 容易忘掉导致重复回复；
- SSE 在 virtual thread + 同步 MVC 下实现，AI 若提议引入 WebFlux/响应式，引用宪法拒绝；
- 渠道异常（IM 平台 5xx）不能拖垮 ReAct 循环，出站失败重试要有上限。

---

## 四、US-8：Agent 目录 API 闭环 + Web 管理台（FR-C07/C08、FR-C05/C06）

**目标**："配置出来的 Agent"从手写目录 + 热加载，升级为全 API 化闭环；系统从"能调"变"可运营"。

**范围**：tar.gz 上传 → 校验（frontmatter schema、Tool 存在性、cron 合法性）→ 落盘 → 运行时注册（`AgentLoader.register` + `AgentScheduler` 动态增删触发器）；API 创建的 Agent 默认零 Tool 需显式授权；钉钉 Channel（验证第三家插件成本）；出站 IM Notify Adapter；管理台前端（消费既有 API，产物打进 fat JAR）。

**验收 Demo**：三个每日 Demo 之一的"每日科技日报"**全程 API 复现**——上传目录、授权 Tool、绑 cron、跑完推飞书群，不登录服务器、不手改文件。

**AI 协作提示**：
- 上传解包必须防 Zip Slip（路径穿越），AI 生成的解包代码高频出此漏洞，tasks 里加安全测试项；
- `AgentScheduler` 增删触发器与现有触发器注册共用一把任务表锁，防并发注册丢任务；
- 管理台只调既有 API，AI 若图省事加"管理台专用后门端点"，review 时拒掉。

---

## 五、US-9：多租户 + SSO/RBAC + 凭证管理（FR-G05/G06/G07/G08、FR-C04 完整版）

**目标**：治理终局的主体落地，OryxOS 具备过企业安全审查的完整故事。

**范围**：三级租户模型 + `tenants` 表 + 全表 `tenant_id` 织入（ArchUnit 防裸查）；`TenantContext` 用 `ScopedValue`；Spring Security OIDC 客户端 + group claim → 角色映射；RBAC 到 Agent/Tool/Skill 粒度；KMS/Vault 凭证对接；`audit_events` 治理事件表 + SIEM Webhook 导出。

**验收 Demo**：Keycloak 起两个租户，各建同名 Agent 互不可见；viewer 角色调写 API 返回 403 且 `audit_events` 留痕；审计事件 5 秒内到达 SIEM Webhook。

**AI 协作提示**：
- **`ScopedValue` 不是 `ThreadLocal`**：AI 的训练记忆里 Spring 上下文传递默认是 `ThreadLocal`，在 virtual thread 项目里必须显式纠正，plan 文档里写死；
- 租户过滤织入点在 Repository 层而非 Service 层，AI 容易在 Service 里手写过滤导致漏路径；
- OIDC 配置全部 `${ENV_VAR}` 占位，AI 若在 application.yml 写死 client secret，直接返工（宪法第七条）。

---

## 六、US-10：受控沉淀 + 情景记忆 + Skill 资产库（FR-M01/M02/M05/M06、FR-M03/M04）

**目标**：知识沉淀价值锚点兑现——Agent 的经验留在企业、经人审生效、新员工可继承。

**范围**：`episodic_memory` 表 + `record_episode` Tool；`proposals` 表 + `propose_memory`/`propose_skill` 两个 Tool + 管理台审批界面；批准内容写入 MEMORY.md（带来源标注）或 `skill_registry`；`PgVectorMemoryStore`（与 US-12 的 PG 外置协同，可分拆）；上下文总结压缩替换简单截断。

**验收 Demo**：Agent 完成一次排障任务后产生 3 条提议，批准 2 条拒绝 1 条——下一轮对话只体现批准的 2 条；从资产库给新 Agent 安装已签名 Skill，AGENT.md 指引下经 `read_file` 渐进取用；审计可回答"谁提议、谁批准、何时生效"。

**AI 协作提示**：
- 最高危错误：**提议内容在批准前混入 prompt 组装路径**。`ContextLoader`/`MemoryService` 只读已生效内容，tasks 里加架构测试断言 `proposals` 表不被 prompt 路径引用；
- "不缓存"契约不破：批准生效后下一轮立即可见，AI 若为性能给 MEMORY.md 加缓存，引用核心阶段契约拒绝；
- embedding 只对新内容懒计算，成本入 `llm_calls`。

---

## 七、US-11：Provider 韧性 + 容器 Sandbox（FR-R02/R03/R04）

**目标**：生产链路的两块硬保障，按"有 Agent 进生产"的信号插入（可与 US-9/US-10 调换顺序）。

**范围**：Profile `fallback` 声明 + `ProviderService` 层切换 + Resilience4j 熔断（切换写 `llm_calls`）；`ContainerSandbox`（`ProcessBuilder` + runc rootless，namespace/cgroups/seccomp），`WhitelistSandbox` 保留为开发档；Tool 调用并行与独立超时。

**AI 协作提示**：
- fallback 对 ReActLoop 完全透明，AI 若把切换逻辑写进 ReActLoop，违反分层（Provider 层之下的事不上浮）；
- `ContainerSandbox` 实现必须经 `Sandbox` 接口注入，接口签名不出现"容器/runc/镜像"字样（接口中立原则，AI 高频违反点）；
- macOS 开发机自动降级 `WhitelistSandbox`，配置显式声明而非静默判断。

---

## 八、US-12：状态外置 + 分布式调度（FR-R05/R06/R07/R08）

**目标**：阶段二——多实例、高可用、水平扩展。信号不出现不启动。

**范围**：`RedisSessionRepository`；审计/租户/资产库迁 PostgreSQL（手动 DDL 脚本，SQLite 保留单机档）；cron 租约制（`lease_owner`/`lease_until`，替换进程内 `ReentrantLock`）；IM 消息幂等表 + 行锁抢占；K8s 部署物（Deployment/Service/Ingress/HPA）。

**验收 Demo**：双实例挂同一飞书渠道 + 同一 cron，消息不重复回复、任务不重复执行；杀一个实例，进行中 Session 在另一实例恢复；HPA 扩容新实例自动分担。

**AI 协作提示**：
- 租约续期与任务执行要在同一事务边界内思考，AI 容易写出"抢到租约后任务超时、租约过期、另一实例重复触发"的竞态；
- PG 迁移脚本必须手写且与 SQLite schema 逐字段对齐，禁止 `ddl-auto`（核心阶段纪律延续）；
- 不引 MQ：幂等表 + 行锁在当前规模足够，AI 若提议 Kafka/RocketMQ，按信号驱动原则拒绝并记录到决策日志。

---

## 九、贯穿性工程纪律（从核心阶段继承 + 增量）

**继承不变的**：

1. Spec-Kit 全流程，每个 US 结束跑一致性检查（spec/plan/代码漂移），不省；
2. 每个 US 有可演示成果，跑通优先于完美；
3. 标识符英文 ASCII，测试中文描述进 `@DisplayName`；
4. 一切调用留审计（LLM/Tool/治理动作/渠道消息/记忆变更）；
5. git commit 标记每个 US 完成点，可回退到稳定态。

**扩展阶段增量（AI 高发错误清单第二批）**：

| ❌ 错误写法 | ✅ 正确做法 |
| --- | --- |
| 用 `ThreadLocal` 传租户上下文 | `ScopedValue`（JDK 21 原生、virtual thread 正确解） |
| 凭训练记忆写 IM 回调验签 | 对官方文档现查现写，契约测试钉住 |
| Policy 判定异常时默认放行 | fail-closed，异常 = 拒绝 + 审计 |
| 提议内容批准前进入 prompt 路径 | `proposals` 与组装路径物理隔离，架构测试断言 |
| 给 MEMORY.md/AGENT.md 加缓存 | 不缓存契约是功能不是缺陷，批准即时生效靠它 |
| 为 SSE/高并发引入 WebFlux | virtual thread + 同步 MVC + `SseEmitter`，宪法禁响应式 |
| `Sandbox` 接口出现"容器/镜像"字样 | 接口中立，实现类才携带档位概念 |
| 分布式一上来就引 MQ/Sentinel | 轮询+行锁+Resilience4j 先行，重量级组件等信号 |
| 多租户过滤写在 Service 层手写 | Repository 层织入 + ArchUnit 防裸查 |
| 新能力直接改 core 既有类 | 新类 + 新模块，core 改动只允许"新类注册进既有接口" |

**新增能力先想三个问题**（核心阶段"接口先行"的扩展版）：

1. 接口签名里有没有出现某一档实现/某一家的词？（渠道不出现"飞书"、存储不出现"Redis"）
2. 这个能力的审计事件长什么样？（没有审计设计 = 设计没完成）
3. 关掉它系统是否退化为核心阶段行为？（每个扩展必须可开关、可回退）

---

## 十、完成定义（DoD）模板

每个 US 关闭前逐项核对：

- [ ] spec / plan / tasks 三件套与实际代码一致（漂移已修复或文档已更新）
- [ ] 验收 Demo 录制或现场跑通（含"钟推 + 人推"双路径，若适用）
- [ ] 审计断言：该 US 涉及的所有动作类型在审计表/指标中有对应记录
- [ ] 安全断言：敏感配置无落盘明文，新端点默认认证（US-9 后）
- [ ] 回退断言：feature flag 关闭后系统行为 = 核心阶段
- [ ] git tag 标记，一致性检查报告归档
