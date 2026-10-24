package org.maibot.sdk.storage.db.dao

import jakarta.persistence.*
import java.time.Instant

/** 模型API请求统计表（热数据）
 *
 * 每「一次 LLM 任务」写一行：任务开始时插入 IN_PROGRESS，结束时更新为
 * SUCCESS / FAILED；中间单次请求失败与重试不单独落行。
 * 字段都是可聚合、可索引的标量，统计查询只碰这张表；
 * 原始输出内容存在 model_interaction（冷数据）里。
 *
 * 字段包括：
 *
 * | 字段名 | 类型 | 描述 |
 * | --- | --- | --- |
 * | id | Long | 主键，自增 |
 * | timestamp | Instant | 请求时间戳 |
 * | task_name | String | 任务名称 |
 * | model_name | String | 模型名称，关联模型配置表中的模型名称字段 |
 * | api_provider | String | API提供商名称，关联模型配置表中的API提供商名称字段 |
 * | input_tokens | Long | 输入的token数量 |
 * | output_tokens | Long | 输出的token数量 |
 * | total_tokens | Long | 输入+输出token数量 |
 * | total_cost | Double | 本次调用的总费用，单位为元 |
 * | duration_ms | Long | 本次调用耗时（毫秒） |
 * | status | String | 调用状态：SUCCESS / FAILED |
 * | cached_input_tokens | Long? | 缓存命中的输入token数；provider未上报时为空 |
 * | cache_hit_rate | Double? | 缓存命中率；provider未上报时为空 | */
@Entity
@Table(
    name = "model_api_request",
    indexes = [Index(
        name = "idx_model_api_request_timestamp",
        columnList = "timestamp"
    ), Index(
        name = "idx_model_api_request_task_name",
        columnList = "task_name"
    ), Index(
        name = "idx_model_api_request_model_name",
        columnList = "model_name"
    ), Index(name = "idx_model_api_request_api_provider", columnList = "api_provider")]
)
class ModelApiRequest {
    @JvmField
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 请求时间戳 */
    @JvmField
    @Column(name = "timestamp", nullable = false)
    var timestamp: Instant? = null

    /** 任务名称 */
    @JvmField
    @Column(name = "task_name", nullable = false)
    var taskName: String? = null

    /** 模型名称，关联模型配置表中的模型名称字段 */
    @JvmField
    @Column(name = "model_name", nullable = false)
    var modelName: String? = null

    /** API提供商名称，关联模型配置表中的API提供商名称字段 */
    @JvmField
    @Column(name = "api_provider", nullable = false)
    var apiProvider: String? = null

    /** 输入的token数量 */
    @JvmField
    @Column(name = "input_tokens", nullable = false)
    var inputTokens: Long? = null

    /** 输出的token数量 */
    @JvmField
    @Column(name = "output_tokens", nullable = false)
    var outputTokens: Long? = null

    /** 输入+输出token数量 */
    @JvmField
    @Column(name = "total_tokens", nullable = false)
    var totalTokens: Long? = null

    /** 本次调用的总费用，单位为元 */
    @JvmField
    @Column(name = "total_cost", nullable = false)
    var totalCost: Double? = null

    /** 本次调用耗时（毫秒） */
    @JvmField
    @Column(name = "duration_ms", nullable = false)
    var durationMs: Long? = null

    /** 调用状态：SUCCESS / FAILED */
    @JvmField
    @Column(name = "status", nullable = false)
    var status: String? = null

    /** 缓存命中的输入token数；provider未上报时为空 */
    @JvmField
    @Column(name = "cached_input_tokens")
    var cachedInputTokens: Long? = null

    /** 缓存命中率；provider未上报时为空 */
    @JvmField
    @Column(name = "cache_hit_rate")
    var cacheHitRate: Double? = null
}
