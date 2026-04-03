package org.maibot.core.thinking

import lombok.Getter
import lombok.Setter
import org.maibot.core.thinking.ThinkingFlow.FlowState.*
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.storage.db.dao.InteractionStream.Companion.idGen
import org.maibot.sdk.storage.db.dao.Message
import org.maibot.sdk.storage.domain.StreamType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile

/**
 * 思维流组件
 *
 *
 * 思维流是对交互流的思考与决策单元。<br></br>
 * 它负责观察交互流中的消息内容，并根据预设的规则和模型做出相应的反应和决策。
 */
@Component(singleton = false)
class ThinkingFlow(
    private val observationWindowSize: Int,
    /** 交互流ID */
    private val id: String?
) {
    /** 观察者 */
    private val flowObserver = FlowObserver(this)

    /** 交互流观察窗口 */
    private val observationWindow: Deque<Message?> = ArrayDeque<Message?>()

    /** 上次活跃时间戳 */
    private var lastActiveTimestamp = System.currentTimeMillis()

    /** 交互流状态 */
    var state = SLEEPING
        set(value) {
            field = value
            flowObserver.onStateChange(value)
        }

    fun observe() {
        this.flowObserver.run()
    }

    fun stopObserving() {
        this.flowObserver.stop()
    }

    fun addToObservationWindow(message: Message) {
        this.observationWindow.addLast(message)
        // 限制观察窗口大小，例如最多保留最近100条消息
        while (this.observationWindow.size > observationWindowSize) {
            this.observationWindow.removeFirst()
        }
        this.lastActiveTimestamp = System.currentTimeMillis()
        if (!this.state.isAtLeast(ACTIVE)) {
            this.state = ACTIVE
        }
    }

    enum class FlowState(@field:Getter private val code: Int) {
        SLEEPING(0),
        ACTIVE(1),
        FOCUSED(2);

        fun isAtLeast(other: FlowState): Boolean {
            return this.code >= other.code
        }
    }

    /**
     * 流观察器
     */
    class FlowObserver(private val thinkingFlow: ThinkingFlow) : Runnable {
        private val log: Logger = LoggerFactory.getLogger(FlowObserver::class.java)

        private val lock = ReentrantLock()
        private val activityCondition: Condition = lock.newCondition()

        @Volatile
        private var running = true

        /** 交互流处于激活状态下的观察间隔 */
        @Setter
        private val activeObservationIntervalSec = 15

        /** 交互流处于专注状态下的观察间隔 */
        @Setter
        private val focusedObservationIntervalSec = 5

        /**
         * 交互流状态变更回调
         *
         * @param newState 新的交互流状态
         */
        fun onStateChange(newState: FlowState) {
            // 如果新的状态是激活或更高，发出信号以唤醒观察线程
            if (newState.isAtLeast(ACTIVE)) {
                this.signalObserver()
            }
        }

        private fun signalObserver() {
            lock.lock()
            try {
                activityCondition.signal()
            } finally {
                lock.unlock()
            }
        }

        /**
         * 停止观察
         */
        fun stop() {
            running = false
            this.signalObserver()
        }

        override fun run() {
            while (running) {
                // 如果流处于休眠状态，等待激活信号
                try {
                    lock.lock()
                    while (thinkingFlow.state == SLEEPING && running) {
                        log.debug("ThinkingFlow {} is sleeping, waiting for activation...", thinkingFlow.id)
                        activityCondition.await()
                    }
                    if (!running) break
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } finally {
                    lock.unlock()
                }

                // 观察交互流内容并做出决策
                // TODO: 实现观察逻辑

                try {
                    lock.lock()
                    val flowState = thinkingFlow.state
                    when (flowState) {
                        ACTIVE -> activityCondition.await(
                            activeObservationIntervalSec.toLong(),
                            TimeUnit.SECONDS
                        )

                        FOCUSED -> activityCondition.await(
                            focusedObservationIntervalSec.toLong(),
                            TimeUnit.SECONDS
                        )

                        SLEEPING -> {
                            // 已经在休眠状态，继续等待激活信号
                        }
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } finally {
                    lock.unlock()
                }
            }
        }
    }

    companion object {
        fun idGen(id: Long, streamType: StreamType): String {
            return idGen(streamType, id)
        }
    }
}
