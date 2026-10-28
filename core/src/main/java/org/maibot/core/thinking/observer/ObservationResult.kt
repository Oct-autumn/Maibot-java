package org.maibot.core.thinking.observer

/**
 * 一次观察的结果
 *
 * `speak` 不是结果：它是观察轮中的副作用；成功发言后由 Bot 消息刷新活动时间。
 */
sealed interface ObservationResult {
    /** 本次没有状态动作，继续按当前状态观察 */
    data object Wait : ObservationResult

    /**
     * 本次执行了状态动作工具（focus/unfocus/quit）
     *
     * 状态和计时已经由 ThinkingFlow 的状态切换方法处理，观察循环只负责重算下一次 tick。
     */
    data object StateAction : ObservationResult

    /** 本轮失败，退避后重试 */
    data class Retry(val delayMs: Long = 1_000) : ObservationResult
}
