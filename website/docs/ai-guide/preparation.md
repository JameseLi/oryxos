---
title: 准备阶段
description: Spec-Kit 安装、constitution 七条非协商原则、specify 与 plan
---

# 准备阶段

## 3. 准备阶段

准备阶段是正式实施前的脚手架工作，由项目方完成，产出三份 Spec-Kit artifacts（constitution、spec、plan），让后续每个 user story 的实施都有清晰的依据。

### 3.1 Spec-Kit 安装 + Claude Code 配置

Specify CLI 是 Spec-Kit 的入口工具（Python 实现，需要 Python 3.11+，推荐用 `uv` 安装）。安装后通过 `specify init` 初始化 OryxOS 项目的 Spec-Kit 工作区，工作区里有 `.specify/memory/constitution.md` 以及 spec、plan、tasks 等 artifacts 的目录结构。

Claude Code 是主推的 AI agent，Spec-Kit 官方支持 Claude Code。具体集成方式（早期是 slash 命令，现在 Claude Code 走 skills 模式，初始化时通过参数指定）以官方文档为准。

---

### 3.2 `/speckit.constitution`：写 OryxOS 项目宪章

`constitution.md` 是项目的 **non-negotiable principles**，所有后续 spec、plan、tasks、implement 都要遵守。OryxOS 的 constitution 从需求文档第 3 章设计目标 + 技术方案第 1.1 节关键技术决策提炼：

| #   | 原则                                | 说明                                                                                                                                               |
| --- | ----------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | JDK 21 + Spring Boot 3.x 单体应用   | Maven 多模块（9 个），单二进制部署                                                                                                                 |
| 2   | 五大核心能力优先                    | 核心阶段交付运行时内核，企业级治理层放扩展阶段                                                                                                     |
| 3   | 自实现 ReAct loop                   | 不直接用 Spring AI 的 Agent 抽象                                                                                                                   |
| 4   | **Spring AI 只用一半**              | 只用 Provider 抽象、协议转换、@Tool schema 生成；**禁用自动 tool 执行**；tool 调度完全由 `ReActLoop` + `ToolExecutor` 控制。**最容易被写错的一条** |
| 5   | Plugin Tool 三档接入                | 主推 SKILL.md + MCP 零代码方式                                                                                                                     |
| 6   | SQLite + MEMORY.md 文件存储         | 向量检索放扩展阶段；`tool_invocations` 和 `llm_calls` **核心阶段就写入落库**                                                                       |
| 7   | 每个 user story 完成后有可演示 Demo | 优先级是跑通而非完美                                                                                                                               |

> `constitution.md` 写一次定下来，整个主体开发期间不改。如果中途发现某条原则不对，停下来重新讨论，**不允许 AI agent 自己修改 constitution**。

---

### 3.3 `/speckit.specify`：把需求文档转成 5 个 user story

`/speckit.specify` 命令的输入是需求文档，输出是 5 个 user story 的 spec，每个 user story 对应一个核心能力。

5 个 user story 按依赖关系排推进顺序，而不是按重要性。这里要特别说明：US-5 Web Service 排在最后实施，是因为它依赖前四个能力都就绪，**不是因为它不重要**。恰恰相反，Web Service 是 OryxOS 区别于个人助手项目的关键能力，重要性很高。本文档不用 P1/P2/P3 这种优先级标记，避免被误读成"靠后的可以不做"，只讲依赖顺序。

每个 user story 的 acceptance criteria 直接复用需求文档第 13 章 5 个验收 Demo：

| User Story  | 对应 Demo         | 场景                              |
| ----------- | ----------------- | --------------------------------- |
| US-1 + US-2 | Demo 一           | 查天气穿衣                        |
| US-3        | Demo 二           | 跨对话记偏好                      |
| US-4        | Demo 三           | 零代码 PR digest                  |
| US-5        | Demo 四 + Demo 五 | Web Service 同步调用 + 多端点联动 |

`/speckit.specify` 执行后生成 `spec.md`，AI agent 据此理解 OryxOS 整体要做什么。跑完后建议跑一次 `/speckit.clarify`，AI agent 会问几个澄清问题（比如 max iterations 默认值、对话历史截断策略等），这一步可选但推荐。

---

### 3.4 `/speckit.plan`：把技术方案转成实施 plan

`/speckit.plan` 命令的输入是技术方案 + 上一步的 `spec.md` + `constitution.md`，输出是实施 plan。Plan 包含：

- 技术栈选型（JDK 21 + Spring Boot 3.x + Spring AI Alibaba + SQLite + Picocli）
- 9 个 Maven 模块的职责（对照技术方案第 10 章）
- 关键技术决策的展开（自实现 ReAct、Spring AI 只用一半的边界、Plugin Tool 三档、SQLite + MEMORY.md、审计 day one 落库）
- 数据流和模块间协作（`PromptBuilder` + `ProviderService` + `ToolExecutor` + `MemoryService` 三层门面）

**Plan 生成后人工 review 是必要环节**。AI agent 可能根据自己对技术方案的理解做了不该做的取舍，重点检查：

- [ ] 有没有把 Memory 简化成跟 Session 合并（应该是 `MemoryService` 三层统一门面）
- [ ] 有没有把 Tool 又拆成多个模块（应该是合并的 `oryxos-tool` 一个模块）
- [ ] 有没有把 `AgentLoader`/`AGENT.md` 当成 Tool（Agent 目录应该归 core 的 `ContextLoader`，正文注入 prompt）
- [ ] 有没有启用 Spring AI 的自动 tool 执行（必须禁用）

Review 通过后 `plan.md` 锁定。

---

### 3.5 准备阶段交付物清单

准备阶段结束时，OryxOS 项目仓库里应该有：

```
.specify/
└── memory/
    └── constitution.md       # 非协商原则集
spec.md                       # 5 个 user story
plan.md                       # 技术栈 + 9 个模块 + 技术决策
docs/
├── DemandAnalysis.md         # 需求文档（来源参考）
├── TechnicalSolution.md      # 技术方案（来源参考）
├── IndustryResearch.md       # 业界调研（来源参考）
└── AiProgrammingGuide.md     # 本文档
```

准备阶段完成后，5 个 user story 的实施依据全部就绪，可以按依赖关系顺序推进。

---
