---
title: 核心功能与关键流程
description: 核心阶段最短链路：工作区、Profile、Provider、ReAct、Memory、Tool、Channel、Web Service、CLI 与五个关键流程
---

# 核心功能与关键流程

## 5. 核心功能

> 核心功能是核心阶段 4 周（合计 12 小时）内必须完成的最短链路，对应 Agent OS 的运行时内核。目标是跑通一个完整链路：用 Profile 配置一个 Agent，通过 CLI 跟它对话，它能调用 LLM 和工具完成任务，并能通过 REST API 对外暴露。

### 5.1 工作区初始化

OryxOS 的工作目录是 `.oryxos/`，通过 `oryxos init` 命令初始化。

```bash
oryxos init   # 在当前目录下创建 .oryxos/ 工作区
```

初始化后的目录结构：

```
.oryxos/
├── profiles/          # Profile 配置（每个 Agent 一个 YAML）
├── sessions/          # 会话历史
├── skills/            # SKILL.md 文件
├── logs/              # 结构化日志
├── tools/             # 自定义 Tool 配置
├── memory/
│   └── MEMORY.md      # 长期记忆文件
├── AGENTS.md          # Bootstrap：项目级 agent 行为说明
├── SOUL.md            # Bootstrap：默认 agent 人格定义
├── USER.md            # Bootstrap：用户偏好
└── profiles/
    └── default.yaml   # 默认 Profile
```

- 三个 Bootstrap 文件在 Agent 启动时被自动加载到系统提示词，让 Agent 知道项目背景、自己的身份、用户偏好
- `oryxos init` 同时生成一份默认 Profile，用最简配置让用户立刻可用

---

### 5.2 Profile 配置

Profile 是 Agent 的运行时宿主配置，用 YAML 文件描述——决定一个 Agent 绑定哪个 Skill、用哪个 Provider/模型、能用哪些 Tool、绑定哪个 Channel、要不要定时。Profile 本身是 Agent OS 内核层的能力（底座），"这个 Agent 具体做什么"由它引用的 Skill 定义，两者绑定在一起才构成一个完整的业务 Agent。

**Profile YAML 结构：**

```yaml
name: string                    # Profile 名称
description: string             # 描述

identity:
  agent_name: string            # Agent 名称
  prompt: string                # 人格/系统提示词（或引用 SOUL.md）

provider:
  name: string                  # Provider 名称（deepseek/qwen/kimi 等）
  model: string                 # 模型名
  temperature: float            # 温度参数（可选）

tools:
  - string                      # 可用 Tool 名称列表

skills:
  - string                      # 引用的 SKILL.md 文件列表

mcp_servers:
  - string                      # 引用的 MCP Server 列表

channels:
  - name: string                # Channel 名称
    config: {}                  # Channel 配置

bootstrap:
  - string                      # Bootstrap 文件列表

settings:
  max_iterations: 10            # 最大 ReAct 迭代次数
  max_history_turns: 20         # 最大对话历史轮数
```

**Profile 管理命令：**

```bash
oryxos profile create <name>    # 创建新 Profile
oryxos profile list             # 查看所有 Profile
oryxos profile show <name>      # 查看 Profile 详情
oryxos profile delete <name>    # 删除 Profile
```

核心阶段支持创建并管理多个 Profile，多个 Agent 可以在同一个 OryxOS 实例上并存，这是"OS"在核心阶段的最小体现。

---

### 5.3 Provider 抽象（核心能力一：对接 LLM）

Provider 是 LLM 调用的统一抽象。所有 LLM 调用通过 Provider 接口走，Agent 不感知具体调的是哪家。

核心阶段直接基于 Spring AI Alibaba 的 `ChatClient` 实现。Spring AI Alibaba 已经做好了主流 LLM（DeepSeek、通义、文心、Kimi、智谱、混元、豆包、Anthropic、OpenAI 等）的 connector，OryxOS 把它们包装成 Provider，不重复造轮子。

每个 Provider 实例配置：
- `provider 名`（deepseek、qwen、kimi 等）
- `模型名`
- `API key`
- `可选的 base URL`

**核心阶段不做**：fallback 和 hedge racing。Provider 故障时直接报错给 Agent；成本透明只做基础版（每次 LLM 调用记录 token 使用量、Provider、模型落到日志）。

---

### 5.4 ReAct 循环（核心能力二：Agent 大脑）

ReAct 循环是 Agent 的核心工作机制，也是 OryxOS 最关键的一段代码。

