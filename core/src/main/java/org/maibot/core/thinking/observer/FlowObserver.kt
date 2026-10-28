package org.maibot.core.thinking.observer

import org.maibot.core.thinking.FlowState
import org.maibot.core.thinking.FlowStatePersister
import org.maibot.core.thinking.FlowWakeup
import org.maibot.core.thinking.ObservationTrigger
import org.maibot.core.thinking.ThinkingFlow
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 每个思维流的观察循环
 *
 * 负责观察时序、退避和 cap 退出；具体「这次观察做什么」由 [ObservationStrategy] 决定，
 * 工具触发的状态迁移由 [ThinkingFlow] 的状态切换方法完成，这里只感知结果并重算计时。
 *
 * 退避规则：
 * - 每次 `wait_and_watch` 后间隔按 growthFactor 增长，直到当前状态的 max；
 * - 到达 max 后仍给两轮观察机会，两轮都没有新消息/发言/状态动作就退出当前状态；
 * - 新消息和成功 `speak` 重置回当前状态的 base；
 * - 状态迁移沿用并 clamp 当前间隔，但 cap 轮次计数清零。
 */
class FlowObserver(
    private val flow: ThinkingFlow,
    private val strategy: ObservationStrategy,
    private val persister: FlowStatePersister,
    private val now: () -> Long = { System.currentTimeMillis() },
) : Runnable {
    @Volatile
    private var running = true

    private val timing: FlowTiming
        get() = flow.timing

    /** 下一次周期观察的绝对时间；0 表示当前状态不需要 tick（INACTIVE） */
    private var nextTickAt: Long = 0

    /** 本次观察开始前的状态，用于判断工具是否完成了迁移 */
    private var stateBeforeObservation: FlowState = FlowState.INACTIVE

    /** 连续观察失败次数，用于指数退避，避免模型忙时每秒重试 */
    private var consecutiveRetries: Int = 0

    /** 本次观察是否包含新消息/发言；有活动时不增长退避 */
    private var lastObservationHadActivity: Boolean = false

    override fun run() {
        initializeSchedule()
        try {
            while (running) {
                when (flow.state) {
                    FlowState.INACTIVE -> runInactive()
                    FlowState.ACTIVE -> runActive()
                    FlowState.FOCUSED -> runFocused()
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            log.error("观察线程退出（stream: {}）", flow.id, e)
        }
    }

    /**
     * 启动时根据恢复的状态初始化首次 tick
     */
    private fun initializeSchedule() {
        flow.resetBackoff()
        nextTickAt = when (flow.state) {
            FlowState.INACTIVE -> 0
            FlowState.ACTIVE -> now() + flow.currentIntervalMs
            FlowState.FOCUSED -> if (flow.hasUnobservedMessages()) now() else now() + flow.currentIntervalMs
        }
    }

    /**
     * 请求停止观察循环
     */
    fun stop() {
        running = false
        flow.stopObserving()
    }

    /**
     * INACTIVE：阻塞等待外部消息；有未观察消息时激活并立即观察。
     */
    private fun runInactive() {
        if (flow.hasUnobservedMessages()) {
            activate()
            return
        }

        when (flow.awaitChange(null)) {
            FlowWakeup.STOPPED -> running = false
            FlowWakeup.MESSAGE -> {
                if (flow.hasUnobservedMessages()) {
                    activate()
                }
            }

            FlowWakeup.TIMEOUT -> Unit
        }
    }

    /**
     * ACTIVE：按退避 tick 观察；新消息只入窗、缩短下一次 tick，不立即触发。
     */
    private fun runActive() {
        if (flow.awaitChange(nextTickAt) == FlowWakeup.STOPPED) {
            running = false
            return
        }

        val currentTime = now()
        val hasNewMessage = flow.hasUnobservedMessages()

        // 先按“新消息”重置退避，再决定是提前 tick 还是直接观察。
        // 这样即使消息恰好和 tick 同时到达，也不会出现“只观察、不重置计时”的临界情况。
        if (hasNewMessage) {
            flow.resetBackoff()
            if (currentTime < nextTickAt) {
                nextTickAt = minOf(nextTickAt, currentTime + flow.currentIntervalMs)
                return
            }
        }

        if (nextTickAt in 1..currentTime) {
            val trigger = if (hasNewMessage) {
                ObservationTrigger.NEW_MESSAGE
            } else {
                ObservationTrigger.PERIODIC
            }
            handleResult(observeOnce(trigger))
        }
    }

    /**
     * FOCUSED：新消息立即观察；无新消息时按退避间隔做主动观察。
     */
    private fun runFocused() {
        when (flow.awaitChange(nextTickAt)) {
            FlowWakeup.STOPPED -> {
                running = false
                return
            }

            FlowWakeup.MESSAGE, FlowWakeup.TIMEOUT -> Unit
        }

        if (flow.hasUnobservedMessages()) {
            flow.resetBackoff()
            val result = observeOnce(ObservationTrigger.NEW_MESSAGE)
            handleResult(result)
            return
        }

        if (nextTickAt > 0 && now() >= nextTickAt) {
            val result = observeOnce(ObservationTrigger.PERIODIC)
            handleResult(result)
        }
    }

    /**
     * INACTIVE → ACTIVE 的激活路径：立即观察一次，然后从 ACTIVE 的 base 开始退避。
     */
    private fun activate() {
        flow.resetBackoff()
        transitionTo(FlowState.ACTIVE, "外部消息激活")

        val result = observeOnce(ObservationTrigger.ACTIVATION)
        handleResult(result, fromActivation = true)
    }

    /**
     * 执行一次观察
     *
     * 成功后推进 lastObservedSequence；Retry 不推进，保持新消息未观察状态。
     * 这里同时判断本次观察是否发生了新消息/发言，用于决定是否重置退避。
     */
    private fun observeOnce(trigger: ObservationTrigger): ObservationResult {
        val snapshot = flow.snapshot(trigger)
        stateBeforeObservation = flow.state
        val result = try {
            strategy.observe(flow, snapshot)
        } catch (e: Exception) {
            log.error("观察失败（stream: {}, trigger: {}）", flow.id, trigger, e)
            return ObservationResult.Retry()
        }

        if (result !is ObservationResult.Retry) {
            val stateChanged = flow.state != stateBeforeObservation
            lastObservationHadActivity = snapshot.newSequences.isNotEmpty() ||
                    trigger == ObservationTrigger.ACTIVATION ||
                    flow.lastActivityAt > snapshot.lastActivityAt
            // 状态迁移已经在 ThinkingFlow 内完成并带走了原间隔，这里不能再按“有活动”重置
            if (lastObservationHadActivity && !stateChanged) {
                flow.resetBackoff()
            }
            // 关注类迁移视为活动，和旧实现保持一致；退出观察不算活动
            if (stateChanged && flow.state != FlowState.INACTIVE) {
                flow.markActivity(now())
            }

            val maxSequence = snapshot.entries.maxOfOrNull { it.sequence } ?: snapshot.lastObservedSequence
            flow.markObserved(maxSequence, decisionOf(result, stateChanged))
            saveThinkingFlow()
        }

        return result
    }

    /**
     * 应用观察结果并重算计时器
     */
    private fun handleResult(result: ObservationResult, fromActivation: Boolean = false) {
        if (result !is ObservationResult.Retry) {
            consecutiveRetries = 0
        }

        when (result) {
            ObservationResult.Wait -> {
                // 激活后的首次观察不计入退避增长，下一次从 base 开始
                if (lastObservationHadActivity || fromActivation) {
                    flow.resetBackoff()
                    scheduleNext()
                    logWait()
                    return
                }

                // 到达 max 后先给两轮观察；两轮仍 wait 就退出当前状态
                if (flow.currentIntervalMs >= timing.maxFor(flow.state)) {
                    flow.recordCapWait()
                    if (flow.capWaitCount >= CAP_WAIT_ROUNDS) {
                        logWait()
                        exitAfterCap()
                        return
                    }
                    scheduleNext()
                } else {
                    flow.growBackoff()
                    scheduleNext()
                }
                logWait()
            }

            ObservationResult.StateAction -> {
                // 状态迁移和计时重置已在 ThinkingFlow 的状态切换方法内完成，
                // 这里只负责把状态变化写进日志并重算下一次 tick。
                val oldState = stateBeforeObservation
                val newState = flow.state
                if (oldState != newState) {
                    logTransition(oldState, newState, flow.lastTransitionReason ?: "state_action")
                } else if (flow.hasUnobservedMessages()) {
                    log.info("观察期间有新消息，优先补一轮观察（stream: {}）", flow.id)
                }

                nextTickAt = when (newState) {
                    FlowState.INACTIVE -> 0
                    FlowState.ACTIVE, FlowState.FOCUSED ->
                        if (flow.hasUnobservedMessages()) now() else now() + flow.currentIntervalMs
                }
            }

            is ObservationResult.Retry -> {
                consecutiveRetries++
                val delay = minOf(
                    1_000L shl (consecutiveRetries - 1).coerceAtMost(5),
                    MAX_RETRY_DELAY_MS,
                )
                log.warn(
                    "观察失败（stream: {}），第 {} 次退避 {}ms 后重试",
                    flow.id, consecutiveRetries, delay
                )
                sleep(delay)
                nextTickAt = now()
            }
        }
    }

    /**
     * 到达退避上限后两轮仍无动作：ACTIVE 退出观察，FOCUSED 降级回 ACTIVE。
     */
    private fun exitAfterCap() {
        if (flow.hasUnobservedMessages()) {
            flow.resetBackoff()
            scheduleNext()
            return
        }

        when (flow.state) {
            FlowState.ACTIVE -> {
                transitionTo(FlowState.INACTIVE, "ACTIVE 观察上限两轮无动作")
                nextTickAt = 0
            }

            FlowState.FOCUSED -> {
                transitionTo(FlowState.ACTIVE, "FOCUSED 观察上限两轮无动作")
                scheduleNext()
            }

            FlowState.INACTIVE -> Unit
        }
    }

    private fun scheduleNext() {
        nextTickAt = when (flow.state) {
            FlowState.INACTIVE -> 0
            FlowState.ACTIVE, FlowState.FOCUSED -> now() + flow.currentIntervalMs
        }
    }

    /**
     * 观察循环自己触发的迁移（激活、cap 退出）：调用 ThinkingFlow 的统一迁移入口，
     * 再补持久化和日志。
     */
    private fun transitionTo(newState: FlowState, reason: String) {
        val oldState = flow.state
        if (!flow.transitionTo(newState, reason)) {
            return
        }
        saveThinkingFlow()
        logTransition(oldState, newState, reason)
    }

    private fun logTransition(oldState: FlowState, newState: FlowState, reason: String) {
        log.info(
            "观察状态切换: stream={}, {} -> {}, reason={}, interval={}ms",
            flow.id, oldState, newState, reason, flow.currentIntervalMs
        )
    }

    private fun logWait() {
        log.debug(
            "观察决策 wait_and_watch: stream={}, state={}, interval={}ms, capRound={}/{}",
            flow.id, flow.state, flow.currentIntervalMs, flow.capWaitCount, CAP_WAIT_ROUNDS
        )
    }

    private fun saveThinkingFlow() {
        try {
            persister.saveThinkingFlow(flow)
        } catch (e: Exception) {
            log.warn("保存思维流状态失败（stream: {}）", flow.id, e)
        }
    }

    private fun decisionOf(result: ObservationResult, stateChanged: Boolean): String {
        return when (result) {
            ObservationResult.Wait -> "wait"
            ObservationResult.StateAction -> {
                if (stateChanged) {
                    flow.lastTransitionReason ?: "state_action"
                } else {
                    "state_action"
                }
            }
            is ObservationResult.Retry -> "retry"
        }
    }

    private fun sleep(millis: Long) {
        if (millis > 0) {
            Thread.sleep(millis)
        }
    }

    companion object {
        /** 到达 max 后允许观察的轮数 */
        private const val CAP_WAIT_ROUNDS = 2

        private const val MAX_RETRY_DELAY_MS = 30_000L

        private val log: Logger = LoggerFactory.getLogger(FlowObserver::class.java)
    }
}
