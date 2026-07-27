---
title: 快速开始
description: 构建 OryxOS、初始化工作区、跑起第一个 Agent
---

# 快速开始

## 环境要求

- **JDK 21+**
- **Maven 3.8+**

## 构建

```bash
git clone https://github.com/oryxos/oryxos.git
cd oryxos
mvn clean package
```

产物是单二进制 fat JAR：`oryxos-boot/target/oryxos.jar`。

## 初始化工作区

```bash
java -jar oryxos.jar init    # 在当前目录创建 .oryxos/ 工作区
```

初始化后的目录结构：

```
.oryxos/
├── agents/            # 一个子目录 = 一个 Agent（AGENT.md + 可选 skills/ scripts/）
├── memory/
│   └── MEMORY.md      # 长期记忆（核心记忆区 + 归档记忆区）
├── mcp_servers.yaml   # MCP server 配置
├── sessions/          # 会话数据
├── logs/              # 结构化日志
├── AGENTS.md          # Bootstrap：项目级 agent 行为说明
├── SOUL.md            # Bootstrap：默认 agent 人格定义
├── USER.md            # Bootstrap：用户偏好
└── oryxos.db          # SQLite
```

三个 Bootstrap 文件在 Agent 启动时自动加载到系统提示词，让 Agent 知道项目背景、自己的身份、用户偏好。

## 配置 LLM Key

敏感配置一律走环境变量，不明文落盘：

```bash
export DEEPSEEK_API_KEY=sk-xxx
```

Profile 里用 `${ENV_VAR}` 占位，加载时从环境变量解析，缺失或非法时给出清晰报错。

## 三种运行模式

| 模式 | 命令 | 说明 |
| --- | --- | --- |
| 交互对话 | `oryxos chat` | 交互式多轮对话，开发调试和日常使用的主要方式 |
| HTTP API | `oryxos serve` | 在指定端口（默认 8080）开放 RESTful 接口 |
| 守护进程 | `oryxos gateway` | 常驻进程，同时服务多个 Channel |

三种模式共享同一份配置和 Session 存储，差异只是接入层。

```bash
java -jar oryxos.jar chat                  # 交互对话
java -jar oryxos.jar chat --profile <name> # 指定 Agent 对话
java -jar oryxos.jar serve                 # 启动 REST API（:8080）
```

## 命令一览（12 个）

| 类别 | 命令 | 说明 |
| --- | --- | --- |
| 启动和状态 | `oryxos init` | 初始化工作区 |
| 启动和状态 | `oryxos status` | 查看配置和运行状态 |
| 启动和状态 | `oryxos chat [--profile <name>]` | 交互对话 |
| 启动和状态 | `oryxos serve` | 启动 HTTP API 服务 |
| 启动和状态 | `oryxos gateway` | 启动多渠道守护进程 |
| Profile 管理 | `oryxos profile list / create / show / delete` | 管理 Agent 配置 |
| 查询 | `oryxos provider list` | 列出已配置的 Provider |
| 查询 | `oryxos tool list` | 列出已注册的 Tool |
| 查询 | `oryxos session list` | 列出会话历史 |

## REST API（核心 10 端点）

```bash
# 会话管理
curl -X POST localhost:8080/api/v1/sessions
curl -X POST localhost:8080/api/v1/sessions/{id}/messages
curl      localhost:8080/api/v1/sessions/{id}
curl -X DELETE localhost:8080/api/v1/sessions/{id}

# Agent 无状态调用
curl -X POST localhost:8080/api/v1/agents/{name}/invoke

# 信息查询
curl localhost:8080/api/v1/profiles
curl localhost:8080/api/v1/memory
curl localhost:8080/api/v1/tools

# 系统状态
curl localhost:8080/api/v1/health
curl localhost:8080/api/v1/info
```

## 定义你的第一个 Agent

在 `.oryxos/agents/` 下放一个目录，就是一个 Agent：

```bash
mkdir -p .oryxos/agents/daily-weather
```

```markdown
---
name: daily-weather
provider: { name: deepseek, model: deepseek-chat }
tools: [http_get, notify]
schedules:
  - cron: "0 0 8 * * *"
    message: "查北京天气并给穿搭建议"
notify_channels:
  - type: webhook
    url: "${WECOM_WEBHOOK_URL}"
---
每天早上查询北京天气，生成穿搭建议，推送到运维群。
```

- `frontmatter` 是这个 Agent 的 profile（用哪个模型、能用哪些 Tool、定时规则、通知渠道）
- 正文是任务指令，注入 system prompt
- `schedules` 让它每天早上 8 点自动运行（钟推），也可以随时 `oryxos chat` 手动触发（人推）——同一个 Agent 走的都是同一条 `AgentService` 链路

目录还可以更丰富：`skills/*.md` 子指令、`scripts/` 脚本，由 LLM 按需经 `read_file`/`shell` 取用（渐进式披露）。详见 [定义一个 Agent](/docs/tech/define-agent)。

## 下一步

- [核心概念](/docs/concepts)：一个目录 = 一个 Agent、三个触发源一个入口、入站 Channel ≠ 出站 Notify
- [验收 Demo 与实施节奏](/docs/tech/demos)：每日天气、每日科技日报、每日 GitHub 日报三个完整示例
- [五大核心能力](/docs/demand/capabilities)：理解底座的全部能力
