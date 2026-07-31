# OryxOS 扩展阶段需求分析：四个方向的功能定义

> 本文是 `docs/DemandAnalysis.md` 的续篇，承接 `docs/roadmap/IndustryResearch.md` 论证的四个方向（治理、渠道、记忆与知识、生产级运行时与分布式），把每个方向落到可验收的功能需求。
>
> 沿用原文档的规则：三档功能分类（核心/扩展/社区）、术语先行、每个需求有验收标准、明确"不做什么"。**本文是发展思考草案，需求编号、优先级、范围均需与人讨论后冻结。**
>
> 需求编号规则：`FR-Gxx` 治理（Governance）、`FR-Cxx` 渠道（Channel）、`FR-Mxx` 记忆（Memory）、`FR-Rxx` 运行时（Runtime）。

---

## 一、定位与范围

核心阶段交付运行时内核（五大能力 + CLI + Web Service + 审计落库）。扩展阶段的目标一句话：**让 OryxOS 从"能跑 Agent 的内核"变成"企业愿意放进生产的底座"**。

扩展阶段不重新定义核心概念（Agent、Profile、Tool、Channel、Memory 的定义以 `DemandAnalysis.md` 为准），只新增以下术语：

| 术语 | 定义 |
| --- | --- |
| **租户（Tenant）** | 三级模型：组织 → 部门 → 项目。Session、Memory、审计、Agent 目录按租户隔离 |
| **Tool Policy** | Profile 级的 Tool allow/deny 策略，含参数级约束（如 `shell` 只允许某前缀命令） |
| **审计查询** | 对 `tool_invocations`、`llm_calls` 两张表的结构化查询、统计与导出能力 |
| **审批队列（Approval Queue）** | Agent 提议的记忆/Skill 变更的待审池，人批准后生效，全程留痕 |
| **情景记忆** | 单个任务过程中学到的东西（改了什么文件、做了什么决策、踩了什么坑），任务结束时提炼 |
| **Skill 资产库** | 企业内受控的 Skill 注册中心：注册、审核、签名、版本、可追溯，不是公开市场 |
| **入站 Channel / 出站 Notify** | 沿用核心阶段定义；扩展阶段入站补 IM，出站补 IM 专用 Adapter |
| **状态外置** | Session、Memory、审计、调度锁从进程内/本地文件迁移到 Redis/PostgreSQL/对象存储 |

---

## 二、方向一：企业治理层（FR-G）

### 2.1 用户故事

> 作为企业的 IT 安全负责人，我要能回答"昨晚谁让哪个 Agent 调了什么 Tool、花了多少 token、有没有失败"，并且能一键导出给审计部门；我要能规定"HR 助手里不能用 shell"，而不需要改代码；我要能用公司 SSO 登录管理台，按部门隔离各自的 Agent 和数据。

### 2.2 功能需求

| 编号 | 需求 | 优先级 | 说明 |
| --- | --- | --- | --- |
| FR-G01 | Tool Policy：Profile 级 allow/deny，支持参数级约束 | P0 | 在现有 `tools` 字段雏形上扩展，最轻的治理能力 |
| FR-G02 | 审计查询 API：`tool_invocations`/`llm_calls` 按时间、Agent、Session、成败过滤查询与分页 | P0 | 数据 day one 已在，补查询面 |
| FR-G03 | 审计统计：token 用量按 Agent/Provider/时间聚合，成本报表 | P0 | 成本透明从"基础版"升级为"可运营" |
| FR-G04 | 审计导出：CSV/JSON 导出，Webhook 推送至 SIEM | P1 | 对接企业现有审计流程 |
| FR-G05 | 认证：管理台与 API 接入 OIDC（先 OIDC，SAML 按客户信号） | P1 | 无认证假设内网的前提到此结束 |
| FR-G06 | RBAC：角色到 Agent/Tool/Skill 粒度，内置 admin/operator/viewer 三角色 | P1 | 权限模型随多租户一起冻结 |
| FR-G07 | 多租户：三级租户模型，Session/Memory/审计/Agent 目录全链路隔离 | P1 | 存储层 key 前缀隔离，请求链路带租户标识 |
| FR-G08 | 凭证管理：API key/webhook 凭证对接企业 KMS/Vault，不落盘不明文，使用留痕 | P2 | 核心阶段的 `${ENV_VAR}` 占位是起点不是终点 |
| FR-G09 | 合规对接：等保三级/SOC 2 所需的数据留存策略、访问日志 | P2 | 按客户信号驱动 |

### 2.3 验收标准

1. 配一条 Policy"禁止 `hr-assistant` 使用 `shell`"后，该 Agent 的 ReAct 循环中 `shell` 不出现在 Tool 列表，LLM 即使尝试调用也被拒绝并留审计记录。
2. 通过 API 查出"过去 24 小时某 Agent 全部 Tool 调用及耗时/成败"，结果与结构化日志一致。
3. 关闭导出开关时系统无任何外发行为；开启后审计事件 5 秒内到达指定 SIEM Webhook。
4. 两个租户各自创建同名 Agent，互不可见、互不可调用，审计数据交叉查询为空。
5. SSO 登录后，viewer 角色调用任何写操作 API 返回 403 且留痕。

