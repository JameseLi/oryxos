-- 001-init.sql - OryxOS SQLite 手工建表脚本（宪法工程约束：不依赖 hibernate.ddl-auto=update）
-- 第 16 节：llm_calls 审计表；第 17 节：追加 tool_invocations 审计表（成败都落库--宪法原则 VI）；
-- 第 18 节：追加 sessions 会话表（session_id 由 SessionManager 按 channel+user+profile 唯一拼接）。
-- 后续节按需在本脚本追加 scheduled_tasks / task_executions 等表。

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

-- tool_invocations：每次工具调用记录（第 17 节，口径与 llm_calls 一致）。
--   success / error_message 两列必须存在：工具执行失败时这次事故在系统里也要有痕迹
--   （课件「配角二」：成功要记、失败也要记--只记成功不记失败 = 事故无痕，属易漏缺陷）。
CREATE TABLE IF NOT EXISTS tool_invocations (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id    TEXT,
    tool_name     TEXT NOT NULL,
    arguments     TEXT,                -- 模型给出的 JSON 入参原文
    result        TEXT,                -- 工具结果文本（失败时可为 NULL，原因在 error_message）
    elapsed_ms    BIGINT NOT NULL,
    success       INTEGER NOT NULL,    -- SQLite 无 BOOLEAN：0=false / 1=true（JPA 层用 Boolean 映射）
    error_message TEXT,                -- 执行失败时记原因；成功时为 NULL
    created_at    TEXT NOT NULL        -- ISO-8601 字符串，由实体层写入（同 llm_calls 口径）；不依赖 SQLite CURRENT_TIMESTAMP
);

-- 按会话查审计记录的索引（与 llm_calls 对称）
CREATE INDEX IF NOT EXISTS idx_tool_invocations_session ON tool_invocations(session_id);
-- 按工具名维度的聚合索引（哪个工具被调了多少次/失败率）
CREATE INDEX IF NOT EXISTS idx_tool_invocations_tool ON tool_invocations(tool_name);

-- sessions：会话元数据 + JSON 序列化的对话历史（第 18 节，技术方案 9.2）。
--   session_id 主键由 SessionManager 按 channel+user+profile 唯一拼接（拼接只在那一个实现内部，
--   所有入口只提供三元组）；对话历史整体序列化进 messages_json 一列，核心阶段不做按条拆表。
--   channel/user_id 可空：第 17 节时代的会话经 save 落库时可能不带三元组身份。
CREATE TABLE IF NOT EXISTS sessions (
    session_id     TEXT PRIMARY KEY,
    profile_name   TEXT NOT NULL,
    channel        TEXT,
    user_id        TEXT,
    messages_json  TEXT,                -- JSON 数组，空历史为 "[]"；由存储层 MessageJsonCodec 编解码
    status         TEXT NOT NULL,       -- active / archived
    created_at     TEXT NOT NULL,       -- ISO-8601 字符串，由实体层写入（口径同 llm_calls）；不依赖 SQLite CURRENT_TIMESTAMP
    last_active_at TEXT NOT NULL,       -- 每次save更新
    archived_at    TEXT                 -- 归档时间；未归档为 NULL
);

-- 按 Profile 维度查会话的索引（session list / Agent 视角）
CREATE INDEX IF NOT EXISTS idx_sessions_profile ON sessions(profile_name);