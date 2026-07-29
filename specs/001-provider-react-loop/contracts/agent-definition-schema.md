# AGENT.md 定义 Schema（本特性子集）

**Feature**: 001-provider-react-loop | **解析方**: `AgentLoader` + `ContextLoader`（热读）

一个目录 = 一个 Agent：`.oryxos/agents/<name>/AGENT.md`。
frontmatter（YAML）派生运行时 Profile；正文为任务指令，原样注入 system prompt。

## 完整示例（验收用 daily-weather）

```markdown
---
name: daily-weather
provider: { name: deepseek, model: deepseek-chat }
tools: [http_get]
max_iterations: 10          # 可选，缺省用 oryxos.react.max-iterations
---
你是一个天气助手。用户问到天气时，调用 http_get 工具查询
https://wttr.in/<城市>?format=j1（如 https://wttr.in/北京?format=j1 ），
基于真实数据回答，并给出穿搭建议。
```

## 字段规约

| 字段 | 类型 | 必填 | 规约 |
|---|---|---|---|
| `name` | string | 是 | 必须与目录名一致，否则加载报错 |
| `provider.name` | string | 是 | 必须命中 `oryxos.providers.<name>`（FR-014 路由键） |
| `provider.model` | string | 是 | 模型标识，透传给连接器 |
| `tools` | string[] | 是（可空数组） | 只引用 `ToolRegistry` 已注册名；未知名称**启动即报** |
| `max_iterations` | int | 否 | 1~50，覆盖全局默认 10（FR-007） |
| `schedules` / `notify_channels` | — | 否 | 本特性**忽略**（Scheduler/Notify 属后续特性），解析不报错 |

## 加载与热读规则

- frontmatter 在 Agent 装载时解析一次（派生 Profile）；**正文每次组装 prompt 重新
  读取**（宪法：改完即时生效）。
- 文件编码 UTF-8；frontmatter 缺失/非法 YAML → 启动即报并指明行号。
- 正文为空 → 允许，等价于无任务指令的通用对话 Agent。

## 越界引用处理

| 场景 | 行为 |
|---|---|
| provider.name 不存在 | 启动失败，列出可用 provider 名（US3-AS3 同类语义） |
| tools 含未注册名 | 启动失败，列出已注册工具名 |
| max_iterations 越界 | 启动失败，提示合法区间 |
