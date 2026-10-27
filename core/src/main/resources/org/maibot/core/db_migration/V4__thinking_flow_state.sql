-- M3b: 思维流观察状态持久化
-- 每个交互流一行，用于启动时恢复 INACTIVE / ACTIVE / FOCUSED 与计时元数据。
-- 观察窗口内容仍从 message 表恢复，本表不保存消息。

CREATE TABLE IF NOT EXISTS 'thinking_flow_state'
(
    'stream_id'               VARCHAR(255) PRIMARY KEY,
    'state'                   VARCHAR(16)  NOT NULL,
    'last_activity_at'        BIGINT       NOT NULL,
    'last_observed_sequence'  BIGINT       NOT NULL DEFAULT 0,
    'last_decision'           VARCHAR(255),
    'updated_at'              BIGINT       NOT NULL,
    FOREIGN KEY ('stream_id') REFERENCES 'interaction_stream' ('id') ON DELETE CASCADE
);