**核心算法（Reason + Act）：**

```
接到用户消息
  └─ 追加到 Session 对话历史
     └─ 组装 Prompt（system prompt + Bootstrap + 对话历史 + 可用 Tool 列表）
        └─ 调用 LLM Provider 获取响应
           ├─ [无 Tool 调用] → 返回最终响应
           └─ [有 Tool 调用] → 执行 Tool，把结果追加到对话历史 → 继续循环
```

达到最大迭代次数（默认 10 次）强制结束。

**实现要点：**
- 核心循环约数十行 Java 代码，自己实现而不依赖 Spring AI 的 Agent 抽象
- 最大迭代次数可在 Profile 里覆盖
- 每次 LLM 调用和 Tool 调用都记录结构化日志

**核心阶段不做**：Tool 调用并行、上下文动态压缩、Agent 间任务委托。

---

### 5.5 Memory 三层记忆（核心能力三：让 Agent 记得住）

**核心阶段实现会话 + 长期两层（情景记忆放扩展阶段）：**

#### 会话记忆

- 当前对话的完整历史，按 Channel + 用户 + Profile 联合标识
- Session 数据持久化到本地 SQLite，重启后可以恢复
- 上下文超过 LLM context window 上限时简单截断早期对话

#### 长期记忆（极简版）

- 存在 `.oryxos/memory/MEMORY.md` 一个 Markdown 文件，跨所有对话保留
- Agent 通过两个内置 Tool 主动读写：
  - `save_memory(content)`：把要长期记住的事追加到 MEMORY.md
  - `recall_memory(query)`：按关键词检索 MEMORY.md 里的相关内容
- Agent 启动时 MEMORY.md 整个文件作为长期上下文注入到 system prompt
- 文件超过 4000 字时简单截断（扩展阶段做压缩）

**核心阶段不做**：自动从对话中抽取事实、语义检索（用关键词匹配）、情景记忆、Memory Wiki、矛盾检测。

**用户核心体验**：用 OryxOS 一段时间后，Agent 自然会记住用户的偏好、项目信息、关键决策，下一次对话不需要重新解释。这是 Agent OS 区别于 chatbot 的核心体验。

---

### 5.6 Tool 体系（核心能力四：让 Agent 能干事）

Tool 是 Agent 可以调用的外部能力。Agent 通过 LLM Function Calling 决定何时调哪个 Tool，OryxOS 负责 Tool 的注册、查找、调用、结果回传。

#### 内置 Tool（核心阶段 5 个）

| Tool                     | 类型   | 说明                                   |
| ------------------------ | ------ | -------------------------------------- |
| `read_file`              | 文件   | 读取文件内容，受路径白名单限制         |
| `write_file`             | 文件   | 写入文件内容，受路径白名单限制         |
| `list_dir`               | 文件   | 列出目录，受路径白名单限制             |
| `shell`                  | Shell  | 执行 bash 命令，有超时和命令白名单限制 |
| `http_get` / `http_post` | HTTP   | 发起 HTTP 请求，有域名白名单限制       |
| `save_memory`            | Memory | 把内容追加到 MEMORY.md                 |
| `recall_memory`          | Memory | 按关键词检索 MEMORY.md                 |

#### Plugin Tool（业务方扩展）

| 方式                                      | 门槛   | 推荐度   | 场景                           |
| ----------------------------------------- | ------ | -------- | ------------------------------ |
| **方式一**：写 SKILL.md + 复用 MCP server | 零代码 | ⭐⭐⭐ 主推 | 描述意图，LLM 自己组合现成能力 |
| **方式二**：自己写 MCP server             | 轻代码 | ⭐⭐       | 接入企业自有系统，任何语言皆可 |
| **方式三**：写 Java @Tool Bean            | 重代码 | ⭐        | 深度集成，性能最好             |

> **选择原则**：能用方式一就不用方式二，能用方式二就不用方式三。

**零代码示例**：想做"每天早上推送昨日 GitHub PR 评审进度到 Slack"，只需：
1. 写 `daily-pr-digest.md` 描述任务和触发时机
2. 复用社区现成的 `github-mcp` 和 `slack-mcp`
3. 配置 Profile 引用这个 Skill 和两个 MCP server

整个过程不写一行代码。

#### Sandbox 安全隔离

核心阶段用应用层白名单校验实现：
- 文件操作：路径白名单
- Shell：命令白名单
- HTTP：域名白名单
- 执行超时和资源占用限制

