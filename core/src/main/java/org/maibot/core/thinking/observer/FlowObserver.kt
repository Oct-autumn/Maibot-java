package org.maibot.core.thinking.observer

import org.maibot.core.thinking.ThinkingFlow
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 每个思维流的观察循环
 *
 * 只负责等待窗口变化、调用策略、处理结果与退避；不决定说什么。
 */
class FlowObserver(
    private val flow: ThinkingFlow,
    private val strategy: ObservationStrategy,
) : Runnable {
    @Volatile
    private var running = true

    override fun run() {
        while (running) {
            val snapshot = flow.awaitPending() ?: break

            try {
                when (val result = strategy.observe(flow, snapshot)) {
                    ObservationResult.Continue -> Unit
                    ObservationResult.Stop -> break
                    is ObservationResult.Retry -> sleep(result.delayMs)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                log.error("观察线程处理失败（stream: {}）", flow.id, e)
                try {
                    sleep(RETRY_DELAY_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
    }

    /**
     * 请求停止观察循环
     */
    fun stop() {
        running = false
        flow.stopObserving()
    }

    private fun sleep(millis: Long) {
        if (millis > 0) {
            Thread.sleep(millis)
        }
    }

    companion object {
        private const val RETRY_DELAY_MS = 1_000L

        private val log: Logger = LoggerFactory.getLogger(FlowObserver::class.java)
    }
}
