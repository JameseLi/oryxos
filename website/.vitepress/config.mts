import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'OryxOS',
  titleTemplate: ':title — OryxOS',
  description: 'Java 原生的企业级 Agent OS —— 私有部署、完全可审计的 Agent 统一底座',
  base: '/',
  cleanUrls: true,
  appearance: 'force-light',

  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/favicon.svg' }],
    ['link', { rel: 'preconnect', href: 'https://fonts.googleapis.com' }],
    ['link', { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' }],
    ['link', { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Space+Grotesk:wght@700&family=JetBrains+Mono:wght@400;600&display=swap' }],
    ['meta', { name: 'author', content: 'OryxOS' }],
    ['meta', { name: 'keywords', content: 'OryxOS, Agent OS, Java, Spring Boot, Spring AI Alibaba, ReAct, MCP, 企业级 Agent, agent runtime' }],
    ['meta', { name: 'robots', content: 'index, follow' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:site_name', content: 'OryxOS' }],
    ['meta', { property: 'og:title', content: 'OryxOS — Java 原生的企业级 Agent OS' }],
    ['meta', { property: 'og:description', content: '私有部署、完全可审计的 Agent 统一底座：渠道接入、模型路由、工具调用、记忆系统、沙箱执行，数据完全留在企业自己的基础设施。' }],
  ],

  locales: {
    root: {
      label: '简体中文',
      lang: 'zh-CN',
      themeConfig: {
        nav: [
          { text: '首页', link: '/' },
          { text: '文档', link: '/docs/what' },
          { text: 'GitHub', link: 'https://github.com/oryxos/oryxos' },
        ],
        sidebar: {
          '/docs/': [
            {
              text: '快速入门',
              items: [
                { text: 'OryxOS 是什么', link: '/docs/what' },
                { text: '快速开始', link: '/docs/quick-start' },
                { text: '核心概念', link: '/docs/concepts' },
              ],
            },
            {
              text: '业界调研',
              items: [
                { text: '什么是 Agent OS', link: '/docs/research/agent-os' },
                { text: '业界格局与企业刚需', link: '/docs/research/landscape' },
                { text: 'Java 生态的缺位', link: '/docs/research/java-gap' },
                { text: '定位、边界与未来', link: '/docs/research/positioning' },
              ],
            },
            {
              text: '需求分析',
              items: [
                { text: '五大核心能力', link: '/docs/demand/capabilities' },
                { text: '核心功能与关键流程', link: '/docs/demand/core-features' },
                { text: '扩展与社区共建', link: '/docs/demand/extension' },
                { text: '非功能需求与验收', link: '/docs/demand/non-functional' },
              ],
            },
            {
              text: '技术方案',
              items: [
                { text: '关键技术决策', link: '/docs/tech/decisions' },
                { text: '整体架构', link: '/docs/tech/architecture' },
                { text: 'Provider 抽象', link: '/docs/tech/provider' },
                { text: 'ReAct 循环', link: '/docs/tech/react-loop' },
                { text: 'Memory 三层记忆', link: '/docs/tech/memory' },
                { text: 'Tool 体系', link: '/docs/tech/tool' },
                { text: 'Web Service', link: '/docs/tech/web-service' },
                { text: '支撑模块与持久化', link: '/docs/tech/supporting' },
                { text: '定义一个 Agent', link: '/docs/tech/define-agent' },
                { text: '验收 Demo 与实施节奏', link: '/docs/tech/demos' },
              ],
            },
            {
              text: 'AI 编程指南',
              items: [
                { text: '实施总览', link: '/docs/ai-guide/overview' },
                { text: '准备阶段', link: '/docs/ai-guide/preparation' },
                { text: '五个 User Story', link: '/docs/ai-guide/user-stories' },
                { text: '增量阶段与风险', link: '/docs/ai-guide/incremental' },
              ],
            },
          ],
        },
      },
    },
    en: {
      label: 'English',
      lang: 'en-US',
      link: '/en/',
      themeConfig: {
        nav: [
          { text: 'Home', link: '/en/' },
          { text: 'Docs', link: '/en/docs/what' },
          { text: 'GitHub', link: 'https://github.com/oryxos/oryxos' },
        ],
        sidebar: {
          '/en/docs/': [
            {
              text: 'Getting Started',
              items: [
                { text: 'What is OryxOS', link: '/en/docs/what' },
                { text: 'Quick Start', link: '/en/docs/quick-start' },
                { text: 'Core Concepts', link: '/en/docs/concepts' },
              ],
            },
            {
              text: 'Industry Research',
              items: [
                { text: 'What is an Agent OS', link: '/en/docs/research/agent-os' },
                { text: 'Landscape & Enterprise Needs', link: '/en/docs/research/landscape' },
                { text: 'The Java Ecosystem Gap', link: '/en/docs/research/java-gap' },
                { text: 'Positioning & Future', link: '/en/docs/research/positioning' },
              ],
            },
            {
              text: 'Requirements',
              items: [
                { text: 'Five Core Capabilities', link: '/en/docs/demand/capabilities' },
                { text: 'Core Features & Flows', link: '/en/docs/demand/core-features' },
                { text: 'Extensions & Community', link: '/en/docs/demand/extension' },
                { text: 'Non-Functional & Acceptance', link: '/en/docs/demand/non-functional' },
              ],
            },
            {
              text: 'Technical Solution',
              items: [
                { text: 'Key Decisions', link: '/en/docs/tech/decisions' },
                { text: 'Architecture', link: '/en/docs/tech/architecture' },
                { text: 'Provider Abstraction', link: '/en/docs/tech/provider' },
                { text: 'ReAct Loop', link: '/en/docs/tech/react-loop' },
                { text: 'Three-layer Memory', link: '/en/docs/tech/memory' },
                { text: 'Tool System', link: '/en/docs/tech/tool' },
                { text: 'Web Service', link: '/en/docs/tech/web-service' },
                { text: 'Supporting Modules & Persistence', link: '/en/docs/tech/supporting' },
                { text: 'Defining an Agent', link: '/en/docs/tech/define-agent' },
                { text: 'Acceptance Demos & Timeline', link: '/en/docs/tech/demos' },
              ],
            },
            {
              text: 'AI Programming Guide',
              items: [
                { text: 'Overview', link: '/en/docs/ai-guide/overview' },
                { text: 'Preparation', link: '/en/docs/ai-guide/preparation' },
                { text: 'Five User Stories', link: '/en/docs/ai-guide/user-stories' },
                { text: 'Incremental Phase & Risks', link: '/en/docs/ai-guide/incremental' },
              ],
            },
          ],
        },
      },
    },
  },

  themeConfig: {
    logo: '/images/oryxos-logo.svg',
    siteTitle: 'OryxOS',
    socialLinks: [
      { icon: 'github', link: 'https://github.com/oryxos/oryxos' },
    ],
  },
})