### 2.4 明确不做

- 不做自研身份体系（只对接，不造 IdP）；不做数据脱敏引擎（留扩展点）；不做区块链存证类噱头。

---

## 三、方向二：渠道与触达（FR-C）

### 3.1 用户故事

> 作为业务部门员工，我在飞书里 @运维助手 就能问"昨晚的备份任务成功了吗"，回答带消息卡片；作为业务系统，我通过 API 上传一个 Agent 目录就有了一个新 Agent，并给它绑定 cron 和飞书渠道，全程不用登录服务器。

### 3.2 功能需求

| 编号 | 需求 | 优先级 | 说明 |
| --- | --- | --- | --- |
| FR-C01 | 飞书 Channel：入站消息接收（回调验签）、出站回复、消息卡片 | P0 | 国内企业渗透率优先 |
| FR-C02 | 企业微信 Channel | P0 | 与 FR-C01 同一批 |
| FR-C03 | SSE 流式响应：Agent 调用端点支持 `SseEmitter` 流式输出 | P0 | Web 管理台和 IM 体验的前置 |
| FR-C04 | IM 身份映射：IM 用户 → OryxOS 用户/租户，会话归属留痕 | P1 | 无治理层时先落基础版 |
| FR-C05 | 钉钉 Channel | P1 | 插件机制验证第三家 |
| FR-C06 | 出站 Notify 专用 Adapter：飞书/企微/钉钉（复用 Channel 配置） | P1 | 入站/出站分离结构兑现 |
| FR-C07 | Agent 目录 API 闭环：上传目录、动态创建/更新/删除 Agent、运行时增删 cron（原 7.3 节 15 端点） | P1 | "配置出来的 Agent"的完整闭环 |
| FR-C08 | Web 管理台：Agent 列表与详情、Session 查看、审计查询页、Tool 调用时间线、cron 管理 | P1 | 6 个 ApiController 扩展 + 前端 |
| FR-C09 | 邮件 Channel（入站） | P2 | 按信号 |
| FR-C10 | Channel SDK/示例：Java + 一种跨语言示例 | P2 | 扩展阶段才出 SDK |

### 3.3 验收标准

1. 飞书群里 @Agent 提问，30 秒内收到流式回复；同一用户在 IM 和 CLI 的 Session 各自独立、审计可查。
2. 通过 API 上传含 `schedules` frontmatter 的 Agent 目录后，到点自动执行且结果经 Notify 推到指定飞书群——即"每日天气 Demo"的**全 API 化复现**。
3. 新增一个 IM Channel 不动 `oryxos-core`/`oryxos-web` 以外的既有代码，只加模块与配置。
4. Web 管理台上能完成"查 Agent → 看 Session → 看审计 → 停 cron"的完整操作闭环。

### 3.4 明确不做

- 不做 C 端渠道（微信个人号、QQ 等 Hermes 式触达）；不做语音/视频通道；WebSocket 按信号再议（SSE 优先）。

---

## 四、方向三：记忆与知识资产化（FR-M）

### 4.1 用户故事

> 作为企业知识管理负责人，我要 Agent 在任务里学到的东西不会自动生效，而是进入审批队列，我批准后才写入记忆或 Skill 库；老员工离职后，他带出来的 Agent 经验和 Skill 留在企业，新员工的 Agent 可以直接继承。

### 4.2 功能需求

| 编号 | 需求 | 优先级 | 说明 |
| --- | --- | --- | --- |
| FR-M01 | 情景记忆：任务级事实记录（文件改动、决策、错误与绕行），随任务结束归档 | P1 | 三层记忆的最后一块 |
| FR-M02 | 受控沉淀：Agent 可"提议"写入长期记忆/Skill，提议进审批队列，人审批准后生效 | P1 | 本文最具差异化的需求，对标并反转 Hermes 全自动沉淀 |
| FR-M03 | 记忆语义检索：向量档 `LongTermMemoryStore` 实现，归档区按语义召回 | P2 | pgvector 优先，顺应状态外置路线 |
| FR-M04 | 上下文总结压缩：替代简单截断，保留任务关键信息 | P2 | 与 FR-M01 的情景记忆联动 |
| FR-M05 | Skill 资产库：注册、审核、签名、版本管理、来源追溯，SKILL.md 兼容 agentskills.io | P2 | 企业内流转，非公开市场 |
| FR-M06 | Skill 继承与分发：新 Agent 可引用资产库中已批准的 Skill | P2 | 知识沉淀价值锚点的兑现 |

### 4.3 验收标准

1. Agent 完成任务后产生一条沉淀提议，在未批准前该提议**不出现**在任何后续 prompt 中；批准后下一轮 ReAct 立即可见（不缓存契约不破）。
2. 从资产库安装一个带签名的 Skill 到新 Agent 目录，Agent 按 AGENT.md 正文指引经 `read_file` 渐进取用，全程审计可查来源与版本。
3. 语义检索开启前后，同一归档记忆区对同一查询的召回差异可演示（关键词查不到、语义能查到）。
4. 所有记忆/Skill 变更在审计表中可回答"谁提议、谁批准、何时生效、内容是什么"。

