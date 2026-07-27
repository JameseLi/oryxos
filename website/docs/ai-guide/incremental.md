---
title: 增量阶段与风险
description: 主体开发后的手动提示词模式、常见挑战与对策
---

# 增量阶段与风险

## 6. 增量阶段：手动提示词模式

### 6.1 为什么从 Spec-Kit 切换到手动提示词

主体开发完成后 OryxOS 进入增量阶段。这个阶段的工作性质跟主体开发完全不同：

| 维度       | 主体开发             | 增量开发                         |
| ---------- | -------------------- | -------------------------------- |
| 任务颗粒度 | 大（9 个模块同时建） | 小（加一个 Channel、补一个 Bug） |
| 涉及文件数 | 多（跨模块）         | 少（通常 1~3 个）                |
| 跨模块协作 | 有                   | 通常无                           |
| 上下文     | 从零设计             | 已有代码                         |

这种工作性质下 Spec-Kit 流程过重，跑一次完整的 constitution + specify + plan + tasks + implement 流程，开销大于单次任务的工作量本身。手动提示词配合 Claude Code 更适合：直接打开 Claude Code 描述要做的事，Claude Code 在已有代码上下文里直接修改，改完跑测试没问题就提 PR，不需要正式的 spec 和 plan artifacts。

### 6.2 增量开发的工作流

增量阶段的典型工作流：

```
1. 社区贡献者认领一个 issue（主仓库标注 good-first-issue / feature-request / long-term-goal）
2. 本地 fork + clone OryxOS
3. 用 Claude Code 打开项目，跟 Claude 描述要做的改动
4. Claude 在已有代码基础上修改、加测试、跑通
5. 提 PR 到主仓库
6. 项目方 review + merge
```

这个流程不强制走 Spec-Kit，每个贡献者按自己习惯做就行。对要求严格的大 feature 可以选择走 Spec-Kit，但不强制。

### 6.3 跟主体阶段 Spec-Kit Artifacts 的对接

主体阶段产出的 `constitution.md` 和 `spec.md` 在增量阶段仍作为参考文档保留在仓库里：

- **`constitution.md` 仍然是非协商原则**：社区贡献的代码必须遵守（JDK 21 + Spring Boot、自实现 ReAct、Spring AI 只用一半、Plugin Tool 三档等）
- **`spec.md` 是核心能力的契约**：社区贡献者改某个核心能力时要保证不破坏 spec 里的 acceptance criteria
- **`plan.md` 在主体阶段后基本不再更新**：技术方案文档作为社区参考保留

**新加 user story 的处理方式：**
- 小 feature：直接手动提示词 + PR
- 大 feature（涉及新增 Maven 模块、改 constitution、跨多个核心能力）：由项目方决定是否单独跑一次 Spec-Kit specify → plan → tasks 流程

---

---

## 7. 风险和注意事项

### 7.1 Spec-Kit 当前局限

Spec-Kit 还在快速迭代，工具本身变化频繁，使用时几个注意点：

| 注意点                         | 说明                                                                                                              |
| ------------------------------ | ----------------------------------------------------------------------------------------------------------------- |
| **版本锁定**                   | 实施前锁定 Specify CLI 一个具体版本号，整个主体开发期间不升级，命令名、artifacts 格式、集成方式可能在版本之间变化 |
| **官方文档随时查**             | 本文档讲思路 + 节奏，具体命令和安装方式以实施时官方文档为准                                                       |
| **community extension 谨慎用** | Spec-Kit 有 70 多个社区扩展，主体开发期间只用官方核心命令，不引入 extension 增加不确定性                          |

### 7.2 实施过程中的常见挑战

| 挑战                           | 对策                                                                                                        |
| ------------------------------ | ----------------------------------------------------------------------------------------------------------- |
| **AI agent 跑偏 constitution** | 每次跑完 implement 后人工检查，发现偏离立刻让 AI agent 重读 constitution 修正                               |
| **跨 user story 的上下文断裂** | 每个 user story 开始前让 AI agent 重读 `spec.md` + `plan.md` + 最近代码                                     |
| **`/speckit.analyze` 被跳过**  | 把 analyze 作为每个 user story 结束的硬性环节，不能省                                                       |
| **MCP server 集成踩坑**        | US-4 实施 MCP 前先用一个最简的 MCP server 测试连通性（stdio transport 可能遇到 process 启动失败、编码问题） |
| **Java 工程基础是前提**        | 实施前确保团队成员对 Spring Boot + Maven + JPA 有基本掌握                                                   |

---

---

## 8. 总结

OryxOS 的 AI 编程实施分两个阶段：

### 主体开发阶段（Spec-Kit）

已有的需求文档 + 技术方案喂给 Spec-Kit，转成 constitution + spec + plan + tasks 等 artifacts。准备阶段一次性把 constitution、spec、plan 准备好，然后按 5 个 user story 的依赖关系顺序实施：

```
US-1 → US-2 → ┌─ US-3 ─┐ → US-5
               └─ US-4 ─┘
```

每个 user story 完成后有可演示 Demo，对应需求文档第 13 章的 5 个验收 Demo。

### 增量阶段（手动提示词 + Claude Code）

小颗粒度增量不适合 Spec-Kit 完整流程，社区贡献者用 Claude Code 直接在已有代码上做改动，主体阶段产出的 constitution + spec 作为长期参考保留。

---

**Spec-Kit 跟 OryxOS 的契合度很高**：纯 greenfield、medium 规模（9 个模块）、需求清晰、AI agent 协作、方法论场景，每条都对得上。

**核心策略是已有文档喂给 Spec-Kit，不重写**。OryxOS 已经投入了完整的业界调研 + 需求文档 + 技术方案，这些是 Spec-Kit 的最佳输入。**关键是喂的是最新版文档**：模块是 9 个不是 11 个，constitution 要包含"Spring AI 只用一半"、"审计 day one 落库"这些新决策，否则 Spec-Kit 生成的 plan 会按旧结构走偏。
