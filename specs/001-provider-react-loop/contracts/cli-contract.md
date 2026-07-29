# CLI 契约：oryxos init / oryxos chat

**Feature**: 001-provider-react-loop | **Status**: Draft
> 本特性只交付 12 个子命令中的 `init` 与 `chat`；其余属后续特性。

## oryxos init

在当前目录生成 `.oryxos/` 工作区（幂等，已存在则跳过并提示）。

```bash
oryxos init [--dir <path>]
```

生成物：
```text
.oryxos/
├── agents/                 # Agent 目录（一个子目录一个 Agent）
├── memory/MEMORY.md        # 长期记忆占位（US-3 启用）
├── sessions/  logs/        # 运行期产物目录
├── mcp_servers.yaml        # MCP 配置占位（US-4 启用）
└── AGENTS.md  SOUL.md  USER.md   # Bootstrap 文件（注入 system prompt）
```
退出码：`0` 成功 / `1` 失败（原因打印 stderr）。`oryxos.db` 首次 chat 时自动创建。

## oryxos chat

```bash
oryxos chat --agent <name> [--session <sessionId>]
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `--agent` | 是 | Agent 名（= `.oryxos/agents/<name>/AGENT.md`） |
| `--session` | 否 | 指定会话 id 追加历史；缺省新建会话 |

**启动即失败场景**（退出码 1，明确原因，FR-013 / US1-AS3）：
- Agent 目录或 `AGENT.md` 不存在
- frontmatter 引用的 provider 未在 `oryxos.providers` 配置
- provider 的 `api-key-env` 所指环境变量缺失

**会话内交互**：
- 输入一行即一轮提问；空行/纯空白 → 提示重新输入，不调模型（Edge Case）
- 单条 >32KB → 拒绝并提示上限（FR-003）
- `exit` / `quit` / Ctrl-C → 结束会话，历史已持久化（US1-AS4）

**输出形态**（FR-006/FR-011）：
```text
你> 北京今天天气怎么样，适合穿什么？
🔧 调用工具: http_get ...
✅ 完成 (1.2s)
🤖 北京今天晴，气温 12~22℃……建议薄外套。
```
工具失败时打印 `❌ 失败: <简述>`，随后模型兜底回答照常输出（FR-008）。
达到迭代上限 / 60s 超时 → 打印明确说明（FR-007/FR-010），会话可继续。

## 环境变量

| 变量 | 说明 |
|---|---|
| `DEEPSEEK_API_KEY` | provider type=deepseek 时由 `api-key-env` 引用 |
| `AI_DASHSCOPE_API_KEY` | provider type=dashscope 时引用 |
| 其他 | openai-compatible 型按配置自定义变量名 |

## 配置（oryxos-boot `application.yaml` 摘录）

```yaml
oryxos:
  react:
    max-iterations: 10        # FR-007
    invocation-timeout-seconds: 60   # FR-010
  providers:
    deepseek:
      type: deepseek
      model: deepseek-chat
      api-key-env: DEEPSEEK_API_KEY
    qwen:
      type: dashscope
      model: qwen-plus
      api-key-env: AI_DASHSCOPE_API_KEY
  storage:
    db-path: .oryxos/oryxos.db
```
