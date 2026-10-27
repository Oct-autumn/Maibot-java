package org.maibot.core.thinking

import org.maibot.sdk.ioc.Component
import org.maibot.sdk.storage.db.dao.InteractionStream.Companion.idGen
import org.maibot.sdk.storage.db.dao.Message
import org.maibot.sdk.storage.domain.StreamType
import org.maibot.sdk.util.MapperUtils
import java.util.*
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile

/**
 * 思维流组件
 *
 * 思维流是对交互流的思考与决策单元：维护观察窗口、观察状态、活动时间和版本号，
 * 并在窗口变化时唤醒观察线程。它不依赖模型和工具，只做状态容器。
 *
 * 观察循环在 [org.maibot.core.thinking.observer.FlowObserver] 中，
 * 具体「这次观察做什么」由 ObservationStrategy 决定。
 */
@Component(singleton = false)
class ThinkingFlow(
    private val observationWindowSize: Int,
    /** 交互流 ID */
    val id: String?,
) {
    /** 交互流观察窗口 */
    private val observationWindow: Deque<ObservationEntry> = ArrayDeque()

    /** 窗口锁与唤醒条件 */
    private val lock = ReentrantLock()
    private val changeCondition: Condition = lock.newCondition()

    /** 观察线程是否已请求停止 */
    private var stopped = false

    /** 窗口版本：每条外部消息 +1，供打断/更正策略判断快照是否过期 */
    @Volatile
    var version: Long = 0
        private set

    /** 观察窗口容量 */
    val capacity: Int
        get() = observationWindowSize

    /** 观察状态 */
    @Volatile
    var state: FlowState = FlowState.INACTIVE
        private set

    /** 最后一次活动时间：外部消息到达或 Bot 成功发言，epoch millis */
    @Volatile
    var lastActivityAt: Long = 0
        private set

    /** 上次成功观察到的消息 sequence */
    @Volatile
    var lastObservedSequence: Long = 0
        private set

    /** 上次观察的决策摘要 */
    @Volatile
    var lastDecision: String? = null
        private set

    /**
     * 追加一条外部消息，并唤醒观察线程
     *
     * 只更新窗口、版本和活动时间，不直接改状态；INACTIVE → ACTIVE 的迁移由观察线程执行。
     */
    fun addExternalMessage(message: Message): ObservationEntry {
        lock.lock()
        try {
            check(!stopped) { "思维流已停止: $id" }
            val entry = ObservationEntry(message, ObservationEntry.Origin.EXTERNAL, replied = false)
            appendLocked(entry)
            version++
            lastActivityAt = System.currentTimeMillis()
            changeCondition.signalAll()
            return entry
        } finally {
            lock.unlock()
        }
    }

    /**
     * 追加一条 Bot 自己发出的消息
     *
     * 该方法永远不会唤醒观察线程，避免 Bot 被自己的消息反复触发。
     *
     * @param message 已落库的 Bot 消息
     * @param replyTo 这条回复覆盖的外部消息 sequence 列表
     */
    fun addBotMessage(message: Message, replyTo: List<Long>): ObservationEntry {
        lock.lock()
        try {
            check(!stopped) { "思维流已停止: $id" }
            val entry = ObservationEntry(message, ObservationEntry.Origin.SELF, replied = true, replyTo = replyTo)
            appendLocked(entry)
            markRepliedLocked(replyTo)
            lastActivityAt = System.currentTimeMillis()
            return entry
        } finally {
            lock.unlock()
        }
    }

    /**
     * 直接把若干外部消息标记为已回复
     *
     * 用于「已经发送成功、但落库失败」的兜底：避免观察线程因为消息仍处于未回复状态
     * 而重复发言。
     */
    fun markReplied(sequences: Collection<Long>) {
        lock.lock()
        try {
            markRepliedLocked(sequences)
        } finally {
            lock.unlock()
        }
    }

    /**
     * 从数据库历史恢复窗口
     *
     * Bot 消息优先使用 raw_content_json 里的 reply_to_sequence；
     * 旧数据没有该字段时，退化为「该 Bot 消息之前的未回复外部消息都视为已回复」。
     *
     * 恢复后默认把窗口内最大 sequence 视为已观察，避免重启时重放旧消息；
     * 如果 DB 中存在状态行，[restoreState] 会用持久化的 lastObservedSequence 覆盖。
     */
    fun restoreHistory(messages: List<Message>) {
        lock.lock()
        try {
            check(!stopped) { "思维流已停止: $id" }
            for (message in messages) {
                if (message.objectType == BOT_MESSAGE_OBJECT_TYPE) {
                    val replyTo = parseReplyTo(message)
                    appendLocked(
                        ObservationEntry(
                            message = message,
                            origin = ObservationEntry.Origin.SELF,
                            replied = true,
                            replyTo = replyTo,
                        )
                    )
                    if (replyTo.isNotEmpty()) {
                        markRepliedLocked(replyTo)
                    } else {
                        markAllUnrepliedLocked()
                    }
                } else {
                    appendLocked(ObservationEntry(message, ObservationEntry.Origin.EXTERNAL, replied = false))
                    version++
                }
            }

            lastObservedSequence = observationWindow.maxOfOrNull { it.sequence } ?: 0
            lastActivityAt = observationWindow.maxOfOrNull { it.message.timestamp ?: 0 } ?: 0
        } finally {
            lock.unlock()
        }
    }

    /**
     * 用数据库中的状态元数据覆盖运行状态
     *
     * 计时器（nextTickAt / deadline）不在这里恢复，由 FlowObserver 计算。
     */
    fun restoreState(persisted: PersistedFlowState) {
        lock.lock()
        try {
            state = persisted.state
            lastObservedSequence = persisted.lastObservedSequence
            lastDecision = persisted.lastDecision
            lastActivityAt = maxOf(lastActivityAt, persisted.lastActivityAt)
        } finally {
            lock.unlock()
        }
    }

    /**
     * 生成当前状态快照
     *
     * @param trigger         本次观察触发原因
     * @param triggerSequence 本次关联的触发消息；为空时取最新新消息
     */
    fun snapshot(
        trigger: ObservationTrigger = ObservationTrigger.PERIODIC,
        triggerSequence: Long? = null,
    ): ObservationSnapshot {
        lock.lock()
        try {
            return buildSnapshotLocked(trigger, triggerSequence)
        } finally {
            lock.unlock()
        }
    }

    /**
     * 是否存在尚未观察过的外部消息
     */
    fun hasUnobservedMessages(): Boolean {
        lock.lock()
        try {
            return observationWindow.any { it.isExternal && it.sequence > lastObservedSequence }
        } finally {
            lock.unlock()
        }
    }

    /**
     * 标记快照中的消息已被观察
     *
     * 只推进到快照里的最大 sequence；观察期间到达的新消息会保持未观察状态。
     */
    fun markObserved(upToSequence: Long, decision: String?) {
        lock.lock()
        try {
            if (upToSequence > lastObservedSequence) {
                lastObservedSequence = upToSequence
            }
            lastDecision = decision
        } finally {
            lock.unlock()
        }
    }

    /**
     * 刷新活动时间
     *
     * 只应由外部消息到达、Bot 成功发言、状态迁移调用；周期 tick 和 wait 不算活动。
     */
    fun markActivity(now: Long = System.currentTimeMillis()) {
        lock.lock()
        try {
            lastActivityAt = now
        } finally {
            lock.unlock()
        }
    }

    /**
     * 应用状态迁移；状态迁移由 FlowObserver 单线程执行
     */
    fun transitionTo(newState: FlowState) {
        lock.lock()
        try {
            if (stopped || state == newState) {
                return
            }
            state = newState
            changeCondition.signalAll()
        } finally {
            lock.unlock()
        }
    }

    /**
     * 等待窗口变化或超时
     *
     * @param timeoutAt 绝对超时时间（epoch millis）；null 表示一直等待
     */
    fun awaitChange(timeoutAt: Long?): FlowWakeup {
        lock.lock()
        try {
            val startVersion = version
            while (true) {
                if (stopped) {
                    return FlowWakeup.STOPPED
                }
                if (version != startVersion) {
                    return FlowWakeup.MESSAGE
                }

                if (timeoutAt == null) {
                    changeCondition.await()
                    continue
                }

                val remaining = timeoutAt - System.currentTimeMillis()
                if (remaining <= 0) {
                    return FlowWakeup.TIMEOUT
                }
                changeCondition.awaitNanos(remaining * 1_000_000)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return FlowWakeup.STOPPED
        } finally {
            lock.unlock()
        }
    }

    /**
     * 导出当前状态元数据，供持久化/关闭时 flush
     */
    fun persistedState(): PersistedFlowState {
        return PersistedFlowState(
            state = state,
            lastActivityAt = lastActivityAt,
            lastObservedSequence = lastObservedSequence,
            lastDecision = lastDecision,
        )
    }

    /**
     * 请求停止观察线程
     */
    fun stopObserving() {
        lock.lock()
        try {
            stopped = true
            state = FlowState.INACTIVE
            changeCondition.signalAll()
        } finally {
            lock.unlock()
        }
    }

    private fun appendLocked(entry: ObservationEntry) {
        observationWindow.addLast(entry)
        while (observationWindow.size > observationWindowSize) {
            observationWindow.removeFirst()
        }
    }

    private fun markRepliedLocked(sequences: Collection<Long>): Boolean {
        if (sequences.isEmpty()) {
            return false
        }

        var changed = false
        val replaced = observationWindow.map { entry ->
            if (entry.isExternal && !entry.replied && entry.sequence in sequences) {
                changed = true
                entry.copy(replied = true)
            } else {
                entry
            }
        }

        if (changed) {
            observationWindow.clear()
            observationWindow.addAll(replaced)
        }
        return changed
    }

    private fun markAllUnrepliedLocked() {
        val replaced = observationWindow.map { entry ->
            if (entry.isExternal && !entry.replied) entry.copy(replied = true) else entry
        }
        observationWindow.clear()
        observationWindow.addAll(replaced)
    }

    private fun buildSnapshotLocked(
        trigger: ObservationTrigger,
        triggerSequence: Long?,
    ): ObservationSnapshot {
        val entries = observationWindow.toList()
        val newSequences = entries
            .asSequence()
            .filter { it.isExternal && it.sequence > lastObservedSequence }
            .map { it.sequence }
            .toSet()

        return ObservationSnapshot(
            streamId = requireNotNull(id) { "思维流 ID 不能为空" },
            version = version,
            state = state,
            entries = entries,
            newSequences = newSequences,
            lastObservedSequence = lastObservedSequence,
            lastActivityAt = lastActivityAt,
            trigger = trigger,
            triggerSequence = triggerSequence ?: newSequences.maxOrNull(),
            lastDecision = lastDecision,
        )
    }

    /**
     * 解析 Bot 消息 raw_content_json 中的 reply_to_sequence
     */
    private fun parseReplyTo(message: Message): List<Long> {
        val raw = message.rawContentJson ?: return emptyList()
        return runCatching {
            val node = MapperUtils.jsonMapper.readTree(raw)
            val replyTo = node.get("reply_to_sequence") ?: return emptyList()
            if (!replyTo.isArray) {
                return emptyList()
            }
            replyTo.mapNotNull { it.asLong() }
        }.getOrDefault(emptyList())
    }

    companion object {
        /** Bot 自身消息的对象类型标识（与 Adapter 的消息事件类型区分） */
        const val BOT_MESSAGE_OBJECT_TYPE = "core.bot_text"

        fun idGen(id: Long, streamType: StreamType): String {
            return idGen(streamType, id)
        }
    }
}
