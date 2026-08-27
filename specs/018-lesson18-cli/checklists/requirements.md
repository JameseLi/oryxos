# Specification Quality Checklist: 第 18 节 CLI（命令行入口 + 会话层）

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-26
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — 注：延续 16/17 节先例，FR 锚定课件字面量（Picocli/`@Command`/`messages_json` 等均来自课件与技术方案原文，属"课件为唯一事实源"的验收锚点而非实现发挥）
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders（User Story 部分技术中立；FR 部分锚定课件原文）
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain（全部经 Clarifications Q1~Q9 与 Resolve R1~R9 裁定，停点对账）
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic（第 1~4 条以 harness/门禁/存在性/回归表述）
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded（明确不做：CLI 无 Agent 逻辑/IM Channel/serve 与 gateway 深层/按条拆表/命令分流自动化）
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows（会话层/命令行骨架/chat 三条主线）
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification（R 项为停点对账的工程裁定记录，属 spec 先例格式）

## Notes

- 课件「四、验收 harness」明言：命令分流与 `--help` 属进程级行为、自动化成本大于收益，留人工清单——harness 只承载会话层（NFR-002 如实固化该边界）。
- R1~R9 为工程裁定（接口升级/实体命名/Jackson 显式化/装配接线/启动类落位/桩边界/init 模板/轻命令口径/fat JAR 主入口），全部待 Step 5 固定停点与用户对账后才 implement。
