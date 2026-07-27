/**
 * oryxos-storage：持久化层（SQLite + Spring Data JPA）。
 *
 * <p>包含：{@code SessionRepository}、{@code ToolInvocationRepository}、
 * {@code LlmCallRepository} 等。数据库文件为 {@code .oryxos/oryxos.db}。
 *
 * <p>关键约束（技术方案第 9 章）：{@code tool_invocations} 和 {@code llm_calls}
 * 两张审计表<b>核心阶段就写入落库</b>（可只写不提供查询接口）；
 * SQLite 上 {@code hibernate.ddl-auto=update} 只能首次建表用，
 * 表结构演进手动维护建表脚本，不依赖自动迁移。
 */
package com.oryxos.storage;
