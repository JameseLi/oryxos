# Implementation Plan: Provider 抽象 + ReAct Loop（可调用工具的多轮对话）

**Branch**: `001-provider-react-loop` | **Date**: 2026-07-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-provider-react-loop/spec.md`

## Summary

打通 OryxOS 的"对话心脏"：Provider 抽象层（多 LLM 可配置、可切换、零 lock-in）+
自实现 ReAct 循环（模型决策 → 工具执行 → 结果回填 → 继续推理，上限 10 轮）+
CLI `chat` REPL（多轮对话、工具调用一行式进度）。审计（`llm_calls` /
`tool_invocations`）day one 落库。技术路线（详见 [research.md](research.md)）：
**直接调用 `ChatModel`（官方语义：不自动执行 tool call），ToolCallbacks 仅作
schema 载体，工具执行全部走 OryxOS 自己的 `ToolExecutor`**——完美契合宪法原则 IV。
Spring Boot 3.4.1 → 3.5.16 升级（仍为 3.x，宪法允许），对齐 Spring AI 1.1.8 +
Spring AI Alibaba 1.1.2.3 稳定线。

## Technical Context

**Language/Version**: Java 21（virtual thread 用于 Agent 调用超时控制）

**Primary Dependencies**:
- Spring Boot **3.5.16**（自 3.4.1 升级，保持 3.x 宪法约束；对齐 Spring AI 1.1.x）
- Spring AI **1.1.8**（`spring-ai-bom`；1.x 最新稳定线，2.0.0 强制 Boot 4.x 违宪不可用）
  - `spring-ai-deepseek`（DeepSeek connector 核心包）
  - `spring-ai-openai`（OpenAI 兼容协议兜底：Kimi/智谱/混元/豆包等）
  - **显式不引入 `*-starter`：starter 带入的 autoconfigure 会无条件索取 API key 且与
    程序化构造路线冲突（实施实证：dashscope autoconfigure `matchIfMissing=true` 无密钥
    拒绝启动）；ChatModel 全部由 ProviderRegistry 手工 new，自动装配零参与**
- Spring AI Alibaba **1.1.2.3**（`spring-ai-alibaba-bom` + `spring-ai-alibaba-dashscope` 核心包，通义）
- Spring Data JPA + `sqlite-jdbc` 3.49.1.0 + `hibernate-community-dialects`（对齐 Boot 管理的 Hibernate 6.6.x）
- Picocli 4.7.6（现有）、SnakeYAML（Boot 自带，解析 AGENT.md frontmatter）

**Storage**: SQLite（`.oryxos/oryxos.db`）；DDL 手写脚本 `scripts/sql/001-init.sql` 经
`spring.sql.init` 执行，`ddl-auto=none`（宪法：不依赖自动迁移）

**Testing**: JUnit 5 + Spring Boot Test + Mockito；ReAct 循环单测用 stub `ChatModel`
（脚本化返回 tool_call → 文本）；工具执行用假 `OryxTool`；E2E 走 quickstart 手工验证
（需真实 API key）

**Target Platform**: 企业内网 Linux/macOS，单 fat JAR + CLI 交互

**Performance Goals**: SC-001 首轮回答 <60s；SC-002 单工具问题 60s 内闭环（均含网络）

**Constraints**: 迭代上限 10 次；单次调用整体超时 60s；单条消息 ≤32KB；核心阶段无认证
（内网假设）、CORS N/A（无 Web）；非流式输出；密钥只走环境变量

**Scale/Scope**: 单用户 CLI 交互会话；审计写入每轮约 1~10 行；不涉及并发会话竞争

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | 原则 | 判定 | 证据/说明 |
|---|------|------|-----------|
| I | 固定技术栈与九模块结构 | ✅ | 只在既有 9 模块内加类，不增删模块；Boot 3.4.1→3.5.16 仍属"Spring Boot 3.x"；不引 GraalVM/WebFlux/响应式 |
| II | 核心阶段只做运行时内核 | ✅ | 本特性=US-1+US-2；不含 SSE/WebSocket/认证/向量检索/Tool Policy；Scheduler 与 Web 为后续特性 |
| III | 自实现 ReAct 循环 | ✅ | `ReActLoop`（oryxos-core）自实现循环与上限控制；不依赖 Spring AI Agent 抽象，也不用 `ChatClient`+`ToolCallingAdvisor` |
| IV | Spring AI 只用一半 | ✅ | 直接 `ChatModel.call(prompt)`——官方文档明确该路径**不自动执行** tool call；`ToolCallback` 仅用于 JSON Schema 生成；Provider 解析用**程序化构造的显式 `Map<providerName, ChatModel>`**（`ProviderRegistry`），不做容器类型扫描；天然支持两家同类型 OpenAI 兼容 Provider 并存（自动配置无法做到的场景） |
| V | 三档接入·零代码优先 | ✅ | 交付 AGENT.md 目录加载（ContextLoader）+ ToolRegistry/`OryxTool` 机制；内置仅最小 `http_get`（spec 边界），完整 9 工具与 MCP 属 US-4 |
| VI | 审计 Day One 落库 | ✅ | `llm_calls`、`tool_invocations` 两张表 + JPA 实体 + 手写 DDL 脚本，本特性即写入；非仅日志 |
| VII | 每个 Story 可演示 | ✅ | quickstart 验收：CLI 天气问答（人推路径）；钟推路径属后续 Scheduler 特性（spec Assumptions 已声明） |
| 约束 | 密钥环境变量 | ✅ | Provider api-key 配置只存环境变量名，`ConfigLoader` 启动校验缺失即报（FR-013） |
| 约束 | 不用 SecurityManager | ✅ | Sandbox 接口 + WhitelistSandbox 应用层域名白名单 |
| 约束 | AGENT.md 热读 | ✅ | ContextLoader 每次组装 prompt 重新读文件，不缓存 |
| 约束 | Schema 手动演进 | ✅ | `scripts/sql/001-init.sql` 为准，`ddl-auto=none` |
| 约束 | 接口中立 | ✅ | `ToolProgressListener`/`Sandbox`/`OryxTool` 签名无实现特定词 |

**Gate 结论：全部通过，无违规项。**

## Project Structure

### Documentation (this feature)

```text
specs/001-provider-react-loop/
├── plan.md              # 本文件
├── research.md          # Phase 0：版本选型与关键 API 决策
├── data-model.md        # Phase 1：实体与 DDL
├── contracts/           # Phase 1：CLI 契约 + AGENT.md schema
│   ├── cli-contract.md
│   └── agent-definition-schema.md
├── quickstart.md        # Phase 1：端到端验证指南
├── checklists/requirements.md
└── spec.md
```

### Source Code (repository root)

```text
oryxos-core/                          # 引擎层（新类）
└── src/main/java/com/oryxos/core/
    ├── react/ReActLoop.java                 # 自实现循环（上限10/审计埋点/超时）
    ├── react/PromptBuilder.java             # system(AGENT.md+Bootstrap)+历史+工具清单
    ├── react/OryxToolSchemaAdapter.java     # OryxTool→ToolCallback 仅作 schema 载体（call 永不执行）
    ├── agent/AgentService.java              # 三触发源统一入口 process()（本特性仅 CLI 调用）
    ├── agent/AgentLoader.java               # AGENT.md 加载 + frontmatter 派生 Profile
    ├── agent/ContextLoader.java             # 每次热读 AGENT.md 正文/Bootstrap
    ├── session/SessionManager.java          # 会话上下文维护（委托 storage 持久化）
    ├── tool/OryxTool.java                   # 工具抽象接口（中立签名）
    ├── tool/ToolExecutor.java               # 调度执行+失败回填+进度事件+审计
    ├── tool/ToolProgressListener.java       # 进度回调接口（FR-011，中立）
    └── audit/AuditRecorder.java             # llm/tool 调用落库门面

