---
title: 非功能需求与验收
description: 性能、可靠性、安全合规，数据模型、里程碑与验收标准
---

# 非功能需求与验收

## 8. 非功能需求

### 8.1 性能

| 指标                  | 目标     |
| --------------------- | -------- |
| 单节点 Agent 数       | ≥ 10 个  |
| 单节点并发 Session 数 | ≥ 100 个 |
| Session 创建 P99 延迟 | ≤ 200ms  |
| OryxOS 内部转发开销   | ≤ 50ms   |

（LLM 调用本身的延迟取决于 Provider，不在 OryxOS 控制范围内）

### 8.2 可靠性

- 已注册的 Profile 配置和已写入的 Session 数据保证不丢
- LLM Provider 故障时核心阶段直接报错，完整 failover 在扩展阶段实现
- Tool 调用失败时按重试策略再调，默认指数退避最多三次

### 8.3 可运维性

- 配置变更通过 Profile YAML 文件修改，核心阶段重启服务生效
- 支持物理机、虚拟机、Docker、Kubernetes 部署

### 8.4 兼容性

- **JDK**：21 及以上（Spring Boot 3.x 要求）
- **操作系统**：Linux 主流发行版（Ubuntu 22.04+、CentOS 8+、Debian 11+、Alibaba Cloud Linux 3、Rocky Linux）
- **LLM 协议**：OpenAI 兼容协议是事实标准，只要 Provider 实现这套协议，OryxOS 就能直接接

### 8.5 安全

- API 调用支持 HTTPS
- 敏感配置（LLM API key、数据库密码、Tool 凭证）支持加密存储，不能明文写在配置文件里
- Tool 调用通过应用层白名单校验做基础隔离
- 完整的鉴权机制、Docker Sandbox 隔离、SSO 集成放在扩展阶段

### 8.6 合规

- **数据驻留**：OryxOS 不主动外发任何数据，所有数据留在企业自己的基础设施上
- 完整的审计日志覆盖、SIEM 导出、SOC 2、GDPR、HIPAA、等保三级的对接放在扩展阶段

---

---

## 10. 数据模型

### Profile（YAML 文件）

