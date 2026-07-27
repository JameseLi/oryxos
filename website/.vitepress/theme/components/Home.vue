<script setup>
import { computed } from 'vue'
import { useData } from 'vitepress'

const { lang } = useData()
const isZh = computed(() => lang.value === 'zh-CN')
const t = (zh, en) => isZh.value ? zh : en

const capabilities = computed(() => [
  {
    icon: '🧠',
    title: t('自实现 ReAct 循环', 'Self-built ReAct Loop'),
    subtitle: t('LLM 思考 → 调工具 → 看结果 → 再决策', 'Reason → Act → Observe → repeat'),
    code: t(`$ oryxos chat --profile weather

> 查一下北京天气并告诉我穿什么

[ReAct] iter 1: LLM 决定调用 http_get
[Tool]  http_get weather-api → 200 OK
[ReAct] iter 2: 无工具调用，输出回复

北京今天晴转多云，18~26°C。
建议薄外套 + 长袖，早晚偏凉。`, `$ oryxos chat --profile weather

> Check Beijing weather and tell me what to wear

[ReAct] iter 1: LLM decides to call http_get
[Tool]  http_get weather-api → 200 OK
[ReAct] iter 2: no tool call, final reply

Sunny turning cloudy, 18~26°C.
Light jacket + long sleeves advised.`),
  },
  {
    icon: '📁',
    title: t('一个目录 = 一个 Agent', 'One Directory = One Agent'),
    subtitle: t('AGENT.md + 可选 skills/ scripts/ · 零代码', 'AGENT.md + optional skills/ scripts/ · zero code'),
    code: t(`# .oryxos/agents/daily-weather/AGENT.md
---
name: daily-weather
provider: { name: deepseek, model: deepseek-chat }
tools: [http_get, notify]
schedules:
  - cron: "0 0 8 * * *"
    message: "查北京天气并给穿搭建议"
notify_channels:
  - type: webhook
    url: "\${WECOM_WEBHOOK_URL}"
---
每天早上查询北京天气，生成穿搭建议，
推送到运维群。`, `# .oryxos/agents/daily-weather/AGENT.md
---
name: daily-weather
provider: { name: deepseek, model: deepseek-chat }
tools: [http_get, notify]
schedules:
  - cron: "0 0 8 * * *"
    message: "Check Beijing weather, give outfit advice"
notify_channels:
  - type: webhook
    url: "\${WECOM_WEBHOOK_URL}"
---
Every morning: fetch Beijing weather,
compose outfit advice, push to ops group.`),
  },
  {
    icon: '🌐',
    title: 'Web Service',
    subtitle: t('完整 REST API · 业务系统的唯一接入通道', 'Full REST API · the integration surface'),
    code: t(`# 创建会话并发消息
curl -X POST localhost:8080/api/v1/sessions \\
  -d '{"profile":"daily-weather","user":"u-1"}'
curl -X POST \\
  localhost:8080/api/v1/sessions/{id}/messages \\
  -d '{"content":"今天天气如何"}'

# 无状态调用 / 查 Agent / 查 Tool
curl -X POST \\
  localhost:8080/api/v1/agents/daily-weather/invoke
curl localhost:8080/api/v1/profiles
curl localhost:8080/api/v1/tools`, `# Create a session and post a message
curl -X POST localhost:8080/api/v1/sessions \\
  -d '{"profile":"daily-weather","user":"u-1"}'
curl -X POST \\
  localhost:8080/api/v1/sessions/{id}/messages \\
  -d '{"content":"How is the weather today"}'

# Stateless invoke / list agents / list tools
curl -X POST \\
  localhost:8080/api/v1/agents/daily-weather/invoke
curl localhost:8080/api/v1/profiles
curl localhost:8080/api/v1/tools`),
  },
])

