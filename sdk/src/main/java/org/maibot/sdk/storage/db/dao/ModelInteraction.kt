package org.maibot.sdk.storage.db.dao

import jakarta.persistence.*
import java.time.Instant

/**
 * 模型调用内容存档表（冷数据）
 *
 * 与 [ModelApiRequest] 一对一：只在需要复盘的任务上写入，内容是大文本
 * （reasoning / content / tool_calls_json），不参与统计查询，按保留期清理。
 *
 * | 字段名 | 类型 | 描述 |
 * | --- | --- | --- |
 * | request_id | Long | 主键，同时是 model_api_request.id 的外键 |
 * | stream_id | String | 交互流 ID（可空） |
 * | trigger_sequence | Long | 触发消息序列号（可空） |
 * | reasoning | String | 模型思考内容 |
 * | content | String | 模型正文输出 |
 * | tool_calls_json | String | 工具调用原文（含参数） |
 * | finish_reason | String | 结束原因（可空） |
 * | created_at | Instant | 写入时间 |
 */
@Entity
@Table(
    name = "model_interaction",
    indexes = [
        Index(name = "idx_model_interaction_stream", columnList = "stream_id, trigger_sequence"),
        Index(name = "idx_model_interaction_created", columnList = "created_at")
    ]
)
class ModelInteraction {
    /** 与 model_api_request.id 一对一 */
    @JvmField
    @Id
    @Column(name = "request_id", nullable = false)
    var requestId: Long? = null

    /** 交互流 ID（可空） */
    @JvmField
    @Column(name = "stream_id")
    var streamId: String? = null

    /** 触发本次调用的消息序列号（可空） */
    @JvmField
    @Column(name = "trigger_sequence")
    var triggerSequence: Long? = null

    /** 模型思考内容 */
    @JvmField
    @Column(name = "reasoning", columnDefinition = "MEDIUMTEXT")
    var reasoning: String? = null

    /** 模型正文输出 */
    @JvmField
    @Column(name = "content", columnDefinition = "MEDIUMTEXT")
    var content: String? = null

    /** 工具调用原文（JSON 数组，含参数） */
    @JvmField
    @Column(name = "tool_calls_json", columnDefinition = "MEDIUMTEXT")
    var toolCallsJson: String? = null

    /** 结束原因（可空） */
    @JvmField
    @Column(name = "finish_reason")
    var finishReason: String? = null

    /** 写入时间 */
    @JvmField
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null
}
