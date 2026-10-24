-- M3: 模型调用缓存命中统计
-- 在 model_api_request 上追加缓存字段；provider 未上报时保持 NULL。
-- 本地模型（如 LM Studio）通常不返回 prompt_tokens_details.cached_tokens，
-- 因此不能用 0 表示“未命中”，必须用 NULL 区分“不支持统计”。

ALTER TABLE 'model_api_request' ADD COLUMN 'cached_input_tokens' INTEGER;

ALTER TABLE 'model_api_request' ADD COLUMN 'cache_hit_rate' REAL;

CREATE INDEX IF NOT EXISTS 'idx_model_api_request_cache_hit'
    ON 'model_api_request' ('task_name', 'cache_hit_rate');
