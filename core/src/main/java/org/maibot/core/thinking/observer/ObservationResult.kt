package org.maibot.core.thinking.observer

/**
 * 一次观察的结果
 *
 * `speak` 不是结果：它是观察轮中的副作用；成功发言后由 Bot 消息刷新活动时间。
 */
sealed interface ObservationResult {
    /** 本次不改变状态，继续按当前状态观察 */
    data object Wait : ObservationResult

    /** 进入关注状态 */
    data object Focus : ObservationResult

    /** 退出关注，回到 ACTIVE */
    data object Unfocus : ObservationResult

    /** 退出观察，回到 INACTIVE */
    data object Quit : ObservationResult

    /** 本轮失败，退避后重试 */
    data class Retry(val delayMs: Long = 1_000) : ObservationResult
}