const scenarios = computed(() => [
  {
    num: '01',
    title: t('每日天气推送', 'Daily weather push'),
    desc: t('cron 到点自动查天气、生成穿搭建议、推送到企业 IM 群。全程无人工触发，审计表里躺着每次 LLM 和 Tool 调用记录。', 'Cron triggers the agent to fetch weather, compose outfit advice, and push to the IM group — zero human involvement, every LLM and tool call audited.'),
  },
  {
    num: '02',
    title: t('每日科技日报', 'Daily tech digest'),
    desc: t('AGENT.md + skills/ 子指令渐进式披露，日报内容体现 MEMORY.md 里记住的关注方向（比如"更关注 AI 和芯片"）。', 'AGENT.md + skills/ sub-instructions with progressive disclosure; the digest reflects preferences remembered in MEMORY.md.'),
  },
  {
    num: '03',
    title: t('每日 GitHub 日报', 'Daily GitHub digest'),
    desc: t('AGENT.md + scripts/ 捆绑脚本，Agent 用 shell 跑 python 拿确定性数据，脚本产出进上下文、代码不进。', 'AGENT.md + bundled scripts/: the agent runs a python script via shell for deterministic data — output enters context, code does not.'),
  },
  {
    num: '04',
    title: t('运维助手', 'Ops assistant'),
    desc: t('凌晨告警经 webhook 进来，Agent 查日志、交叉历史故障库发现已知 bug，自动重启服务，在运维群汇报"已自愈"。', 'Alerts arrive via webhook at 3am; the agent pulls logs, matches the known-issue memory, restarts the service, and reports "self-healed".'),
  },
  {
    num: '05',
    title: t('知识管理助手', 'Knowledge assistant'),
    desc: t('员工问"上次 SaaS 协议怎么处理数据出境条款"，Agent 检索 Memory 拉历史案例，综合法规给出建议草稿并标注引用来源。', '"How did we handle data-residency clauses last time?" — the agent recalls precedents from memory and drafts advice with citations.'),
  },
  {
    num: '06',
    title: t('销售助手', 'Sales assistant'),
    desc: t('拜访客户前问一句，Agent 调 CRM connector 拉历史交易、调企查查 MCP 查工商信息，一键生成客户简报。', 'Before a customer visit: the agent pulls CRM history via a connector and registry data via MCP — instant customer briefing.'),
  },
  {
    num: '07',
    title: t('跨对话记住偏好', 'Memory across conversations'),
    desc: t('告诉 Agent "我项目用 Spring Boot 部署在 K8s"，它主动 save_memory；重启后新对话自然引用这些偏好。', 'Tell the agent "we use Spring Boot on K8s" — it calls save_memory; after a restart, new conversations still remember.'),
  },
  {
    num: '08',
    title: t('多 Agent 并存', 'Multiple agents coexisting'),
    desc: t('同一个 OryxOS 实例上跑多个 Agent，各自独立的模型、工具集、渠道和定时规则，共享一套底座——"OS"的最小体现。', 'Multiple agents on one OryxOS instance — each with its own model, toolset, channel, and schedule, sharing one runtime base.'),
  },
])
</script>