> 注：不使用 Java SecurityManager，它在 JDK 17 起已废弃、JDK 21 已不可用。完整的 Docker/K8s 容器级沙箱放在扩展功能。

---

### 5.7 Channel 接入

Channel 是 Agent 对外的消息接入入口，主要解决"消息进来、响应出去"这件事。HTTP 接入归 Web Service，不在 Channel 范畴内。

核心阶段只内置一种 Channel：**CLI Channel**，通过 `oryxos chat` 命令启动，支持多轮对话、查看上下文、查看 Tool 调用记录。

企业微信、飞书、钉钉、Slack 等 IM Channel 放在扩展功能（实现复杂度高，需要 OAuth 和企业资质，不在 12 小时核心阶段能完成的范围）。

---

### 5.8 Web Service（核心能力五：对外接口暴露）

Web Service 是 OryxOS 的对外完整门面，业务系统通过 REST API 接入 OryxOS 的所有能力。这是 OryxOS 区别于偏个人定位的 OpenClaw、Hermes 的关键能力。

#### 核心阶段 10 个关键端点

| 类别         | 端点                                  | 说明        |
| ------------ | ------------------------------------- | ----------- |
| 会话管理     | `POST /api/v1/sessions`               | 创建会话    |
| 会话管理     | `POST /api/v1/sessions/{id}/messages` | 发消息      |
| 会话管理     | `GET /api/v1/sessions/{id}`           | 查历史      |
| 会话管理     | `DELETE /api/v1/sessions/{id}`        | 归档会话    |
| Agent 调用   | `POST /api/v1/agents/{name}/invoke`   | 无状态调用  |
| Profile 信息 | `GET /api/v1/profiles`                | 列 Profile  |
| Memory 操作  | `GET /api/v1/memory`                  | 查长期记忆  |
| Tool 信息    | `GET /api/v1/tools`                   | 列可用 Tool |
| 系统状态     | `GET /api/v1/health`                  | 健康检查    |
| 系统状态     | `GET /api/v1/info`                    | 运行信息    |

#### 扩展阶段补齐的端点（15 个）

Profile 的 show/reload/create/update/delete；Memory 的 append/clear/search；Tool describe 和调用历史查询；LLM call 历史、token 统计；Webhook 触发、流式 SSE 响应；Prometheus metrics、OpenAPI spec。

**核心阶段不做**：认证机制（无认证假设内网）、流式响应 SSE、WebSocket、RBAC 权限。

#### 业务系统集成场景

| 模式         | 方式                                  | 适用             |
| ------------ | ------------------------------------- | ---------------- |
| 同步调用     | `POST /agents/{name}/invoke` 等返回   | Stateless 短任务 |
| 会话保持     | 先创建 Session，后续多次发消息        | 连续对话         |
| Webhook 触发 | 告警系统、CI/CD 通过 Webhook 调 Agent | 事件驱动         |
| 跨语言集成   | 任何能发 HTTP 请求的语言都能接入      | 通用集成         |

---

### 5.9 Session 管理

Session 是用户和 Agent 一次对话的上下文容器，包含起止时间、用户身份、Agent 标识、对话历史、当前上下文、临时变量。Session 标识由 Channel、用户、Agent 联合生成。

核心阶段 Session 数据持久化到本地 SQLite（`.oryxos/sessions/` 下）。重启 OryxOS 后，正在进行的 Session 可以恢复。Session 上下文超过 LLM 的 context window 时，简单截断早期对话保留近期对话。

---

### 5.10 三种运行模式

| 模式     | 命令             | 说明                                                                               |
| -------- | ---------------- | ---------------------------------------------------------------------------------- |
| 交互对话 | `oryxos chat`    | 交互式多轮对话，开发调试和日常使用的主要方式。`--message "xxx"` 可发单条消息后退出 |
| HTTP API | `oryxos serve`   | 启动后在指定端口（默认 8080）开放 RESTful 接口，业务系统通过 HTTP 调用             |
| 守护进程 | `oryxos gateway` | 常驻守护进程，同时服务多个 Channel                                                 |

三种模式共享同一份 Profile 配置和 Session 存储。

---

### 5.11 命令行工具

核心阶段实现 **12 个命令**：

