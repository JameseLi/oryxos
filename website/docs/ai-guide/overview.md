---
title: 实施总览
description: Spec-Kit + 手动提示词的混合模式，按 user story 拆解与匹配度评估
---

# 实施总览

## 1. 实施总览

### 1.1 主体思路：Spec-Kit + 手动提示词的混合模式

OryxOS 的 AI 编程实施分两个阶段，两个阶段用不同的协作工具：

| 阶段             | 工具                     | 适用场景                                            |
| ---------------- | ------------------------ | --------------------------------------------------- |
| **主体开发阶段** | Spec-Kit                 | 从零开发 OryxOS 1.0 的五大核心能力，9 个 Maven 模块 |
| **增量开发阶段** | 手动提示词 + Claude Code | 扩展功能、修 bug、加 Plugin Tool 等小颗粒度增量     |

这两个阶段的边界很清晰：**Spec-Kit 适合大颗粒度 greenfield，手动提示词适合小颗粒度增量**。OryxOS 主体开发是前者，社区接力是后者，工具选择跟工作性质匹配。

---

### 1.2 跟需求文档 + 技术方案的关系

实施指引不重写需求和技术方案，而是把已有文档喂给 Spec-Kit。具体对应关系：

| Spec-Kit 输入           | 来源文档                                                | 作用                                            |
| ----------------------- | ------------------------------------------------------- | ----------------------------------------------- |
| `/speckit.specify` 输入 | 需求文档                                                | Spec-Kit 把需求文档转成 5 个 user story 的 spec |
| `/speckit.plan` 输入    | 技术方案                                                | Spec-Kit 把技术方案转成模块化的实施 plan        |
| `constitution.md`       | 需求文档第 3 章设计目标 + 技术方案第 1.1 节关键技术决策 | 非协商原则                                      |
| acceptance criteria     | 需求文档第 13 章 5 个验收 Demo                          | 直接复用                                        |

已有文档的投入不浪费，Spec-Kit 只是把它们转换成 AI agent 能直接消费的格式。

> **关键注意**：技术方案是 `/speckit.plan` 的输入，所以 plan 里的模块结构必须跟技术方案第 10 章的 9 个模块一致，喂文档时确保用的是最新版技术方案，否则生成的 plan 会按错误的模块数拆分。

---

### 1.3 拆解策略：按 user story 拆，不按时间拆

整个 OryxOS 主体开发按 **5 个 user story** 组织，每个对应一个核心能力。

5 个 user story 不是平行的，它们之间有明确的依赖关系，依赖关系决定推进顺序：

| User Story | 核心能力                  | 依赖             | 可并行       |
| ---------- | ------------------------- | ---------------- | ------------ |
| **US-1**   | 对接 LLM（Provider 抽象） | 无               | —            |
| **US-2**   | ReAct 循环（Agent 大脑）  | US-1             | —            |
| **US-3**   | Memory 三层记忆           | US-2             | 与 US-4 并行 |
| **US-4**   | Plugin Tool 体系          | US-2             | 与 US-3 并行 |
| **US-5**   | Web Service               | US-1 ~ US-4 全部 | —            |

**推进顺序**：US-1 → US-2 → （US-3 ∥ US-4）→ US-5

- US-1 是基础，没有 LLM 调用所有 Agent 能力都跑不起来
- US-2 依赖 US-1，ReAct 循环每轮都要调 LLM
- US-3 + US-4 并行依赖 US-2（Memory 注入 ReAct 的 prompt，Tool 被 ReAct 调用）
- US-5 依赖前 4 个，对外暴露所有能力

> 具体推进的时间投入由项目方根据团队情况决定。本文档按依赖顺序拆，不规定时长。需求文档和技术方案定的核心阶段节奏是 4 周每周 3 小时，5 个 user story 跟这个节奏的对应关系见技术方案第 12 章，本文档不重复。

这套 user story 拆法跟 Spec-Kit 的机制天然契合。Spec-Kit 的 `/speckit.tasks` 命令本身就是按 user story 组织任务的，每个 user story 成为一个独立的实施 phase，任务之间按依赖排序、可并行的标记出来。

---

---

## 2. Spec-Kit 跟 OryxOS 的匹配度评估

写实施计划之前先回答一个根本问题：Spec-Kit 真的适合 OryxOS 项目吗？

### 2.1 Spec-Kit 适合什么场景

Spec-Kit 是 GitHub 开源的 spec-driven development 工具链，是 2026 年增长最快的开发者工具之一，社区采用度很高，支持二十多种 AI coding agent。它把 AI 辅助编码结构化成可重复的 specify → plan → tasks → implement 流程，核心理念是让 spec 成为代码行为的契约和单一事实来源，把 AI agent 从"代码生成器"变成"按规格干活的协作者"，对治 vibe coding。

**社区共识适合的场景：**
- medium 到 large greenfield 项目（从零开发，工程量中到大，模块跨多个文件夹）
- 需求清晰（上游有明确的需求文档或产品决策）
- AI agent 协作（用 Claude Code、Copilot、Cursor 等做主体开发）
- 方法论场景（强制 spec-driven 流程，团队能学到工程方法论）

**不适合的场景：**
- 小 feature、快速原型、单文件改动（流程开销大于收益）
- 大型 brownfield 项目改造（legacy 代码上下文太复杂，超出 LLM context limit）
- 探索性研究项目（需求未定就跑 spec 会反复返工）

### 2.2 OryxOS 的匹配度判断

对照 Spec-Kit 适合的场景逐条评估 OryxOS：

| 评估维度      | OryxOS 情况                                                         | 匹配度     |
| ------------- | ------------------------------------------------------------------- | ---------- |
| greenfield    | 从零开发的全新项目，不是改造现有代码                                | ✅ 完全匹配 |
| 规模          | 9 个 Maven 模块、五大核心能力清晰，典型 medium 规模                 | ✅ 完全匹配 |
| 需求清晰      | 已有完整的需求文档 + 技术方案，五大核心能力都有 user story 级别描述 | ✅ 完全匹配 |
| AI agent 协作 | OryxOS 本来就是用 Claude Code 做主体开发                            | ✅ 完全匹配 |
| 方法论场景    | Spec-Kit 的强制流程让产出对齐需求，对开发者掌握工程方法论很有价值   | ✅ 匹配     |

**结论**：Spec-Kit 是 OryxOS 主体开发的最佳工具选择。社区在 brownfield 项目上对 Spec-Kit 有争议，但 OryxOS 是纯 greenfield，这些争议不适用。

### 2.3 Spec-Kit 的局限和应对

| 局限                                   | 描述                                     | OryxOS 的应对                                                    |
| -------------------------------------- | ---------------------------------------- | ---------------------------------------------------------------- |
| 流程对小增量过重                       | Spec-Kit 完整流程对小改动开销过大        | OryxOS 主体开发是大颗粒度，增量阶段切换到手动提示词              |
| spec 不会自动跟实现同步                | AI agent 在 implement 阶段可能偏离 spec  | 每个 user story 实施完成后跑 `/speckit.analyze` 做一致性检查     |
| context limit 在大型 brownfield 上失效 | 十万级文件的 legacy 项目 LLM 看不全      | OryxOS 是纯 greenfield，整个项目在 LLM context window 内，不适用 |
| Spec-Kit 本身在快速迭代                | 命令名、artifacts 格式、集成方式都还在变 | 本文档不锁定具体版本细节，具体命令以实施时官方文档为准           |

---
