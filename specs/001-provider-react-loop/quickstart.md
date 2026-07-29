# Quickstart 验证指南: Provider 抽象 + ReAct Loop

**Feature**: 001-provider-react-loop | **目标**: 端到端证明 spec 的 6 条 SC 全部达成

## 前置条件

- JDK 21+、Maven 3.8+
- 至少一个真实 API key：`export DEEPSEEK_API_KEY=sk-xxx`
  （SC-006 需第二个：`export AI_DASHSCOPE_API_KEY=sk-yyy`）

## 构建与初始化

```bash
git checkout 001-provider-react-loop
mvn clean package -q          # 期望：BUILD SUCCESS（9 模块）
java -jar oryxos-boot/target/oryxos.jar init
# 期望：生成 .oryxos/ 工作区（agents/ memory/ sessions/ logs/ + Bootstrap 文件）
```

创建验收 Agent（见 [agent-definition-schema.md](contracts/agent-definition-schema.md)）：

```bash
mkdir -p .oryxos/agents/daily-weather
cat > .oryxos/agents/daily-weather/AGENT.md <<'EOF'
---
name: daily-weather
provider: { name: deepseek, model: deepseek-chat }
tools: [http_get]
---
你是天气助手。用户问天气时调用 http_get 查询 https://wttr.in/<城市>?format=j1，基于真实数据回答并给穿搭建议。
EOF
```

## 场景 A：多轮对话（US1 / SC-001）

```bash
java -jar oryxos-boot/target/oryxos.jar chat --agent daily-weather
你> 我叫小李，在北京工作
🤖 ...(60s 内回答，记住名字)...
你> 我叫什么名字？
🤖 你叫小李……
你> exit
```
**期望**：追问正确引用前文；`exit` 后 `.oryxos/oryxos.db` 的 `sessions` /
`session_messages` 已有记录（US1-AS4）。

## 场景 B：工具调用闭环（US2 / SC-002、FR-011）

```text
你> 北京今天天气怎么样？适合穿什么？
🔧 调用工具: http_get ...
✅ 完成 (1.2s)
🤖 北京今天……（回答中含工具返回的真实数据）
```
**期望**：60s 内一次提问闭环；进度行可见；`llm_calls` ≥2 行（首轮回 tool_call +
回填后轮）、`tool_invocations` 1 行，均有耗时与 success=1（SC-005）。

无工具问题对照（US2-AS3）：问"1+1 等于几" → 无 🔧 行，直接回答。

## 场景 C：失败兜底（SC-004 / FR-007/008）

1. 断网后提问 → 明确错误提示，会话不崩，可继续提问。
2. 临时把天气 URL 指到不可达域名提问 → `❌ 失败: ...` 后模型给出兜底说明。
3. 构造持续要求调工具的提问 → 第 10 轮后系统终止并明示"达到推理步数上限"。

## 场景 D：Provider 切换（US3 / SC-006）

1. `application.yaml` 增加 `qwen` provider（type: dashscope，见
   [cli-contract.md](contracts/cli-contract.md) 配置摘录）。
2. 复制 Agent 目录为 `daily-weather-qwen`，frontmatter 改
   `provider: { name: qwen, model: qwen-plus }`。
3. `chat --agent daily-weather-qwen` 重复场景 B → 表现一致；全程零代码改动，
   耗时 <5 分钟。`llm_calls.provider_name` 可核对两 Agent 各自路由（FR-014）。

## 场景 E：启动校验（FR-013 / US1-AS3）

```bash
unset DEEPSEEK_API_KEY
java -jar oryxos-boot/target/oryxos.jar chat --agent daily-weather
# 期望：启动即失败，提示"环境变量 DEEPSEEK_API_KEY 缺失"，而非首轮调用才报错
```

## 审计核对（SC-005）

```bash
sqlite3 .oryxos/oryxos.db \
  "select provider_name,model,iteration,success,duration_ms from llm_calls;
   select tool_name,success,duration_ms from tool_invocations;"
```
**期望**：100% 的调用有行；失败行 error_message 非空。同时检查应用日志：**审计
写失败的结构化 ERROR 日志计数必须为 0**（AuditRecorder 兑底路径，SC-005 闭环；
见 tasks.md T012）。

## 验收对照

| SC | 验证场景 |
|---|---|
| SC-001 首轮 <60s | A |
| SC-002 单工具 60s 闭环 | B |
| SC-003 ≤3 步 90% 自主完成 | B（多步提问变体） |
| SC-004 异常 100% 优雅结束 | C |
| SC-005 审计 100% 留痕 | B + 审计核对 |
| SC-006 切换 <5min 零代码 | D |