| 类别         | 命令                             | 说明                  |
| ------------ | -------------------------------- | --------------------- |
| 启动和状态   | `oryxos init`                    | 初始化工作区          |
| 启动和状态   | `oryxos status`                  | 查看配置和运行状态    |
| 启动和状态   | `oryxos chat [--profile <name>]` | 交互对话              |
| 启动和状态   | `oryxos serve`                   | 启动 HTTP API 服务    |
| 启动和状态   | `oryxos gateway`                 | 启动多渠道守护进程    |
| Profile 管理 | `oryxos profile list`            | 列出所有 Profile      |
| Profile 管理 | `oryxos profile create <name>`   | 创建新 Profile        |
| Profile 管理 | `oryxos profile show <name>`     | 查看 Profile 详情     |
| Profile 管理 | `oryxos profile delete <name>`   | 删除 Profile          |
| 查询         | `oryxos provider list`           | 列出已配置的 Provider |
| 查询         | `oryxos tool list`               | 列出已注册的 Tool     |
| 查询         | `oryxos session list`            | 列出会话历史          |

---

### 5.12 配置与密钥加载

核心阶段做基础版：

- 敏感配置通过**环境变量**注入或独立的本地配置文件加载，不明文写死在 Profile YAML 里
- Profile 里用 `${ENV_VAR}` 占位，加载时从环境变量解析
- 配置加载时做基础校验（必填项、格式），缺失或非法时给出清晰报错

完整的加密存储、密钥轮转、对接企业密钥管理系统（KMS、Vault）放在扩展阶段。

---

### 5.13 项目主页

OryxOS 作为开源项目，需要一个独立的主页作为对外门面，讲清楚 OryxOS 是什么、能干嘛、怎么用，引导开发者快速上手。

主页在核心阶段做出来，与核心代码同期发布，作为 OryxOS 1.0 对外亮相的一部分。技术栈推荐使用 VitePress、Astro 或 Docusaurus 等静态站点生成器。

---

---

## 9. 关键流程

### 流程一：工作区初始化

```
用户执行 oryxos init
  → OryxOS 创建 .oryxos/ 目录及五个子目录
  → 创建三个 Bootstrap 文件（AGENTS.md、SOUL.md、USER.md）
  → 生成默认 Profile（profiles/default.yaml）
用户编辑 Bootstrap 文件填入项目背景、Agent 人格、用户偏好
用户编辑 default.yaml 配置 LLM Provider 的 API key 和模型
```

### 流程二：Profile 创建和 Agent 启动

```
用户执行 oryxos profile create <name>
  → OryxOS 在 .oryxos/profiles/ 下创建新 YAML 文件
用户编辑配置 Agent 人格、Provider、Tool 列表、Channel
用户执行 oryxos chat --profile <name>
  → OryxOS 加载 Profile
  → 初始化 Provider 连接
  → 注册 Tool 到 Agent 工具池
  → 把 Bootstrap 文件加载到系统提示词
  → Agent 进入待对话状态
```

### 流程三：消息处理（最高频链路）

```
消息从 Channel 进来（CLI 输入或 HTTP API 调用）
  → Channel Adapter 转换成内部统一格式
  → Agent 查询 Session 上下文
  → 组装 LLM Prompt（Bootstrap + 对话历史 + 可用 Tool 列表）
  → 调用 LLM Provider 获取响应
  → [有 Tool 调用] → OryxOS 执行 Tool → 结果回传给 LLM 继续生成
  → 最终响应通过 Channel Adapter 发回给用户
  → 所有动作落结构化日志
```

### 流程四：Tool 调用

```
LLM 通过 Function Calling 指明 Tool 名称和参数
  → OryxOS 从 Agent 工具池找到对应 Tool
  → 做参数校验和白名单校验
  → 内置 Tool：在 OryxOS 进程内执行（白名单约束下）
  → MCP Tool：通过 MCP 协议转发给对应 MCP server 执行
  → 执行结果（成功/失败、错误信息、可重试标识）回传给 Agent
  → Agent 把 Tool 结果作为新一轮 LLM 输入继续推理
```

### 流程五：Session 上下文管理

```
用户第一次跟 Agent 说话
  → OryxOS 用 Channel+用户+Agent 联合 ID 查活跃 Session
  → [无活跃 Session] → 创建新 Session，初始化空对话历史
  → [有活跃 Session] → 恢复 Session 上下文
后续消息追加到 Session 对话历史
  → [上下文超限] → 截断早期对话，保留近期（扩展阶段做总结压缩）
Session 超时无消息 → 结束，对话历史归档可查
```

---
