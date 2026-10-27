package org.maibot.core.thinking

/**
 * 从数据库恢复的思维流状态元数据
 *
 * 计时器（nextTickAt、关注 deadline）不持久化，恢复时按规则重算。
 *
 * @param state                  观察状态
 * @param lastActivityAt         最后一次活动时间（epoch millis）
 * @param lastObservedSequence   最后一次成功观察到的消息 sequence
 * @param lastDecision           上次观察决策摘要
 */
data class PersistedFlowState(
    val state: FlowState,
    val lastActivityAt: Long,
    val lastObservedSequence: Long,
    val lastDecision: String? = null,
)
