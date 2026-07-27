---
title: 扩展与社区共建
description: 核心阶段之后：企业级治理层与社区共建方向
---

# 扩展与社区共建

## 6. 扩展功能

扩展功能在核心功能完成后推进，补齐生产级使用必需但不在最短链路上的能力，以开源社区方式陆续补齐。

### 6.1 渠道和模型层

- **多 Channel 接入**：企业微信、飞书、钉钉、Slack、邮件，通过 Channel Adapter 插件机制扩展
- **Provider Fallback 和可靠性**：三层 failover（hedge racing、circuit breaker、自动切换），Provider 故障时自动切换备用
- **Adaptive Routing**：LLM 路由从静态配置升级为动态决策，根据任务类型、历史调用质量、当前 Provider 负载自动选择

### 6.2 记忆和能力层

- **Memory 自动抽取**：LLM 在对话结束时自动提取值得长期保留的事实写入 MEMORY.md
- **Memory 语义检索**：集成向量数据库（Milvus、Qdrant、Weaviate、PostgreSQL pgvector），按语义相似度匹配
- **情景记忆**：补齐 Memory 第三层，记录任务过程中修改的文件、决策、成果
- **Memory Wiki**：结构化 claim/evidence、矛盾检测、新鲜度管理
- **Skill 体系**：完整支持 SKILL.md 文件，兼容 agentskills.io 开放标准

### 6.3 工具和安全层

- **MCP Server 暴露**：OryxOS 自己作为 MCP server，把内部 Agent 能力暴露给其他系统
- **Tool Policy**：Profile 级别的 Tool 允许或拒绝规则，这是 Agent OS 治理能力里最轻、最能体现 OS 管控的一项，扩展阶段优先做
- **Tool LRU 加载**：工具数量多时，动态加载，避免把所有工具塞进 LLM context
- **完整 Sandbox 隔离**：Docker 容器和 K8s pod 两种 sandbox 实现，WebAssembly Sandbox 作为高性能选项

### 6.4 治理和运维层

> 这一层是 OryxOS 区别于个人级 Agent OS 的核心差异化所在

- **Web 仪表板**：提供 Web 仪表板做 Profile 管理、Session 查看、监控看板、审计日志查询
- **SSO 和多租户**：SAML/OIDC 接入，对接企业 AD/Okta/Entra ID/阿里云 IDaaS。三级租户模型（组织、部门、项目），RBAC 权限粒度到 Agent、Tool、Skill 级别
- **审计与可追溯**：完整审计事件记录、JSON 结构化输出、trace ID 串联、敏感信息脱敏、SIEM 导出
- **可观测性**：Prometheus 指标、结构化日志、健康检查接口、Grafana Dashboard 模板
- **集群化部署与高可用**：多节点协同通过 Nacos 或 ETCD 完成，节点故障自动迁移负载

### 6.5 企业集成层

- **企业 IT 系统 connector**：ERP（用友、金蝶、SAP）、CRM（销售易、纷享销客、Salesforce）、CMDB、监控系统、内网知识库的现成 connector

---

---

## 7. 社区共建功能

社区共建功能不在 OryxOS 主线开发计划内，作为长期方向开放给社区贡献，不规定时间表。

- **剩余项目文档**：API 参考文档、部署运维手册、贡献者指南 CONTRIBUTING.md、典型场景使用手册
- **Skills Marketplace**：社区贡献的 Skill 共享平台，兼容 agentskills.io 开放标准，跟 OpenClaw 和 Hermes Agent 兼容
- **SDK 多语言支持**：优先级 Java → Python → TypeScript → Go
- **可视化 Profile 编辑器**：让非工程师也能配置 Agent，接近 Dify 的 Agent 配置界面
- **Native 文件生成**：不依赖 LibreOffice 直接生成 pptx、docx、xlsx（Apache POI）
- **多区域部署**：跨地域 OryxOS 集群，Agent、Memory、Session 可以跨区域协同
- **Kubernetes Operator**：一键部署、声明式配置、GitOps 工作流
- **移动端管理台**：手机随时查集群状态、处理告警
- **Voice Channel**：语音唤醒和连续语音对话
- **RISC-V 和边缘部署**：跑在 Raspberry Pi、边缘网关（GraalVM Native Image）

---
