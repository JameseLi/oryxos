---
title: 关键技术决策
description: JDK 21 + Spring Boot 3.x 单体下的 7 个关键取舍，含 Spring AI 只用一半
---

# 关键技术决策

## 1. 方案概述

OryxOS 是一个 **Spring Boot 3.x** 单体应用，跑在 **JDK 21** 上，基于 **Spring AI Alibaba** 做 LLM 调用，自己实现 **ReAct loop** 作为 Agent 核心。整个 OryxOS 是一个可执行 JAR，单二进制部署，扩展阶段引入 **GraalVM Native Image** 进一步压缩启动时间和内存占用。

> **技术栈选型一句话总结**：JDK 21 + Spring Boot 3.x + Spring AI Alibaba + 自实现 ReAct loop + SQLite + Picocli 命令行。

### 1.1 关键技术决策

需求文档定义了五大核心能力，下面统一列出 7 个关键决策的取舍。先用一张表速览，再逐条展开。

| #   | 决策                | 选择                                                                                                         | 理由                                                                                                                         |
| --- | ------------------- | ------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------- |
| 1   | ReAct loop 实现方式 | 自实现，不依赖 Spring AI Agent 抽象                                                                          | 完全可控，保留未来定制循环行为的空间                                                                                         |
| 2   | Spring AI 使用边界  | 只用 Provider 抽象 + 协议转换 + `@Tool` schema 生成，禁用自动 tool 执行                                      | 避免 tool 被调两次，ReAct 循环完全由 OryxOS 自己掌控                                                                         |
| 3   | 执行模型            | 同步阻塞 + Java 21 virtual thread                                                                            | 直观简洁，无需响应式编程，单节点撑高并发                                                                                     |
| 4   | Tool 注册机制       | `@Tool` 注解 + **OryxTool** 抽象层                                                                           | 统一内置 Tool 和 MCP Tool 接口，ReAct 循环不感知 Tool 来源                                                                   |
| 5   | HTTP 服务层         | Spring MVC + Java 21 virtual thread                                                                          | 同步直观，单机撑千级并发，扩展阶段 `SseEmitter` 支持流式                                                                     |
| 6   | Sandbox 策略        | 接口先行：`Sandbox` 抽象 + `WhitelistSandbox`（应用层 Path/Pattern 白名单）实现，扩展阶段按容器→microVM 演进 | `SecurityManager` 在 JDK 17 起废弃、JDK 21 已不可用，与 JDK 21+ 要求冲突；接口独立于白名单实现，未来换重隔离方案不用改调用方 |
| 7   | 持久化方案          | SQLite + Spring Data JPA + `MEMORY.md` 文件                                                                  | 单二进制，审计表 day one 写入，避免后期从日志反解析返工                                                                      |

**决策一：自己实现 ReAct loop。** Spring AI 负责 LLM 调用、Function Calling 的协议格式转换、Provider 抽象这些底层工作，ReAct loop 自己写，保证 Agent 核心完全可控，也保留未来定制循环行为的空间。

**决策二：明确划清 Spring AI 的使用边界。** 这是最容易埋 bug 的地方，单列为一条决策。Spring AI 自身带有一套完整的 tool calling 自动执行机制（能自动执行 tool 再把结果回灌给模型）。

OryxOS **不使用**这套自动执行，只用 Spring AI 的两件事：
- 一是 Provider 抽象和向各家 LLM 的协议转换
- 二是 `@Tool` 注解的 JSON Schema 生成

Tool 的实际调度和执行完全由 OryxOS 自己的 **`ReActLoop`** 加 **`ToolExecutor`** 控制。换句话说，Spring AI 在 OryxOS 里只做协议适配器和 schema 生成器，不做循环引擎。研发时必须禁用 Spring AI 的自动 tool 执行，否则会出现 tool 被调两次的问题。

**决策三：同步执行模型。** 核心阶段采用同步阻塞执行模型，跟 Spring MVC 一致。一次消息从进来、ReAct loop 执行、Tool 调用、Provider 调用到最终响应返回，全程同步。这跟 Java 21 的 virtual thread 配合得很好，单节点支撑高并发不需要响应式编程。扩展阶段引入流式输出（SSE）和异步 Tool 调用。

**决策四：Tool 注册机制用 `@Tool` 注解加 `OryxTool` 抽象层。** Spring AI 注解负责扫描 Java 方法生成 JSON Schema，OryxOS 在其上加一层 **`OryxTool`** 抽象，统一内置 Tool 和 MCP Tool 的接口形式，让 ReAct loop 不感知 Tool 来源。注解的确切名称和用法以采用的 Spring AI 版本为准，研发前需对当前版本核实。

**决策五：HTTP 服务层用 Spring MVC 加 Java 21 virtual thread。** 同步直观的代码加 virtual thread 的高并发能力，单机轻松撑住几千并发。扩展阶段要 SSE 流式返回时，Spring MVC 的 `SseEmitter` 也能支持。

**决策六：Sandbox 先定接口，核心阶段只填一档实现。** 隔离强度和开销是一个跷跷板，从轻到重依次是应用层白名单校验、容器隔离（namespace + cgroups + seccomp）、microVM（Firecracker / Kata / gVisor）、完整虚拟机或物理隔离。为了不让核心阶段的实现选择绑死未来的架构，先抽象出一个 `Sandbox` 接口，表达"在受控环境里执行一个动作"这个意图，不携带任何一档实现特有的概念（不出现"容器镜像""VM 配置"字样）。核心阶段只实现 `WhitelistSandbox` 这一档：文件操作限制工作目录、Shell 命令白名单、HTTP 域名白名单，在应用层做校验，不使用 Java `SecurityManager`（它在 JDK 17 起已废弃、JDK 21 已不可用，与本项目 JDK 21+ 要求冲突）。扩展阶段按信号驱动升级：出现"要跑不可信代码或要多租户"时上容器隔离；出现"要跑完全不可信代码或要规模化多租户"时上 microVM。接口不随升级变化，新增的是实现类。

**决策七：持久化用 SQLite 加 Spring Data JPA，Memory 长期记忆用 `MEMORY.md` 文件加关键词检索。** Agent 目录放 `.oryxos/agents/`，Session、Tool Invocation、LLM Call 落 SQLite。其中审计相关的 `tool_invocations` 和 `llm_calls` 两张表在核心阶段就做写入（不做查询接口），让可审计这个差异化能力的数据地基在 day one 就立起来，避免后期从日志反解析返工。完整的向量检索方案在扩展阶段升级（详见第 8 章）。

### 1.2 整体技术栈

OryxOS 的完整技术栈：

1. **JDK 21** 加 **Spring Boot 3.x**（virtual thread 处理高并发）
2. **Spring AI** 加 **Spring AI Alibaba**（LLM Provider 抽象，复用现成的主流 LLM connector）
3. 自实现 **ReAct loop**（Agent 核心循环）
4. **Spring MVC**（HTTP API 服务层）
5. **Picocli**（命令行工具）
6. **SnakeYAML**（Profile YAML 解析）
7. **SQLite** 加 **Spring Data JPA**（Session、审计和元数据持久化）
8. **MCP Java SDK**（MCP Client 集成，社区项目，可能需要部分自实现）
9. **Logback** 加 **SLF4J**（结构化日志）
10. **Micrometer** 加 **Prometheus**（指标采集，扩展阶段）

---
