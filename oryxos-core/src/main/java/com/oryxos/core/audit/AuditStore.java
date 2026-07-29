package com.oryxos.core.audit;

/**
 * 审计存储接口（DIP：core 定义，oryxos-storage 以 JPA 实现）。
 * 宪法原则六：审计数据 day one 落库，不允许只记日志。
 */
public interface AuditStore {

    void saveLlmCall(LlmCallRecord record);

    void saveToolInvocation(ToolInvocationRecord record);
}