oryxos-provider/
└── src/main/java/com/oryxos/provider/
    ├── ProviderProperties.java              # oryxos.providers.<name>.{type,model,base-url,api-key-env}
    ├── ProviderRegistry.java                # 程序化构造显式 Map<name,ChatModel>（宪法 IV）
    ├── ProviderService.java                 # 按 Agent 绑定解析 ChatModel；启动校验密钥
    └── ChatModelFactory.java                # deepseek/dashscope/openai-compatible 三型构造

oryxos-tool/
└── src/main/java/com/oryxos/tool/
    ├── registry/ToolRegistry.java           # 名称→OryxTool 注册表
    ├── builtin/HttpGetTool.java             # 最小验收工具（域名白名单受控）
    └── sandbox/{Sandbox,WhitelistSandbox}.java  # 应用层白名单（无 SecurityManager）

oryxos-storage/
└── src/main/java/com/oryxos/storage/
    ├── entity/{SessionEntity,SessionMessageEntity,LlmCallEntity,ToolInvocationEntity}.java
    ├── repository/{SessionRepository,SessionMessageRepository,LlmCallRepository,ToolInvocationRepository}.java
    └── resources/scripts/sql/001-init.sql   # 手写 DDL（唯一事实来源）

oryxos-channel-cli/
└── src/main/java/com/oryxos/channel/cli/CliChannel.java   # REPL+一行式进度渲染（FR-011）

oryxos-cli/
└── src/main/java/com/oryxos/cli/
    ├── OryxosCli.java                       # Picocli 主入口（CommandLineRunner 分发）
    ├── InitCommand.java                     # 生成 .oryxos/ 工作区
    └── ChatCommand.java                     # --agent/--profile 进入 CliChannel

oryxos-boot/
└── src/main/resources/application.yaml      # oryxos.* 配置（providers/react/storage）
```

**Structure Decision**: 严格落在既有 9 模块骨架内；`OryxTool` 接口在 core、实现与
Registry 在 oryxos-tool（依赖方向：tool → core），与 AGENTS.md §6 模块表一致。

## Complexity Tracking

> 无违规项——本表无需填写。
