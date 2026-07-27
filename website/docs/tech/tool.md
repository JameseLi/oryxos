---
title: Tool 体系
description: OryxTool 抽象、九个内置 Tool、Plugin Tool 三档接入、Sandbox 与 NotifyTools
---

# Tool 体系

## 6. 核心能力四：Tool 体系

Tool 是 Agent 可以调用的外部能力。OryxOS 的 Tool 分两类：**内置 Tool** 由 OryxOS 提供，**Plugin Tool** 由业务方扩展。Plugin Tool 有三种接入方式，按门槛从低到高排。

> **模块调整说明：** 核心阶段 Tool 相关合并为一个 `oryxos-tool` 模块（内置 Tool、MCP Client、`ToolRegistry`、Sandbox 都在里面），不拆成 builtin/skill/mcp 三个模块。原因是它们共享同一个 `OryxTool` 抽象和 `ToolRegistry`，耦合度高，核心阶段没必要拆细。
>
> 另外，一个 Agent 目录（`AGENT.md` 及其 `skills/` 子指令）严格说不是一种 Tool，而是上下文来源（正文注入 system prompt、子指令经 `read_file` 按需读，见 11.1），因此 `AgentLoader`/`ContextLoader` 不放在 Tool 体系里，归到上下文加载那一层（见 8.3）。这样概念更齐整。

### 6.1 OryxTool 抽象

OryxOS 内部统一的 Tool 抽象接口。内置 Tool、`@Tool` 注解的 Plugin Tool、MCP Tool 都被包装成 **`OryxTool`** 实例注册到 `ToolRegistry`，ReAct 循环不感知具体 Tool 的来源。

`OryxTool` 接口约定四个核心方法：

- `getName`
- `getDescription`
- `getInputSchema`（JSON Schema）
- `execute`（接收 JSON 输入返回 `ToolResult`）

`ToolResult` 包含成功标识、结果内容、错误信息、是否可重试。


### 6.2 内置 Tool（九个）

核心阶段提供九个内置 Tool，分五组：

- **`FileTools`**：`read_file`、`write_file`、`list_dir`，执行前调用 `Sandbox.enforce(...)` 做路径白名单检查
- **`ShellTools`**：`shell` Tool 执行 bash 命令，带超时和命令白名单
- **`HttpTools`**：`http_get`、`http_post`，带域名白名单
- **`MemoryTools`**：`save_memory`、`recall_memory`（归 Memory 模块，但作为内置 Tool 注册）
- **`NotifyTools`**：`notify`（把消息推送到 Profile 配置好的通知渠道，详见 6.8）

这九个覆盖"让 Agent 能读写文件、跑命令、调外部 API、记事、往外推通知"的最短链路。


### 6.3 Plugin Tool 方式一：零代码 AGENT.md 目录 加复用 MCP

OryxOS **主推**的接入方式。业务方不写代码，只写一个 Agent 目录描述要做的事，LLM 自己理解任务、自己组合调用 MCP 工具。

定义一个 Agent = 写一个目录 `.oryxos/agents/<name>/`（借 Anthropic Agent Skills 的目录形态，详见第 11 章）：`AGENT.md` = frontmatter（这个 Agent 自己的 profile）加任务说明正文，外加可选的 `skills/*.md`（子指令）、`scripts/*.py`、`REFERENCE.md` 等资源。一个目录就是一个自足的 Agent，只调用底座的系统基础能力。

加载走**一个 Agent 内部的渐进式披露**：`AGENT.md` 正文进 system prompt，正文里指向的子指令 / 脚本 / 参考经底座 `read_file`/`shell` 按需读跑。LLM 读到后自己理解任务、自己决定调哪个 MCP 工具、自己组合完成——OryxOS 不解析任务步骤、不做工作流引擎，所有逻辑交给 LLM。

> 注意 `AGENT.md` 的解析由 `AgentLoader` / `ContextLoader`（8.3）负责，不在 Tool 模块里——它是 prompt 的输入源、不是可执行 Tool（见宪法原则四与 11.1）。

### 6.4 Plugin Tool 方式二：自己写 MCP server

业务方用任何语言写 MCP server，通过 MCP 协议暴露工具，OryxOS 作为 MCP Client 连接进来。MCP server 配置在 `.oryxos/mcp_servers.yaml`，声明 `name`、`transport`、`command`、`env`。

