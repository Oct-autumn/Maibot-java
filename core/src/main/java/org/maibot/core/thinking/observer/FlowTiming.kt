package org.maibot.core.thinking.observer

import org.maibot.core.config.CoreConfig
import org.maibot.core.thinking.FlowState

/**
 * 观察循环的计时参数（毫秒）
 *
 * 退避是“连续 wait 越来越稀疏”的节奏：
 * 每次 wait 后间隔按 growthFactor 增长，直到 max；到达 max 后仍给两轮观察机会，
 * 两轮都没有新消息/发言/状态动作就退出当前状态。
 *
 * 单独抽出来便于状态机单测使用短间隔。
 */
data class FlowTiming(
    val activeBaseIntervalMs: Long,
    val activeMaxIntervalMs: Long,
    val focusBaseIntervalMs: Long,
    val focusMaxIntervalMs: Long,
    val growthFactor: Double,
) {
    fun baseFor(state: FlowState): Long {
        return when (state) {
            FlowState.ACTIVE -> activeBaseIntervalMs
            FlowState.FOCUSED -> focusBaseIntervalMs
            FlowState.INACTIVE -> 0
        }
    }

    fun maxFor(state: FlowState): Long {
        return when (state) {
            FlowState.ACTIVE -> activeMaxIntervalMs
            FlowState.FOCUSED -> focusMaxIntervalMs
            FlowState.INACTIVE -> 0
        }
    }

    fun clamp(intervalMs: Long, state: FlowState): Long {
        val base = baseFor(state)
        val max = maxFor(state)
        if (base <= 0 || max <= 0) {
            return 0
        }
        return intervalMs.coerceIn(base, maxOf(base, max))
    }

    fun grow(intervalMs: Long, state: FlowState): Long {
        val base = baseFor(state)
        val max = maxOf(base, maxFor(state))
        if (base <= 0 || max <= 0) {
            return 0
        }

        val current = intervalMs.coerceIn(base, max)
        val next = if (growthFactor > 1.0) {
            (current * growthFactor).toLong()
        } else {
            current
        }
        return next.coerceIn(base, max)
    }

    companion object {
        fun from(config: CoreConfig.Thinking): FlowTiming {
            return FlowTiming(
                activeBaseIntervalMs = config.activeIntervalSec * 1_000,
                activeMaxIntervalMs = maxOf(config.activeIntervalSec, config.activeMaxIntervalSec) * 1_000,
                focusBaseIntervalMs = config.focusIntervalSec * 1_000,
                focusMaxIntervalMs = maxOf(config.focusIntervalSec, config.focusMaxIntervalSec) * 1_000,
                growthFactor = config.intervalGrowthFactor.coerceAtLeast(1.0),
            )
        }
    }
}