### 4.4 明确不做

- 不做全自动沉淀（Agent 直接写自己的指令——这是与 Hermes 的主动分歧，不是能力缺失）；不做跨企业的 Skill 市场；不做知识图谱（按信号）。

---

## 五、方向四：生产级运行时与分布式（FR-R）

### 5.1 用户故事

> 作为企业平台运维，我要 OryxOS 像任何一个 Spring Boot 服务一样接入我的监控（Prometheus 抓指标、Grafana 看板、告警）；Provider 挂了 Agent 自动切备用；当部门试点走向全公司时，我加实例就能扩容，定时任务不会跑重，Session 不丢。

### 5.2 功能需求

| 编号 | 需求 | 优先级 | 说明 |
| --- | --- | --- | --- |
| FR-R01 | Micrometer + Prometheus 指标端点：LLM 调用、Tool 调用、ReAct 迭代深度、Session 数 | P0 | 数据源 day one 已有 |
| FR-R02 | Provider fallback：Profile 声明备用 Provider，故障自动切换，熔断与重试 | P1 | 经显式映射层实现，不动 ReAct |
| FR-R03 | 容器 Sandbox：`ContainerSandbox` 实现（namespace + cgroups + seccomp） | P1 | 信号：跑相对不可信 Tool 或多租户 |
| FR-R04 | Tool 调用并行与超时治理：单响应多 Tool 调用的并行执行与独立超时 | P2 | 核心阶段顺序执行的升级 |
| FR-R05 | Session 外置：Session/短期上下文迁 Redis，实例无状态化 | P2 | 分布式前置条件 |
| FR-R06 | 存储外置：审计/长期记忆迁 PostgreSQL（pgvector 复用），大文件迁对象存储 | P2 | 与 FR-M03 共用 pgvector |
| FR-R07 | 分布式调度：定时任务租约机制，多实例下同一任务只跑一份 | P2 | 替换进程内 `ReentrantLock` |
| FR-R08 | 渠道消息消费去重：多实例下一条 IM 消息恰好被一个实例处理 | P2 | 消费组/分发层 |
| FR-R09 | GraalVM Native Image：压缩启动与内存 | P3 | AOT 适配成本已知，按信号 |
| FR-R10 | microVM Sandbox（Firecracker/Kata/gVisor） | P3 | 信号：完全不可信代码/规模化多租户 |

### 5.3 验收标准

1. Prometheus 能抓到 `llm_calls_total`、`tool_invocations_total{status=success|failed}`、ReAct 迭代深度直方图，且与审计表数据一致。
2. 主 Provider 注入故障后，Agent 调用自动落到备用 Provider，审计中可见切换记录，用户无感知。
3. 两个实例同时挂同一个飞书渠道和同一个 cron 任务：消息不重复回复，任务不重复执行；杀掉一个实例，进行中的 Session 在另一个实例上可恢复。
4. `ContainerSandbox` 开启后，越界文件访问/越权命令在容器层被拦截（而非应用层判空），审计记录拦截事件。

### 5.4 明确不做

- 核心阶段原则延续：不引入 WebFlux/响应式重写；多实例不做有状态 sticky session（直接无状态化）；阶段三的跨节点 Agent 协作（Agent 通信底座）不在本文范围。

---

## 六、非功能需求增量

在原文档非功能需求之上追加：

1. **性能**：治理链路（Policy 判定、租户解析）对单次 Agent 调用的额外延迟 ≤ 5ms；审计查询 P95 ≤ 500ms（百万行内）。
2. **安全**：所有新增端点默认要求认证（FR-G05 落地后）；Policy 判定失败默认拒绝（fail-closed），不是默认放行。
3. **兼容**：扩展阶段所有能力以"新增实现类/新增模块"落地，不改核心阶段已冻结的接口签名；`AGENT.md` frontmatter 新增字段向后兼容。
4. **可回退**：每个波次（见调研文档第五章）独立可发布、独立可回退，git tag 标记。
5. **可演示**：每个 FR 完成必须有可演示成果，优先级是跑通而非完美（沿用核心阶段原则七）。

---

## 七、优先级汇总与依赖

```
P0：FR-G01/G02/G03、FR-C01/C02/C03、FR-R01          → 波次一
P1：FR-G04/G05/G06/G07、FR-C04/C05/C06/C07/C08、
    FR-M01/M02、FR-R02/R03                          → 波次二~三
P2：FR-G08/G09、FR-C09/C10、FR-M03/M04/M05/M06、
    FR-R04/R05/R06/R07/R08                          → 波次三~五
P3：FR-R09/R10                                      → 按信号
```

关键依赖链：FR-M02（受控沉淀）依赖 FR-G06 的角色语义（谁能批准）；FR-R07/R08 依赖 FR-R05/R06（状态不外置谈不上多实例）；FR-C04 的完整版依赖 FR-G07 的租户模型；FR-M05 的签名审核依赖 FR-G 的权限体系。详细排期见 `docs/roadmap/AiProgrammingGuide.md`。