**`McpClientService` 子模块。** MCP server 的连接维护和工具注册。OryxOS 启动时连接所有配置的 MCP server，调 `tools/list` 拿工具列表，把每个 MCP 工具包装成 `OryxTool` 注册到 `ToolRegistry`，处理 server 失联、超时、错误恢复。

**`McpToolAdapter` 子模块。** 把 MCP Tool 适配成 `OryxTool` 接口。Tool 调用时通过 MCP 协议（JSON-RPC over stdio 或 SSE）转发给对应 MCP server 执行，结果包装成 `ToolResult` 返回。

### 6.5 Plugin Tool 方式三：写 Java Spring Bean

用 Spring AI `@Tool` 注解标注 Java 方法，OryxOS 启动时自动扫描注册。工程量最大但集成深度最好，适合需要直接调用企业内部 Java 服务、复用现有 Spring Bean、跟 Spring Security 集成做权限控制的场景。写法跟 OryxOS 内置 Tool 完全一样，直接在进程内调用 Java 方法，不走 MCP 协议、不起独立进程、不序列化，性能最好。

### 6.6 ToolRegistry

统一管理所有 Tool。启动时通过 Spring 容器扫描所有 `@Tool` 注解的方法（内置 Tool 和方式三的 Plugin Tool），加上 MCP Client 注册的工具（方式二），全部包装成 `OryxTool` 实例。Profile 启动 Agent 时按 `tools` 字段从 Registry 过滤出该 Profile 可用的 Tool 子集。

### 6.7 Sandbox 检查

Sandbox 遵循"接口先行"原则：先定一个不携带任何实现细节的抽象接口，核心阶段只在接口后面挂一档实现，未来加重隔离方案时只新增实现类，不改接口、不改调用方。

**`Sandbox` 接口。** 只有一个方法，表达"在受控环境里执行一个动作"这个意图：

```text
Sandbox.enforce(SandboxAction action)

SandboxAction  = { type: ActionType, target: String }
ActionType     = FILE_READ | FILE_WRITE | SHELL_COMMAND | HTTP_REQUEST
```

> ActionType 取四值（文件读 / 文件写 / Shell 命令 / HTTP 请求）——文件读写分开便于未来按读/写分权限；`WhitelistSandbox` 的 `enforce` 把 `FILE_READ`、`FILE_WRITE` 两 case 同路由到 `checkFilePath`。

接口签名里不出现"白名单""容器镜像""VM 配置"这类某一档实现特有的词——用最重的 microVM 实现去反向套这个签名，也应该能干净套入，这是校验接口是否中立的办法。

**`WhitelistSandbox`（核心阶段唯一实现）。** 配置在 `application.yaml`（`file.allowed_paths`、`shell.allowed_commands`、`http.allowed_domains`），内部按 `ActionType` 路由到三个私有校验方法：

- `checkFilePath`（路径标准化后比对白名单，需处理 `../` 路径穿越）
- `checkShellCommand`（拆出命令首个 token 比对白名单）
- `checkHttpUrl`（解析 host 后做通配符匹配）

任意校验失败抛 `SandboxViolationException`，Tool 执行终止；异常信息直接复用 `ToolExecutor` 已有的失败审计路径写入 `tool_invocations`（`success=false`、`error_message`），不需要为 Sandbox 单独新增审计逻辑。

`FileTools`、`ShellTools`、`HttpTools` 在各自 `execute` 方法开头调用 `sandbox.enforce(...)`，校验通过才执行真正的 IO：


**扩展阶段按信号驱动升级，接口不变，只新增实现类：**

| 阶段       | 实现                                             | 升级信号                             |
| ---------- | ------------------------------------------------ | ------------------------------------ |
| 核心阶段   | `WhitelistSandbox`（应用层 Path/Pattern 白名单） | —                                    |
| 扩展阶段一 | 容器隔离（namespace + cgroups + seccomp）        | 要跑相对不可信代码，或要做多租户     |
| 扩展阶段二 | microVM（Firecracker / Kata / gVisor）           | 要跑完全不可信代码，或要规模化多租户 |

