-- M2: 模型调用存档
-- 热表 model_api_request 扩列（现有数据用默认值补齐），新增冷表 model_interaction

-- 调用状态：SUCCESS / FAILED
ALTER TABLE 'model_api_request' ADD COLUMN 'status' VARCHAR(32) NOT NULL DEFAULT 'SUCCESS';

-- 输入+输出 token 总数
ALTER TABLE 'model_api_request' ADD COLUMN 'total_tokens' INTEGER NOT NULL DEFAULT 0;

-- 本次调用耗时（毫秒）
ALTER TABLE 'model_api_request' ADD COLUMN 'duration_ms' INTEGER NOT NULL DEFAULT 0;

-- 统计查询常用组合索引
CREATE INDEX IF NOT EXISTS 'idx_model_api_request_task_timestamp'
    ON 'model_api_request' ('task_name', 'timestamp');
CREATE INDEX IF NOT EXISTS 'idx_model_api_request_model_timestamp'
    ON 'model_api_request' ('model_name', 'timestamp');

-- 模型调用内容存档（冷数据，与 model_api_request 一对一）
CREATE TABLE IF NOT EXISTS 'model_interaction'
(
    'request_id'       INTEGER PRIMARY KEY,
    'stream_id'        VARCHAR(255),
    'trigger_sequence' INTEGER,
    'reasoning'        MEDIUMTEXT,
    'content'          MEDIUMTEXT,
    'tool_calls_json'  MEDIUMTEXT,
    'finish_reason'    VARCHAR(255),
    'created_at'       BIGINT NOT NULL,
    FOREIGN KEY ('request_id') REFERENCES 'model_api_request' ('id') ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS 'idx_model_interaction_stream'
    ON 'model_interaction' ('stream_id', 'trigger_sequence');
CREATE INDEX IF NOT EXISTS 'idx_model_interaction_created'
    ON 'model_interaction' ('created_at');