<template>
  <div class="oryx-page">

    <!-- ── HERO ── -->
    <section class="oryx-hero">
      <div class="oryx-hero-inner">
        <div class="oryx-badge">
          <span class="oryx-badge-dot"></span>
          {{ t('Java 原生 · 企业级 Agent OS', 'Java-native · Enterprise Agent OS') }}
        </div>

        <h1 class="oryx-title">
          <span class="oryx-title-name">OryxOS</span>
        </h1>

        <p class="oryx-title-sub">{{ t('企业能完全掌控的 Agent 统一底座', 'The agent foundation enterprises fully control') }}</p>

        <p class="oryx-hero-desc">
          {{ t('装在企业自己的 K8s 或服务器上，作为统一底座跑各种业务 Agent，共享一套渠道接入、模型路由、工具调用、记忆系统、沙箱执行能力。数据完全留在企业自己的基础设施，不锁任何云生态。', 'Deploy on your own K8s or servers and run business agents — ops, support, HR, knowledge — on one shared foundation: channels, model routing, tools, memory, and sandboxing. Your data never leaves your infrastructure. No cloud lock-in.') }}
        </p>

        <div class="oryx-hero-actions">
          <a class="oryx-btn-primary" :href="t('/docs/quick-start', '/en/docs/quick-start')">
            {{ t('快速开始', 'Get Started') }} →
          </a>
          <a class="oryx-btn-ghost" :href="t('/docs/tech/decisions', '/en/docs/tech/decisions')">
            {{ t('技术方案', 'Technical Design') }}
          </a>
          <a class="oryx-btn-ghost" href="https://github.com/oryxos/oryxos" target="_blank" rel="noopener">
            GitHub
          </a>
        </div>

        <div class="oryx-hero-note">
          {{ t('JDK 21 · Spring Boot 3.x · Spring AI Alibaba · 自实现 ReAct · SQLite · Apache-2.0', 'JDK 21 · Spring Boot 3.x · Spring AI Alibaba · self-built ReAct · SQLite · Apache-2.0') }}
        </div>
      </div>
    </section>

    <!-- ── PROBLEM ── -->
    <section class="oryx-section">
      <div class="oryx-section-inner">
        <div class="oryx-problem">
          <div class="oryx-problem-text">
            <h2 class="oryx-section-title">{{ t('企业落地 Agent 的两个坎', 'Two Walls Every Enterprise Hits') }}</h2>
            <p>{{ t('做出一个 Agent 的 demo 不难，难的是让它在企业里可控地跑起来。', 'Demoing an agent is easy. Running it under enterprise control is not.') }}</p>
            <p class="oryx-problem-item">
              <strong>{{ t('① 单个 Agent 容易做，企业级可控难', '① Easy to build one agent, hard to govern it') }}</strong>
              {{ t('集成、数据访问、安全审计、变更管理——这些全是底座问题，不是模型问题。大量 agent pilot 永远到不了生产。', 'Integration, data access, security audit, change management — foundation problems, not model problems. Many agent pilots never reach production.') }}
            </p>
            <p class="oryx-problem-item">
              <strong>{{ t('② 现有开源 Agent OS 都不是 Java', '② No open-source Agent OS is Java-native') }}</strong>
              {{ t('OpenClaw 是 Node.js、Hermes 是 Python。它们跟企业的 Java 技术栈、运维工具链、审计流程之间有一道断裂。', 'OpenClaw is Node.js, Hermes is Python. They sit at odds with the Java stack, ops tooling, and audit processes enterprises already run.') }}
            </p>
            <p class="oryx-solution-line">{{ t('OryxOS 把这两件事一起解决：Java 原生的 Agent 统一底座，私有部署，安全审计 day one 设计。', 'OryxOS solves both: a Java-native agent foundation — privately deployable, with security and audit designed from day one.') }}</p>
          </div>
          <div class="oryx-problem-compare">
            <div class="oryx-compare-item oryx-compare-bad">
              <div class="oryx-compare-label">{{ t('今天的做法', 'Today') }}</div>
              <div class="oryx-compare-rows">
                <div class="oryx-compare-row">
                  <span class="oryx-compare-icon">✗</span>
                  <span>{{ t('Node.js / Python Agent OS，跨技术栈写大量胶水', 'Node.js / Python agent OS, lots of cross-stack glue code') }}</span>
                </div>
                <div class="oryx-compare-row">
                  <span class="oryx-compare-icon">✗</span>
                  <span>{{ t('个人定位项目：CVE、恶意 skill，安全靠事后补丁', 'Consumer-grade projects: CVEs, malicious skills, security patched after the fact') }}</span>
                </div>
                <div class="oryx-compare-row">
                  <span class="oryx-compare-icon">✗</span>
                  <span>{{ t('SaaS / 云厂商方案：数据出企业、锁生态', 'SaaS / cloud-vendor offerings: data leaves the enterprise, ecosystem lock-in') }}</span>
                </div>
                <div class="oryx-compare-row">
                  <span class="oryx-compare-icon">✗</span>
                  <span>{{ t('每个团队重复造渠道 / 模型 / 记忆 / 工具底座', 'Every team rebuilds channels, models, memory, and tooling') }}</span>
                </div>
              </div>
            </div>
            <div class="oryx-compare-item oryx-compare-good">
              <div class="oryx-compare-label">OryxOS</div>
              <div class="oryx-compare-rows">
                <div class="oryx-compare-row">
                  <span class="oryx-compare-icon oryx-icon-ok">✓</span>
                  <span>{{ t('Java 原生 Spring Boot，纳入现有运维审计体系', 'Java-native Spring Boot — fits existing ops and audit systems') }}</span>
                </div>
                <div class="oryx-compare-row">
                  <span class="oryx-compare-icon oryx-icon-ok">✓</span>
                  <span>{{ t('安全 day one：白名单沙箱、凭证不落盘、审计落库', 'Security from day one: whitelist sandbox, env-injected credentials, audit tables') }}</span>
                </div>
                <div class="oryx-compare-row">
                  <span class="oryx-compare-icon oryx-icon-ok">✓</span>
                  <span>{{ t('私有部署，数据完全留在企业基础设施', 'Private deployment — data stays entirely on your infrastructure') }}</span>
                </div>
                <div class="oryx-compare-row">
                  <span class="oryx-compare-icon oryx-icon-ok">✓</span>
                  <span>{{ t('五大能力统一底座，上新 Agent 写一个目录', 'Five capabilities in one base — a new agent is just one directory') }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ── FLOW DIAGRAM ── -->
    <section class="oryx-section oryx-flow-section">
      <div class="oryx-section-inner">
        <img src="/flow.svg" alt="OryxOS architecture flow" class="oryx-flow-img" />
      </div>
    </section>

    <!-- ── CAPABILITIES ── -->
    <section class="oryx-section oryx-primitives-section">
      <div class="oryx-section-inner oryx-primitives-inner">
        <div class="oryx-section-header">
          <div class="oryx-section-tag">{{ t('核心能力', 'Core Capabilities') }}</div>
          <h2 class="oryx-section-title">{{ t('运行时内核，五大能力一体', 'A runtime kernel with five capabilities') }}</h2>
        </div>
        <div class="oryx-primitives">
          <div v-for="p in capabilities" :key="p.title" class="oryx-primitive">
            <div class="oryx-primitive-header">
              <span class="oryx-primitive-icon">{{ p.icon }}</span>
              <div>
                <h3 class="oryx-primitive-title">{{ p.title }}</h3>
                <p class="oryx-primitive-subtitle">{{ p.subtitle }}</p>
              </div>
            </div>
            <pre class="oryx-code"><code>{{ p.code }}</code></pre>
          </div>
        </div>
      </div>
    </section>

    <!-- ── SCENARIOS ── -->
    <section class="oryx-section">
      <div class="oryx-section-inner">
        <div class="oryx-section-header">
          <div class="oryx-section-tag">{{ t('真实场景', 'Real Scenarios') }}</div>
          <h2 class="oryx-section-title">{{ t('八个真实使用场景', 'Eight real-world use cases') }}</h2>
        </div>
        <div class="oryx-scenarios">
          <div v-for="s in scenarios" :key="s.num" class="oryx-scenario">
            <div class="oryx-scenario-num">{{ s.num }}</div>
            <div>
              <h3 class="oryx-scenario-title">{{ s.title }}</h3>
              <p class="oryx-scenario-desc">{{ s.desc }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ── PLUGIN TOOLS ── -->
    <section class="oryx-section oryx-sdk-section">
      <div class="oryx-section-inner">
        <div class="oryx-section-header">
          <div class="oryx-section-tag">{{ t('扩展方式', 'Extensibility') }}</div>
          <h2 class="oryx-section-title">{{ t('Plugin Tool 三档接入，按需选择', 'Three tiers of Plugin Tools — pick what fits') }}</h2>
        </div>
        <div class="oryx-sdk-cards">
          <div class="oryx-sdk-card oryx-sdk-card-featured">
            <div class="oryx-sdk-card-icon">📁</div>
            <h3 class="oryx-sdk-card-title">{{ t('零代码（主推）', 'Zero-code (recommended)') }}</h3>
            <p class="oryx-sdk-card-desc">{{ t('写一个 Agent 目录（AGENT.md + 可选子指令/脚本），复用社区现成 MCP server。业务方全程不写一行代码，LLM 自己组合调用。', 'Write an agent directory (AGENT.md + optional sub-instructions/scripts) and reuse community MCP servers. No code at all — the LLM composes the calls.') }}</p>
            <div class="oryx-sdk-installs">
              <code>.oryxos/agents/daily-pr-digest/AGENT.md</code>
              <code>mcp_servers.yaml → github-mcp</code>
            </div>
          </div>
          <div class="oryx-sdk-card">
            <div class="oryx-sdk-card-icon">🔌</div>
            <h3 class="oryx-sdk-card-title">{{ t('轻代码', 'Light-code') }}</h3>
            <p class="oryx-sdk-card-desc">{{ t('业务方用任何语言自写 MCP server，接入企业自有系统（ERP、CRM、CMDB）。OryxOS 作为 MCP Client 连接并把工具注册进 ToolRegistry。', 'Write your own MCP server in any language to connect in-house systems (ERP, CRM, CMDB). OryxOS connects as an MCP client and registers the tools.') }}</p>
            <div class="oryx-sdk-installs">
              <code>mcp_servers.yaml → your-erp-mcp</code>
            </div>
          </div>
          <div class="oryx-sdk-card">
            <div class="oryx-sdk-card-icon">☕</div>
            <h3 class="oryx-sdk-card-title">{{ t('重代码', 'Full-code') }}</h3>
            <p class="oryx-sdk-card-desc">{{ t('用 Spring AI @Tool 注解写 Java Spring Bean，启动时自动扫描注册。进程内直接调用，不走 MCP 协议，集成深度和性能最好。', 'Annotate Java Spring Beans with @Tool — auto-scanned at startup. In-process calls without MCP: deepest integration, best performance.') }}</p>
            <div class="oryx-sdk-installs">
              <code>@Tool public ToolResult queryErp(...)</code>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ── RUNTIME SURFACE ── -->
    <section class="oryx-section">
      <div class="oryx-section-inner">
        <div class="oryx-section-header">
          <div class="oryx-section-tag">{{ t('能力总览', 'Runtime Surface') }}</div>
          <h2 class="oryx-section-title">{{ t('内置 Tool · REST API · CLI', 'Built-in Tools · REST API · CLI') }}</h2>
          <p class="oryx-section-desc">{{ t('9 个内置 Tool 全部经 OryxTool 抽象注册；REST API 是业务系统集成的唯一通道；CLI 覆盖全部日常操作。', 'Nine built-in tools behind the OryxTool abstraction; the REST API is the single integration surface; the CLI covers daily operations.') }}</p>
        </div>
        <div class="oryx-proto-grid">
          <div class="oryx-proto-group">
            <div class="oryx-proto-group-label">{{ t('内置 Tool（9 个）', 'Built-in Tools (9)') }}</div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">read_file · write_file · list_dir</code>
              <span class="oryx-proto-desc">{{ t('文件读写，受路径白名单约束', 'File operations under path whitelist') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">shell</code>
              <span class="oryx-proto-desc">{{ t('执行 bash 命令，命令白名单 + 超时', 'Run bash commands — command whitelist + timeout') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">http_get · http_post</code>
              <span class="oryx-proto-desc">{{ t('发起 HTTP 请求，域名白名单', 'HTTP requests under domain whitelist') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">save_memory · recall_memory</code>
              <span class="oryx-proto-desc">{{ t('长期记忆写入与关键词检索（MEMORY.md）', 'Write and keyword-search long-term memory (MEMORY.md)') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">notify</code>
              <span class="oryx-proto-desc">{{ t('把结果推送到 Profile 配置的通知渠道（webhook）', 'Push results to configured notify channels (webhook)') }}</span>
            </div>
          </div>
          <div class="oryx-proto-group">
            <div class="oryx-proto-group-label">REST API</div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">POST /api/v1/sessions</code>
              <span class="oryx-proto-desc">{{ t('创建会话 / 发消息 / 查历史 / 归档', 'Create session / post message / history / archive') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">POST /api/v1/agents/{name}/invoke</code>
              <span class="oryx-proto-desc">{{ t('无状态调用一次 Agent', 'Stateless one-shot agent invocation') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">GET /api/v1/profiles · /tools · /memory</code>
              <span class="oryx-proto-desc">{{ t('列 Agent、查 Tool 元信息、查长期记忆', 'List agents, tool metadata, long-term memory') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">GET /api/v1/health · /info</code>
              <span class="oryx-proto-desc">{{ t('健康检查与运行信息', 'Health check and runtime info') }}</span>
            </div>
          </div>
          <div class="oryx-proto-group">
            <div class="oryx-proto-group-label">CLI</div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">oryxos init</code>
              <span class="oryx-proto-desc">{{ t('初始化 .oryxos/ 工作区（agents/ memory/ Bootstrap…）', 'Initialize the .oryxos/ workspace') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">oryxos chat · serve · gateway</code>
              <span class="oryx-proto-desc">{{ t('交互对话 / REST API 服务 / 多渠道守护进程', 'Interactive chat / REST API server / multi-channel daemon') }}</span>
            </div>
            <div class="oryx-proto-row">
              <code class="oryx-proto-subject">oryxos profile · provider · tool · session</code>
              <span class="oryx-proto-desc">{{ t('Profile 管理、Provider / Tool / Session 查询，共 12 个命令', 'Profile management and queries — 12 commands total') }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ── CTA ── -->
    <section class="oryx-section oryx-cta-section">
      <div class="oryx-section-inner">
        <div class="oryx-cta">
          <h2 class="oryx-cta-title">{{ t('开始构建', 'Start Building') }}</h2>
          <p class="oryx-cta-desc">{{ t('JDK 21+ 与 Maven，三分钟跑起第一个 Agent。', 'With JDK 21+ and Maven, your first agent runs in three minutes.') }}</p>
          <pre class="oryx-code oryx-cta-code"><code>{{ t(`git clone https://github.com/oryxos/oryxos.git && cd oryxos
mvn clean package              # 产出 oryxos-boot/target/oryxos.jar

java -jar oryxos.jar init      # 初始化 .oryxos/ 工作区
export DEEPSEEK_API_KEY=sk-xxx # LLM Key 走环境变量，不落明文

java -jar oryxos.jar chat      # 交互式对话
java -jar oryxos.jar serve     # 启动 REST API（:8080）`, `git clone https://github.com/oryxos/oryxos.git && cd oryxos
mvn clean package              # produces oryxos-boot/target/oryxos.jar

java -jar oryxos.jar init      # initialize the .oryxos/ workspace
export DEEPSEEK_API_KEY=sk-xxx # LLM key via env var, never plaintext

java -jar oryxos.jar chat      # interactive chat
java -jar oryxos.jar serve     # start the REST API (:8080)`) }}</code></pre>
          <div class="oryx-cta-links">
            <a class="oryx-btn-primary" :href="t('/docs/quick-start', '/en/docs/quick-start')">{{ t('查看文档', 'Read the Docs') }}</a>
            <a class="oryx-btn-ghost" href="https://github.com/oryxos/oryxos" target="_blank" rel="noopener">GitHub</a>
          </div>
        </div>
      </div>
    </section>

  </div>
</template>

<style scoped>
.oryx-page {
  min-height: 100vh;
  background: #ffffff;
  color: #000000;
  font-family: inherit;
}

/* ── Hero ── */
.oryx-hero {
  position: relative;
  padding: 100px 24px 80px;
  text-align: center;
  overflow: hidden;
}
.oryx-hero-inner {
  position: relative;
  max-width: 760px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.oryx-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  border-radius: 20px;
  border: 1px solid #d4d4d4;
  background: #f5f5f5;
  color: #555555;
  font-size: 12px;
  margin-bottom: 28px;
}
.oryx-badge-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: #000000;
  animation: pulse 2s infinite;
}
@keyframes pulse {
  0%,100% { opacity:1; transform:scale(1); }
  50% { opacity:0.4; transform:scale(1.4); }
}
.oryx-title {
  margin: 0 0 12px;
  line-height: 1;
}
.oryx-title-name {
  font-family: 'Space Grotesk', 'Inter', sans-serif;
  font-size: clamp(72px, 14vw, 120px);
  font-weight: 700;
  letter-spacing: -0.03em;
  color: #000000;
}
.oryx-title-sub {
  font-size: 18px;
  color: #666666;
  margin: 0 0 20px;
}
.oryx-hero-desc {
  font-size: 16px;
  line-height: 1.7;
  color: #444444;
  max-width: 600px;
  margin: 0 0 32px;
}
.oryx-hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
  margin-bottom: 20px;
}
.oryx-btn-primary {
  padding: 11px 28px;
  border-radius: 8px;
  background: #000000;
  color: #ffffff;
  font-weight: 600;
  font-size: 14px;
  text-decoration: none;
  transition: opacity 0.2s, transform 0.15s;
}
.oryx-btn-primary:hover { opacity: 0.75; transform: translateY(-1px); }
.oryx-btn-ghost {
  padding: 11px 28px;
  border-radius: 8px;
  border: 1px solid #d4d4d4;
  color: #333333;
  font-weight: 600;
  font-size: 14px;
  text-decoration: none;
  transition: border-color 0.2s, background 0.2s;
}
.oryx-btn-ghost:hover { border-color: #000000; background: #f5f5f5; }
.oryx-hero-note {
  font-size: 12px;
  color: #999999;
}

/* ── Section ── */
.oryx-section { padding: 72px 24px; }
.oryx-section-inner { max-width: 1000px; margin: 0 auto; }
.oryx-primitives-inner { max-width: 1400px; }
.oryx-section-header { text-align: center; margin-bottom: 48px; }
.oryx-section-tag {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #555555;
  padding: 4px 12px;
  border-radius: 20px;
  border: 1px solid #d4d4d4;
  background: #f5f5f5;
  margin-bottom: 14px;
}
.oryx-section-title {
  font-size: clamp(22px, 4vw, 32px);
  font-weight: 700;
  color: #000000;
  margin: 0 0 12px;
}
.oryx-section-desc {
  font-size: 15px;
  color: #666666;
  max-width: 600px;
  margin: 0 auto;
  line-height: 1.6;
}

/* ── Problem ── */
.oryx-problem {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 48px;
  align-items: start;
}
.oryx-problem-text p { color: #666666; line-height: 1.7; margin: 0 0 14px; font-size: 15px; }
.oryx-problem-item strong { color: #000000; display: block; margin-bottom: 4px; }
.oryx-solution-line { color: #000000 !important; font-weight: 600; }
.oryx-problem-compare { display: flex; flex-direction: column; gap: 16px; }
.oryx-compare-item {
  padding: 20px;
  border-radius: 12px;
  border: 1px solid #e5e5e5;
}
.oryx-compare-bad { background: #fafafa; }
.oryx-compare-good { background: #f5f5f5; border-color: #d4d4d4; }
.oryx-compare-label { font-size: 11px; font-weight: 700; color: #999999; margin-bottom: 12px; text-transform: uppercase; letter-spacing: 0.08em; }
.oryx-compare-rows { display: flex; flex-direction: column; gap: 8px; }
.oryx-compare-row { display: flex; align-items: flex-start; gap: 10px; font-size: 13px; color: #555555; line-height: 1.5; }
.oryx-compare-icon { flex-shrink: 0; font-style: normal; color: #bbbbbb; font-weight: 700; width: 14px; }
.oryx-icon-ok { color: #000000; }

/* ── Primitives ── */
.oryx-primitives-section { background: #f5f5f5; }
.oryx-primitives { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); grid-auto-rows: 1fr; gap: 16px; }
.oryx-primitive {
  padding: 20px;
  border-radius: 14px;
  border: 1px solid #e5e5e5;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: border-color 0.2s, box-shadow 0.2s;
  min-width: 0;
  overflow: hidden;
}
.oryx-primitive .oryx-code { flex: 1; }
.oryx-primitive:hover { border-color: #000000; box-shadow: 0 4px 16px rgba(0,0,0,0.06); }
.oryx-primitive-header { display: flex; align-items: flex-start; gap: 12px; }
.oryx-primitive-icon { font-size: 28px; flex-shrink: 0; }
.oryx-primitive-title { font-size: 17px; font-weight: 700; color: #000000; margin: 0 0 2px; }
.oryx-primitive-subtitle { font-size: 12px; color: #999999; margin: 0; }
.oryx-code {
  background: #f5f5f5;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  padding: 14px 16px;
  font-size: 12px;
  line-height: 1.6;
  color: #333333;
  overflow-x: auto;
  margin: 0;
  white-space: pre;
}
.oryx-code code { font-family: 'JetBrains Mono', 'Fira Code', monospace; background: none; color: inherit; }

/* ── Scenarios ── */
.oryx-scenarios { display: grid; grid-template-columns: repeat(2, 1fr); gap: 20px; }
.oryx-scenario {
  display: flex;
  gap: 16px;
  padding: 20px;
  border-radius: 12px;
  border: 1px solid #e5e5e5;
  background: #fafafa;
}
.oryx-scenario-num {
  font-size: 28px;
  font-weight: 900;
  color: #e5e5e5;
  line-height: 1;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.oryx-scenario-title { font-size: 15px; font-weight: 600; color: #000000; margin: 0 0 6px; }
.oryx-scenario-desc { font-size: 13px; color: #666666; line-height: 1.6; margin: 0; }

/* ── SDK cards ── */
.oryx-sdk-section { background: #f5f5f5; }
.oryx-sdk-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}
.oryx-sdk-card {
  background: #ffffff;
  border: 1px solid #e5e5e5;
  border-radius: 16px;
  padding: 28px 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.oryx-sdk-card-featured { border-color: #000000; }
.oryx-sdk-card-icon { font-size: 28px; }
.oryx-sdk-card-title { font-size: 17px; font-weight: 700; color: #000000; margin: 0; }
.oryx-sdk-card-desc { font-size: 14px; color: #666666; line-height: 1.6; margin: 0; flex: 1; }
.oryx-sdk-installs { display: flex; flex-direction: column; gap: 6px; }
.oryx-sdk-installs code {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  background: #f5f5f5;
  border: 1px solid #e5e5e5;
  border-radius: 6px;
  padding: 5px 10px;
  color: #000000;
  display: block;
}

/* ── Protocol grid ── */
.oryx-proto-grid { display: flex; flex-direction: column; gap: 28px; }
.oryx-proto-group { display: flex; flex-direction: column; gap: 6px; }
.oryx-proto-group-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #555555;
  margin-bottom: 4px;
}
.oryx-proto-row {
  display: flex;
  align-items: baseline;
  gap: 16px;
  padding: 8px 14px;
  border-radius: 8px;
  background: #fafafa;
  border: 1px solid #e5e5e5;
  flex-wrap: wrap;
}
.oryx-proto-subject {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: #000000;
  background: #f0f0f0;
  border: 1px solid #d4d4d4;
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
  white-space: nowrap;
}
.oryx-proto-desc { font-size: 13px; color: #666666; flex: 1; }

/* ── CTA ── */
.oryx-cta-section { background: #f5f5f5; }
.oryx-cta { text-align: center; max-width: 680px; margin: 0 auto; }
.oryx-cta-title { font-size: 28px; font-weight: 700; color: #000000; margin: 0 0 12px; }
.oryx-cta-desc { font-size: 15px; color: #666666; margin: 0 0 24px; }
.oryx-cta-code { text-align: left; margin-bottom: 28px; }
.oryx-cta-links { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; }

/* ── Flow diagram ── */
.oryx-flow-section { padding: 0 24px 72px; }
.oryx-flow-img {
  width: 100%;
  display: block;
  border: 1px solid #e5e5e5;
  border-radius: 12px;
}

/* ── Responsive ── */
@media (max-width: 900px) {
  .oryx-sdk-cards { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .oryx-hero { padding: 72px 20px 60px; }
  .oryx-problem { grid-template-columns: 1fr; }
  .oryx-primitives { grid-template-columns: 1fr; }
  .oryx-scenarios { grid-template-columns: 1fr; }
  .oryx-section { padding: 48px 20px; }
}
</style>
