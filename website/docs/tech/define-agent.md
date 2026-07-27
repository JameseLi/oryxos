---
title: 定义一个 Agent
description: 一个目录 = 一个 Agent：借 Agent Skills 形态、派生 Profile、渐进式披露
---

# 定义一个 Agent

## 11. 定义一个 Agent：一个目录 + Web Service

前面十章是**底座**——让任意 Agent 都能可靠运行的引擎、能力、支撑设施，本身不是某个具体的业务 Agent。这一章讲底座之上怎么真正"定义出一个业务 Agent"，以及这个动作通过哪个入口对外暴露。形态**借鉴 Anthropic Agent Skills**（目录 + 渐进式披露），但在 OryxOS 里，这样一个目录定义的是一个 **Agent**。

### 11.1 术语：一个目录 = 一个 Agent

**底座 / Agent 两层，分清楚。** 这是这一章最关键的一条：

- **底座 = 系统基础能力**：Provider、ReAct、内置 Tool（`read_file`/`shell`/`http_get`/`notify`/`save_memory`…）、Memory、Sandbox、定时、Web（第 1~10 章）。所有 Agent 共享。
- **Agent = 一个目录** `.oryxos/agents/<name>/`：`AGENT.md`（frontmatter = 这个 Agent 自己的 profile：`name`/`description`/`provider`/`model`/`tools`/`notify_channels`/`schedules`；正文 = 任务指令）+ 可选 `skills/*.md`（子指令）、`scripts/`（脚本）、`REFERENCE.md`（参考）。一个自足的业务 Agent，**自带一切、不再另写 Profile YAML**（`.oryxos/profiles/` 取消）。

**借 Anthropic Agent Skills 的形态、但定义的是 Agent。** Anthropic 把这种目录叫一个 Skill（Claude 这个大 Agent 的一项可加载能力）；我们借的是目录的**形态**，不是命名——在 OryxOS，**一个目录 = 一个 Agent**，"skill"只是 Agent 目录里的一个可选组成（子指令），**不做跨 Agent 的共享能力库、没有 `use_skill`、没有全局索引**。每个 Agent 独立自足，只调用底座的系统基础能力。

**派生 Profile**：底座（第 1~10 章的一切）都吃 `Profile`，所以 `AgentLoader.deriveProfile(agentDir)` 把 `AGENT.md` 的 frontmatter 映射成一个 `Profile`，让 Agent 目录**零改动复用整台底座**。

**渐进式披露（收进一个 Agent 内部）**：Agent 的**正文**在被触发时进 system prompt（它就是这个 Agent 的"人格 + 干什么"）；目录里的**子指令 / 参考 / 脚本不预载**，按正文指引**用底座既有能力按需取**——读子指令 / 参考用 `read_file`，跑脚本用 `shell`/`python`（脚本产出进上下文、代码不进）。没有新工具、没有能力库、没有全局索引。

> **底线不变（宪法原则四）**：`AGENT.md` 正文由 `ContextLoader` 注入 system prompt（与 Bootstrap 文件同层）；**一个 Agent 目录不是一个可执行 Tool**——它的子资源经底座既有的 `read_file`/`shell` 取用，不新造机制。

### 11.2 核心阶段：一个目录定义一个 Agent（文件系统）

核心阶段走文件系统，一步到位：

1. 在 `.oryxos/agents/<name>/` 放一个目录：至少一份 `AGENT.md`，需要子指令 / 脚本就加 `skills/*.md`、`scripts/`。
2. 启动时 `AgentLoader` 扫 `.oryxos/agents/`，对每个目录 `deriveProfile` → `ProfileRegistry.register`；有 `schedules` 的交 `AgentScheduler`。
3. 运行时：Agent 到点被触发，正文进 system prompt，子指令 / 参考 / 脚本按需经 `read_file`/`shell` 取用；`ContextLoader` / `read_file` 每次现取、不缓存，改正文即时生效。

配合运行时注册（11.3 的改造点在核心阶段就立好），新增 Agent 无需重启。

### 11.3 扩展阶段：`/api/v1/agents` + 一句话生成 + 实时监听 + 文件浏览器

