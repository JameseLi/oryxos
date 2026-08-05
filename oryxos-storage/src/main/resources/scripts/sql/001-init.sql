-- 001-init.sql — OryxOS SQLite 手工建表脚本（宪法工程约束：不依赖 hibernate.ddl-auto=update）
-- 第 16 节：仅 llm_calls 审计表（成功/失败都落库——宪法原则 VI）。
-- 后续节按需在本脚本追加 sessions / tool_invocations / scheduled_tasks / task_executions 等表。

-- llm_calls：每次大模型调用记录（FR-006/FR-007）。
--   success / error_message 两列必须存在：调用失败时这次事故在系统里也要有痕迹，
--   与工具侧 tool_invocations 对称，守住「可审计」卖点（课件「第四步」明确点名）。
CREATE TABLE IF NOT EXISTS llm_calls (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id        TEXT,
    provider          TEXT NOT NULL,
    model             TEXT,
    prompt_tokens     INTEGER,
    completion_tokens INTEGER,
    total_tokens      INTEGER,
    elapsed_ms        BIGINT NOT NULL,
    success           INTEGER NOT NULL,    -- SQLite 无 BOOLEAN：0=false / 1=true（JPA 层用 Boolean 映射）
    error_message     TEXT,               -- 调用失败时记原因；成功时为 NULL
    created_at        TEXT NOT NULL       -- ISO-8601 字符串，由实体层写入（见 LlmCallEntity 注释）；不依赖 SQLite CURRENT_TIMESTAMP
);

-- 按会话查审计记录的索引
CREATE INDEX IF NOT EXISTS idx_llm_calls_session ON llm_calls(session_id);
-- 按 provider/model 维度的成本聚合索引（课件「第四步」成本透明基础版）
CREATE INDEX IF NOT EXISTS idx_llm_calls_provider ON llm_calls(provider, model);