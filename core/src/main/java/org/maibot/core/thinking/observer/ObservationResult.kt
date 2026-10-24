package org.maibot.core.thinking.observer

/**
 * 一次观察的结果
 *
 * W/O 阶段只会用到 CONTINUE / RETRY；M3b 的 ObserverAI 可以扩展出
 * FOCUS / SLEEP / STOP 等语义。
 */
sealed interface ObservationResult {
    /** 继续观察（窗口里还有未处理消息时会立即再次进入） */
    data object Continue : ObservationResult

    /** 结束这个流的观察循环 */
    data object Stop : ObservationResult

    /** 失败后退避重试 */
    data class Retry(val delayMs: Long = 1_000) : ObservationResult
}