业务系统 / 运营要完全通过 API 或页面管理、不摸文件系统。对外只有**一类资源——Agent**（一个目录）。`AgentLifecycleService`（在 26 节已有的 `AgentApiController` 上扩展）：

- `POST /api/v1/agents/generate`：一句话经 LLM 生成一份 **`AGENT.md` 草稿**原样返回（不落盘、不注册），供页面预览、修改（尤其 cron/tools 敏感项要人过一眼）
- `POST /api/v1/agents`：写 Agent 目录（`AGENT.md`[+ 脚本 / 子指令]）→ `deriveProfile` → 注册
- `GET /api/v1/agents` / `GET /{name}`：查询已定义的 Agent
- `PUT /api/v1/agents/{name}`：更新正文 / provider / notify（覆写即时生效）和/或 `schedules`（变则先注销旧句柄再注册新的）
- `DELETE /api/v1/agents/{name}`：注销定时 → 移出索引 → **整个 Agent 目录**归档 `.oryxos/archive/`（不物理删）
- `POST /api/v1/agents/{name}/invoke`：已有的无状态调用端点，不变


写完 Agent 目录后走的 `AgentLoader.deriveProfile → ProfileRegistry.register → AgentScheduler.registerProfile`，与启动扫描是**同一段代码**——保证"API 建的 Agent 和手工丢目录建的 Agent 行为一模一样"。`ProfileRegistry`（`register`/`remove`/`exists`）和 `AgentScheduler`（`registerProfile`/`unregisterProfile` + `scheduledTasks` 句柄表）的运行时注册方法在核心阶段（课程第 29 节）就已立好，本阶段直接调；`generate` 走既有 `ProviderService`（并落 `llm_calls` 审计）。

**一个目录、两条录入路径 + 实时监听。** `.oryxos/agents/` 是**唯一真相源**，填充它两条路殊途同归：API 上传（校验 + 写 Agent 目录）、手工丢目录（scp/git/编辑器）。本阶段新增 `WorkspaceWatcher`（装配层一个守护线程，用 JDK `WatchService`；启动全量扫 + 之后实时监听 `.oryxos/agents/` 变更），**它是统一注册入口**：任何 Agent 目录新增/改/删都调 `AgentLifecycleService.register(agentDir)`（与 API 上传写完目录后调的是**同一个方法**）或注销。于是"上传即上线 = 丢目录即上线、全程免重启"。此外 `WorkspaceApiController` 提供**只读**的工作区文件浏览（`GET /workspace/tree` 列 Agent 目录树、`GET /workspace/file?path=` 读文件内容，**必做防目录穿越**：`normalize()` 后 `startsWith(root)` 校验），供管理台"工作区"页钻进一个 Agent 目录看它的 `AGENT.md`/脚本/子指令。

### 11.4 为什么这几件事要打包在一起交付

单独做任何一件都拼不出"说一句话 / 调一次 API / 丢一个目录就上线一个会自动定时运行的新 Agent"这个闭环：少了目录写盘，派生不了 Profile；少了运行时注册，新 Agent 要等重启；少了定时注册，"会自己跑"是空话；少了一句话生成，运营还得手写 frontmatter。所以放在同一批一起交付，不拆开先做一半。

### 11.5 三个例子

下一章（12.1~12.3）的三个 Demo——每日天气、每日科技日报、每日 GitHub 日报——各演一种 **Agent 目录丰富度**：**天气 Agent 是光杆 `AGENT.md`**（正文写指令，用内置 HTTP Tool + `schedules`）；**科技日报 Agent 是 `AGENT.md` + `skills/` 子指令**（组稿规范单独一份，Agent 用 `read_file` 按需读，配 MCP / Memory）；**GitHub 日报 Agent 是 `AGENT.md` + `scripts/` 脚本**（含 `scripts/github_trending.py`，Agent 用 `shell` 跑脚本拿确定性数据）。核心阶段先靠 11.2 的手动路径跑通，扩展阶段升级到 11.3 的统一 API 后，不用重启进程就能上新 Agent。

---
