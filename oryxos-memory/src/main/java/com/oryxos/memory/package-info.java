/**
 * oryxos-memory：核心能力三（Memory 三层记忆）。
 *
 * <p>包含：{@code MemoryService} 三层统一门面（对 ReAct 循环只暴露一个接口，内部委托
 * 会话记忆 {@code SessionManager} 与长期记忆 {@code LongTermMemory}）、
 * {@code LongTermMemoryStore} 可插拔后端（Markdown / Sqlite / Mem0 三档，{@code memory.backend} 配置切换）、
 * {@code MemoryTools}（{@code save_memory} / {@code recall_memory} 两个内置 Tool）。
 *
 * <p>关键约束（技术方案第 5 章）：核心记忆区永不截断，截断与关键词检索只作用在归档区；
 * 核心阶段不做自动抽取、不引内向量库依赖。
 */
package com.oryxos.memory;