见 [5.2 Profile 配置](#52-profile-配置)。

### Session（持久化到 SQLite）

| 字段             | 类型      | 说明                                |
| ---------------- | --------- | ----------------------------------- |
| `session_id`     | VARCHAR   | 主键，channel+user+profile 联合生成 |
| `profile_name`   | VARCHAR   | 关联的 Profile 名称                 |
| `channel`        | VARCHAR   | 接入渠道                            |
| `user_id`        | VARCHAR   | 用户标识                            |
| `messages_json`  | TEXT      | JSON 序列化的对话历史               |
| `status`         | VARCHAR   | `active` / `archived`               |
| `created_at`     | TIMESTAMP | 创建时间                            |
| `last_active_at` | TIMESTAMP | 最后活跃时间                        |
| `archived_at`    | TIMESTAMP | 归档时间（可空）                    |

### Memory（文件形态，非数据库表）

长期记忆是 `.oryxos/memory/MEMORY.md` 一个 Markdown 文件，按追加方式写入，无结构化 schema。扩展阶段引入向量库后，Memory 才有结构化的 embedding 存储。

### Tool Invocation（记录每次 Tool 调用）

| 字段            | 类型      | 说明             |
| --------------- | --------- | ---------------- |
| `id`            | BIGINT    | 主键             |
| `session_id`    | VARCHAR   | 关联 Session     |
| `tool_name`     | VARCHAR   | Tool 名称        |
| `input_json`    | TEXT      | 调用参数（JSON） |
| `result_json`   | TEXT      | 执行结果（JSON） |
| `success`       | BOOLEAN   | 是否成功         |
| `error_message` | TEXT      | 错误信息（可空） |
| `duration_ms`   | BIGINT    | 执行耗时（毫秒） |
| `created_at`    | TIMESTAMP | 调用时间         |

### LLM Call（记录每次 LLM 调用）

| 字段                | 类型      | 说明             |
| ------------------- | --------- | ---------------- |
| `id`                | BIGINT    | 主键             |
| `session_id`        | VARCHAR   | 关联 Session     |
| `provider`          | VARCHAR   | Provider 名称    |
| `model`             | VARCHAR   | 模型名           |
| `prompt_tokens`     | INT       | 输入 token 数    |
| `completion_tokens` | INT       | 输出 token 数    |
| `total_tokens`      | INT       | 总 token 数      |
| `duration_ms`       | BIGINT    | 调用耗时（毫秒） |
| `created_at`        | TIMESTAMP | 调用时间         |

---

---

## 11. 里程碑规划

OryxOS 核心功能的实施按 **4 周节奏**组织，每周 3 小时，合计 12 小时。

| 周次       | 时间投入 | 能力主线                               | 可演示成果                                                         |
| ---------- | -------- | -------------------------------------- | ------------------------------------------------------------------ |
| **第一周** | 3 小时   | 对接 LLM + ReAct 循环（核心能力一+二） | `oryxos chat` 多轮对话，Agent 通过 ReAct 调 HTTP Tool 完成天气查询 |
| **第二周** | 3 小时   | Memory + Tool 体系（核心能力三+四）    | Agent 记住用户偏好并在后续对话用到，能调本地文件和外部 MCP server  |
| **第三周** | 3 小时   | Web Service（核心能力五）              | 外部系统通过 10 个 REST 端点完整调用 OryxOS                        |
| **第四周** | 3 小时   | 多 Agent 演示 + 工程化收尾             | 多 Agent 并存可用，CLI 完整，Session 跨重启恢复，项目主页可访问    |

### 各周详细实施内容

**第一周**（3 小时）：对接 LLM + ReAct 循环
- `oryxos init` 工作区初始化、Profile YAML 解析
- Provider 抽象（基于 Spring AI Alibaba，先跑通 DeepSeek 或 Kimi）
- ReAct 循环（核心循环约数十行 Java，含 LLM 调用、Tool 调用解析、消息累积）
- 一个基础内置 Tool（HTTP）、CLI Channel
- Session 管理（内存版，第四周加 SQLite 持久化）

**第二周**（3 小时）：Memory + Tool 体系
- Memory 长期记忆极简版（MEMORY.md 文件、`save_memory` 和 `recall_memory` 两个内置 Tool、启动时整个文件注入 system prompt）
- 文件操作 Tool（read_file、write_file、list_dir）、Shell Tool（带白名单校验）
- MCP Client 集成（连接外部 MCP server）

**第三周**（3 小时）：Web Service + API 端点
- Web Service 核心 10 个 REST 端点（会话管理 4 个、Agent 调用 1 个、Profile/Memory/Tool 列表 3 个、health/info 2 个）
- 通过 `oryxos serve` 启动 Spring MVC 服务
- 配置与密钥加载（环境变量注入 + 基础校验）

**第四周**（3 小时）：多 Agent 演示 + 工程化收尾
- 多 Agent 演示（配置两个不同 Profile 的 Agent 在同一实例并存）
- 命令行工具完整 12 个命令、Session 持久化到 SQLite（跨重启恢复）
- Bootstrap 文件机制（AGENTS.md、SOUL.md、USER.md 加载到系统提示词）
- 结构化日志、项目主页（VitePress 或类似静态站点工具）

---

---

## 12. 风险与未决事项

### 已识别风险

| 风险                        | 描述                                                   | 应对措施                                                                              |
| --------------------------- | ------------------------------------------------------ | ------------------------------------------------------------------------------------- |
| **核心功能范围风险**        | 4 周 12 小时是极紧的时间约束，某些功能可能比预期复杂   | 核心功能范围卡紧；某周完不成时立刻把末段功能挪到扩展功能，保证每周有可演示成果        |
| **Spring AI 兼容性风险**    | Function Calling、Stream、Token 计数、错误码细节不一致 | 核心阶段先把 OpenAI 协议跑稳，其他 Provider 在扩展阶段做完整回归测试                  |
| **Tool 执行安全风险**       | 应用层白名单不是完整 Sandbox，可能影响 OryxOS 进程     | 严格限制内置 Tool 能力范围，核心阶段不建议在生产环境跑高敏感场景                      |
| **Java 启动速度和内存占用** | Java 应用启动慢、内存占用大，影响体验                  | 核心阶段先验证功能完整，扩展阶段引入 GraalVM Native Image                             |
| **社区接力的不确定性**      | 扩展功能依赖社区贡献者，可能某些功能长期没人推进       | 项目维护方对核心扩展功能保持基本投入，社区共建功能靠社区                              |
| **定位被误读的风险**        | 社区可能问"核心阶段跟 OpenClaw、Hermes 有什么区别"     | 文档明确说明核心阶段是地基，差异化是终局，不包装成完整企业级 Agent OS                 |
| **生态关系风险**            | OryxOS 和 OpenClaw、Hermes 的关系                      | 通过 SKILL.md 互通，生态互补不竞争；OpenClaw 偏个人、Hermes 偏小团队、OryxOS 定位企业 |

### 未决事项

| 事项                           | 说明                                                                            | 决议时间       |
| ------------------------------ | ------------------------------------------------------------------------------- | -------------- |
| Provider 抽象接口设计          | 直接用 Spring AI `ChatClient`，还是在 `ChatClient` 之上加一层 OryxOS 自己的抽象 | 技术方案阶段   |
| 底层存储选 SQLite 还是 H2      | SQLite 是嵌入式 C 实现，H2 是纯 Java                                            | 技术方案阶段   |
| Bootstrap 文件加载顺序和优先级 | AGENTS.md、SOUL.md、USER.md 怎么组合进系统提示词                                | 技术方案阶段   |
| GraalVM Native Image 引入时机  | 核心阶段还是扩展阶段                                                            | 核心阶段结束后 |

---

---

## 13. 验收标准

### 功能验收

核心功能（第 5 章）全部完成，每个功能模块至少有一个端到端测试用例覆盖：

- [ ] `oryxos init` 工作区初始化
- [ ] Profile 配置和管理（支持多 Profile 并存）
- [ ] Provider 抽象（至少跑通 DeepSeek 和 Kimi 两个）
- [ ] ReAct 循环（多轮 Tool 调用、正确累积消息历史、达到最大迭代次数时正确终止）
- [ ] Memory 长期记忆（save_memory 写入、recall_memory 关键词检索、启动时注入 system prompt）
- [ ] 内置 Tool（文件、HTTP、Shell、save_memory、recall_memory、notify）
- [ ] Plugin Tool 接入（方式一零代码 SKILL.md + MCP 跑通；方式三 @Tool 注解示例跑通）
- [ ] MCP Client 集成、CLI Channel
- [ ] 定时任务 `AgentScheduler`（第三触发源，cron 到点自动触发，跟 CLI/Web Service 复用同一条 `AgentService` 链路）
- [ ] Web Service 核心 10 个 REST 端点全部跑通
- [ ] Session 持久化（SQLite，跨重启恢复）
- [ ] 12 个命令行工具
- [ ] 配置与密钥加载

### 性能验收

通过压力测试验证：
- 单节点 10 个 Agent 稳定运行 4 小时
- 单节点 100 个并发 Session
- Session 创建 P99 延迟 < 200ms
- 内部转发开销 < 50ms

### 可运维性验收

- 完整的部署文档（新手 30 分钟内完成单节点部署）
- 命令行工具有清晰的帮助和错误提示
- 项目主页可访问，讲清楚 OryxOS 是什么、怎么快速开始

### 场景验收（两个 Demo）

早期按"一个 Demo 验证一个能力"拆了五个 Demo，但真实场景从来不是单一能力独立跑的——一个能打动人的 Agent，一定是多个能力叠在一起、自己到点跑起来的。改成两个**每日自动运行**的端到端 Demo，每个 Demo 横向串起多个核心能力，两个 Demo 加起来覆盖全部五大核心能力加定时任务这个第三触发源。两个 Demo 跑通是核心功能发布的**硬条件**：

| Demo                      | 验证能力                                                                                                  | 场景描述                                                                                             | 验收标准                                                                                                                                                                                                                   |
| ------------------------- | --------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Demo 一：每日天气**     | 能力一+二（LLM + ReAct）、能力四（内置 HTTP Tool）、定时任务（`AgentScheduler`）                          | 每天早上到点自动查天气、生成穿搭建议，推送到企业 IM 群                                               | 不需要人工触发，到点自动跑完整 ReAct 循环；查天气和推送各一次 HTTP 调用，都过 Sandbox 域名白名单且都写入 `tool_invocations`；`GET /api/v1/sessions/{id}` 能查到这次自动触发的完整对话记录                                  |
| **Demo 二：每日科技日报** | 能力四（Plugin Tool 方式一 SKILL.md 零代码 + 方式二 MCP）、能力三（Memory）、定时任务（`AgentScheduler`） | 每天到点自动汇总当日科技新闻并推送，且日报内容会体现用户之前说过的关注方向（比如"更关注 AI 和芯片"） | 业务方全程不写 Java 代码，只写 SKILL.md + `mcp_servers.yaml` + Profile 的 `schedules` 字段；LLM 自己决定调新闻 MCP 工具、自己组织日报、自己调推送 MCP 工具，OryxOS 不解析任务步骤；日报内容能体现 `MEMORY.md` 里记住的偏好 |

两个 Demo 都是"钟推"（`AgentScheduler` 到点自动触发），但都要能同时支持"人推"手动补跑一次做验证（`oryxos chat` 或 `POST /agents/{name}/invoke`），验证同一个 Agent 不管从哪个入口触发，走的都是同一条 `AgentService` 链路。

---
