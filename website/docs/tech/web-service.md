---
title: Web Service
description: 对外完整门面：核心 10 个 REST 端点与业务系统集成场景
---

# Web Service

## 7. 核心能力五：Web Service

Web Service 是 OryxOS 的对外完整门面，业务系统通过 REST API 接入。前面四大能力是 OryxOS 的内部能力，Web Service 是对外暴露。没有 Web Service，OryxOS 只是一个 CLI 工具，无法跟企业现有业务系统集成。这也是 OryxOS 区别于偏个人定位的 OpenClaw、Hermes 的关键能力。

### 7.1 模块组成

**`WebServer` 模块。** 启动 Spring MVC 服务器，`oryxos serve` 命令触发，默认端口 `8080`，开启 Java 21 virtual thread。

**`ApiController` 集合。** 按资源分六个 Controller：`SessionApiController`（会话管理）、`AgentApiController`（无状态调用）、`ProfileApiController`（Profile 查询）、`MemoryApiController`（Memory 查询）、`ToolApiController`（Tool 信息）、`SystemApiController`（系统状态）。每个 Controller 只做参数校验、响应包装、错误处理，实际逻辑委托给核心层的服务。

**`GlobalExceptionHandler` 模块。** 统一异常处理，把异常转成标准 JSON 响应信封 `ApiResponse`（`code`、`message`、`data`、`timestamp`；成功与错误共用一个信封）。此信封第24节随白名单管理端点已建于 `oryxos-web`，26 节复用、不另建 ErrorBody。

**OpenAPI 文档模块。** 通过 `springdoc-openapi` 自动生成 OpenAPI 3.0 文档，暴露在 `/swagger-ui`。

### 7.2 核心阶段 10 个端点

**会话管理（4 个）：**

1. `POST /api/v1/sessions`（创建）
2. `POST /api/v1/sessions/{id}/messages`（发消息）
3. `GET /api/v1/sessions/{id}`（查历史）
4. `DELETE /api/v1/sessions/{id}`（归档）

**Agent 调用（1 个）：**

1. `POST /api/v1/agents/{name}/invoke`（无状态调用）

**Profile / Memory / Tool 信息（3 个）：**

1. `GET /api/v1/profiles`
2. `GET /api/v1/memory`
3. `GET /api/v1/tools`

**系统状态（2 个）：**

1. `GET /api/v1/health`
2. `GET /api/v1/info`

### 7.3 扩展阶段补齐的端点

**`Agent` 目录的上传/查看/更新/删除**（业务方通过 API 而不是手动把目录丢进 `.oryxos/agents/` 来创建新 Agent，含一句话生成 `AGENT.md`，这才是"纯 API 定义一个新 Agent"的完整闭环）；Memory 的 append/clear/search；Tool describe 和调用历史；LLM call 历史和 token 统计；**`AgentScheduler` 的调度管理**（增删查改某个 Agent frontmatter 的 `schedules`，不用改文件重启进程）；Webhook 触发；SSE 流式响应；Prometheus metrics；OpenAPI spec。

> **核心阶段为什么不做：** 核心阶段"定义一个 Agent"的路径是业务方手写一个 `.oryxos/agents/<name>/` 目录（`AGENT.md`[+ 脚本 / 子指令]）、重启或热加载生效（`ContextLoader` 每次组装 prompt 都重新读取，不需要重启也能生效，见 8.3），Web Service 核心阶段的端点都只做查询，不做创建，这跟"业务系统通过 API 动态创建新 Agent"是两件事——后者需要 Agent 目录上传接口（含一句话生成）、`AgentScheduler` 的运行时增删接口一起补齐，缺了这条链路就不完整，所以放在同一批扩展阶段一起做，不拆开先做一半。

### 7.4 关键设计点

- **错误码规范：** 标准 HTTP 状态码加内部错误码（400 参数错误、404 资源不存在、500 内部错误、503 Provider 故障）。
- **CORS：** 核心阶段开放所有源方便调试，扩展阶段加白名单。
- **请求大小限制：** 单条消息最大 32KB，Session 历史返回最多最近 100 条。
- **超时：** Agent 调用最长 60 秒超时返回 504。

### 7.5 核心阶段不做的部分

- 认证机制（无认证假设内网，扩展补 API Key 加 JWT）
- 流式响应 SSE
- WebSocket
- RBAC 权限
- 限流

这些放扩展阶段。

### 7.6 业务系统集成场景

- **同步调用**（最常用，业务系统调 invoke 等返回，适合 stateless 短任务）
- **会话保持**（先创建 Session 再多次发消息，适合连续对话）
- **Webhook 触发**（告警系统、CI/CD、定时任务调 Agent，打通监控的感知到分析到行动闭环）
- **跨语言集成**（任何能发 HTTP 请求的语言都能接，核心阶段不出 SDK，扩展阶段才出）

---
