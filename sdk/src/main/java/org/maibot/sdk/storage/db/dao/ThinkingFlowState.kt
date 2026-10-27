package org.maibot.sdk.storage.db.dao

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 思维流观察状态持久化实体
 *
 * 一个交互流对应一行；只保存状态机需要的元数据，观察窗口内容仍从
 * [Message] 表恢复。
 */
@Entity
@Table(name = "thinking_flow_state")
class ThinkingFlowState {
    @JvmField
    @Id
    @Column(name = "stream_id", nullable = false)
    var streamId: String? = null

    /**
     * 观察状态：INACTIVE / ACTIVE / FOCUSED
     */
    @JvmField
    @Column(name = "state", nullable = false)
    var state: String? = null

    /**
     * 最后一次活动时间（外部消息或 Bot 发言），epoch millis
     */
    @JvmField
    @Column(name = "last_activity_at", nullable = false)
    var lastActivityAt: Long = 0

    /**
     * 最后一次成功观察到的消息 sequence；用于重启后识别未观察消息
     */
    @JvmField
    @Column(name = "last_observed_sequence", nullable = false)
    var lastObservedSequence: Long = 0

    /**
     * 上次观察决策摘要，供恢复后的 prompt 使用
     */
    @JvmField
    @Column(name = "last_decision")
    var lastDecision: String? = null

    @JvmField
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Long = 0
}