> **要点一：** 应用层白名单是"劝阻级"防线，防的是模型犯傻误操作，防不住蓄意绕过，核心阶段不建议用它跑完全不可信的代码或对外做多租户。
>
> **要点二：** Sandbox 加白名单是核心阶段唯一的 Tool 治理手段，而 Profile 级的 Tool Policy（哪个 Agent 能用哪些 Tool）放在扩展阶段。核心阶段 Profile 的 `tools` 字段已经能限定 Agent 可用 Tool 子集，算是 Tool 治理的雏形，完整的 allow/deny 策略扩展阶段补。

### 6.8 通知推送（`NotifyTools`）

一句话：**入站有 Channel Adapter 负责"消息怎么进来"，出站现在缺一个对称的东西负责"消息怎么出去"——`NotifyTools` 补的就是这一块。**

在两个验收 Demo 出现之前，OryxOS 没有真正需要"主动往外推一条消息"这件事——CLI 和 Web Service 都是别人发消息进来、Agent 回一句话，回复方式是同步返回，不需要额外的推送机制。但"每日天气""每日科技日报"这类定时触发的 Demo 不一样：`AgentScheduler` 到点自动跑，没有人在等着看响应，Agent 必须**主动**把结果送到人能看到的地方（企业 IM 群），这就需要一个"往外推"的能力。

**如果没有这个模块会怎样：** 每个业务方定义 Agent 时都要自己在 Skill 里手写"调 `http_post` 打这个 webhook URL"，或者自己去找一个企业微信/飞书的 MCP server 配上——每个 Skill 各写一份，重复且不统一，跟前面 Sandbox、Memory 强调的"接口先行"原则相反。

**`NotifyChannelAdapter` 接口。** 跟 6.7 Sandbox 同样的思路：先定一个不携带具体渠道细节的抽象接口，表达"把一条内容送到某个通知目标"这个意图：

```text
NotifyChannelAdapter.send(NotifyTarget target, String content)

NotifyTarget = { channelType: String, config: Map<String, String> }
```

**`WebhookNotifyAdapter`（核心阶段唯一实现）。** 用通用 HTTP webhook 承接所有场景——企业微信、飞书、钉钉的群机器人都提供 webhook 地址，核心阶段不用逐家接它们的专用 API（签名算法、AccessToken 刷新这些认证细节核心阶段不做），直接把 `content` 包成对方 webhook 约定的 JSON 格式发一次 POST。发送前一样要过 `Sandbox.enforce(new SandboxAction(HTTP_REQUEST, url))` 域名白名单校验，跟 `http_post` 共享同一份 `http.allowed_domains` 配置，不新增 Sandbox 逻辑。

**`NotifyTools`（内置 Tool，归 `oryxos-tool`）：**

```text
@Tool notify(content: String, channel: String = 默认渠道)
```

`channel` 参数对应 Profile 新增的 `notify_channels` 字段（声明这个 Agent 能推送到哪些目标，每项带 `type` 和渠道特定配置如 `url`）；LLM 大多数时候只需要传 `content`，不需要知道具体 webhook 地址——地址是运行时配置，不是对话里的信息，这跟 Sandbox 域名白名单"配置在 Profile/`application.yaml`，不暴露在接口签名里"是同一个设计考虑。


**跟已有机制的关系：**

- **审计：** `notify` 跟其他 Tool 一样走 `ToolExecutor` 现有的成功/失败审计路径，写入 `tool_invocations`，不新增审计逻辑。
- **Sandbox：** 复用已有的 `Sandbox.enforce(HTTP_REQUEST, ...)`，不新增沙箱概念。
- **跟入站 Channel 的对称关系，但不是同一个东西：** `ChannelAdapter`（8.4）解决"什么触发 Agent 开始跑"，`NotifyChannelAdapter` 解决"Agent 跑完把结果送到哪"——语义方向相反，所以分开建模，不合并成一个抽象；同一个企业微信群，可能同时是某个 Agent 的入站 Channel、又是另一个 Agent 的出站通知目标。
- **和 Plugin Tool 方式二（MCP）的边界：** 如果业务方需要企业微信官方富文本卡片消息这种更复杂的格式，`notify` 简单场景之外仍然可以走 MCP 方式二自己接一个专用 MCP server，两条路并存，`notify` 只是把"最常见的纯文本/webhook 推送"这个重复劳动统一掉，不是要吃掉 MCP 方式二的场景。

---
