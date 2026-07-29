# Data Model: Provider 抽象 + ReAct Loop

**Date**: 2026-07-29 | **Source**: spec.md Key Entities + FR-016/017/018

## 持久化实体（SQLite，`scripts/sql/001-init.sql` 为唯一事实来源）

### sessions — 会话

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | TEXT | PK | UUID |
| agent_name | TEXT | NOT NULL | 绑定 Agent（= `.oryxos/agents/<name>`） |
| channel | TEXT | NOT NULL | 渠道来源，本特性固定 `cli` |
| user_id | TEXT | NOT NULL | 本特性固定 `local` |
| created_at | TEXT | NOT NULL | ISO-8601 |
| updated_at | TEXT | NOT NULL | ISO-8601 |

### session_messages — 会话消息（按 seq 有序）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | TEXT | PK | UUID |
| session_id | TEXT | FK→sessions.id, NOT NULL | |
| seq | INTEGER | NOT NULL | 会话内单调递增 |
| role | TEXT | NOT NULL | `user` / `assistant` / `tool` |
| content | TEXT | | 文本内容（tool 角色存执行结果） |
| tool_calls_json | TEXT | | assistant 角色：模型请求的工具调用 JSON |
| tool_call_id | TEXT | | tool 角色：对应的调用 id |
| created_at | TEXT | NOT NULL | |

唯一约束：`(session_id, seq)`。校验：单条 content ≤32KB（FR-003 在入口处拦截）。

### llm_calls — LLM 调用审计（只增不改，FR-016）

| 列 | 类型 | 说明 |
|---|---|---|
| id | TEXT PK | UUID |
| session_id | TEXT FK | 所属会话（invoke 场景可为空） |
| provider_name / model | TEXT NOT NULL | 路由证据（FR-014 验收依据） |
| iteration | INTEGER NOT NULL | ReAct 第几轮（0 起） |
| request_json / response_json | TEXT | 请求/响应快照，各 ≤16KB 截断 |
| request_bytes / response_bytes | INTEGER | 快照原始字节数（截断前），供完整性核对 |
| prompt_tokens / completion_tokens | INTEGER | token 用量（可空，取决于 provider 返回） |
| duration_ms | INTEGER NOT NULL | |
| success | INTEGER NOT NULL | 0/1 |
| error_message | TEXT | 失败原因（FR-016） |
| created_at | TEXT NOT NULL | |

### tool_invocations — 工具调用审计（只增不改，FR-017）

| 列 | 类型 | 说明 |
|---|---|---|
| id | TEXT PK | UUID |
| session_id | TEXT FK | |
| tool_name | TEXT NOT NULL | |
| arguments_json | TEXT | 入参 |
| result_preview | TEXT | 结果截断预览（≤4KB，防大结果爆库） |
| duration_ms | INTEGER NOT NULL | |
| success | INTEGER NOT NULL | 0/1 |
| error_message | TEXT | |
| created_at | TEXT NOT NULL | |

> 截断策略（对称防膨胀，FR-016/017）：llm_calls 请求/响应快照各 ≤16KB，原始字节数
> 记入 request_bytes/response_bytes；tool_invocations 结果预览 ≤4KB（失败详情以
> error_message 为准）。由 AuditRecorder（tasks T012）统一执行。

## 配置态实体（非持久化，运行时对象）

- **AgentDefinition**（来自 `AGENT.md`，见 contracts/agent-definition-schema.md）：
  `name, provider{name, model}, tools[], maxIterations?, systemPrompt(body)`
- **ProviderConfig**：`name, type(deepseek|dashscope|openai-compatible), model,
  baseUrl?, apiKeyEnv` —— 密钥本体只存在于环境变量，对象中仅存变量名。
- **会话内消息模型**（ReAct 循环内）：user / assistant(含 toolCalls) /
  toolResponse 三类，与 Spring AI `Message` 体系映射，落库时转换为上表行。

## 状态与流转

- 会话：`active`（REPL 存活期间）→ 退出即终态，无显式状态列（updated_at 推断）。
- ReAct 单轮：`LLM_CALL → (tool 分支: TOOL_EXEC → LLM_CALL)×N → FINAL |
  MAX_ITERATIONS | TIMEOUT`，每次 LLM_CALL/TOOL_EXEC 即写一行审计（FR-018：
  即使最终失败，已写行不丢）。

## 关系

```text
sessions 1───* session_messages
sessions 1───* llm_calls
sessions 1───* tool_invocations
AgentDefinition *───1 ProviderConfig（按 provider.name 绑定，FR-014）
```
