-- OryxOS 核心阶段初始建表脚本（data-model.md 为语义依据，本文件为 DDL 唯一事实来源）
-- 宪法工程约束：表结构演进只能手改脚本，禁止依赖 ddl-auto 自动迁移。
-- 幂等：spring.sql.init.mode=always 每次启动执行，全部 CREATE ... IF NOT EXISTS。

CREATE TABLE IF NOT EXISTS sessions (
    id          TEXT PRIMARY KEY,
    agent_name  TEXT NOT NULL,
    channel     TEXT NOT NULL,
    user_id     TEXT NOT NULL,
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS session_messages (
    id              TEXT PRIMARY KEY,
    session_id      TEXT NOT NULL REFERENCES sessions(id),
    seq             INTEGER NOT NULL,
    role            TEXT NOT NULL,
    content         TEXT,
    tool_calls_json TEXT,
    tool_call_id    TEXT,
    created_at      TEXT NOT NULL,
    UNIQUE (session_id, seq)
);

CREATE INDEX IF NOT EXISTS idx_session_messages_session ON session_messages(session_id, seq);

-- 审计表（宪法原则六：day one 落库，只增不改）
CREATE TABLE IF NOT EXISTS llm_calls (
    id                TEXT PRIMARY KEY,
    session_id        TEXT,
    provider_name     TEXT NOT NULL,
    model             TEXT NOT NULL,
    iteration         INTEGER NOT NULL,
    request_json      TEXT,
    response_json     TEXT,
    request_bytes     INTEGER,
    response_bytes    INTEGER,
    prompt_tokens     INTEGER,
    completion_tokens INTEGER,
    duration_ms       INTEGER NOT NULL,
    success           INTEGER NOT NULL,
    error_message     TEXT,
    created_at        TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_llm_calls_session ON llm_calls(session_id);

CREATE TABLE IF NOT EXISTS tool_invocations (
    id              TEXT PRIMARY KEY,
    session_id      TEXT,
    tool_name       TEXT NOT NULL,
    arguments_json  TEXT,
    result_preview  TEXT,
    duration_ms     INTEGER NOT NULL,
    success         INTEGER NOT NULL,
    error_message   TEXT,
    created_at      TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tool_invocations_session ON tool_invocations(session_id);
