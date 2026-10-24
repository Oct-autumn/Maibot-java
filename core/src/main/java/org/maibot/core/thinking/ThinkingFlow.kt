package org.maibot.core.thinking

import org.maibot.core.thinking.ThinkingFlow.FlowState.*
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
 * 思维流是对交互流的思考与决策单元：维护观察窗口、状态和版本号，
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
    var version: Long = 0
        private set

    /** 观察窗口容量 */
    val capacity: Int
        get() = observationWindowSize

    /** 交互流状态 */
    @Volatile
    var state = SLEEPING
        set(value) {
            if (field == value) {
                return
            }
            field = value
            lock.lock()
            try {
                changeCondition.signalAll()
            } finally {
                lock.unlock()
            }
        }

    /**
     * 追加一条外部消息，并唤醒观察线程
     */
    fun addExternalMessage(message: Message): ObservationEntry {
        lock.lock()
        try {
            check(!stopped) { "思维流已停止: $id" }
            val entry = ObservationEntry(message, ObservationEntry.Origin.EXTERNAL, replied = false)
            appendLocked(entry)
            version++
            state = FOCUSED
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
        } finally {
            lock.unlock()
        }
    }

    /**
     * 获取完整窗口快照
     */
    fun snapshot(): ObservationSnapshot {
        lock.lock()
        try {
            return buildSnapshotLocked(null)
        } finally {
            lock.unlock()
        }
    }

    /**
     * 如果存在未回复的外部消息，返回以最早一条为触发消息的快照；否则返回 null
     */
    fun pendingSnapshot(): ObservationSnapshot? {
        lock.lock()
        try {
            val trigger = firstUnrepliedLocked() ?: return null
            return buildSnapshotLocked(trigger.sequence)
        } finally {
            lock.unlock()
        }
    }

    /**
     * 等待一条未回复的外部消息；停止时返回 null
     *
     * W/O 阶段使用这个阻塞等待；M3b 的 ACTIVE 定时观察可以在外层加超时。
     */
    fun awaitPending(): ObservationSnapshot? {
        lock.lock()
        try {
            while (true) {
                if (stopped) {
                    return null
                }

                val trigger = firstUnrepliedLocked()
                if (trigger != null) {
                    return buildSnapshotLocked(trigger.sequence)
                }

                changeCondition.await()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        } finally {
            lock.unlock()
        }
    }

    /**
     * 请求停止观察线程
     */
    fun stopObserving() {
        lock.lock()
        try {
            stopped = true
            state = SLEEPING
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

    private fun firstUnrepliedLocked(): ObservationEntry? {
        return observationWindow.firstOrNull { it.isExternal && !it.replied }
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

    private fun buildSnapshotLocked(triggerSequence: Long?): ObservationSnapshot {
        return ObservationSnapshot(
            streamId = requireNotNull(id) { "思维流 ID 不能为空" },
            version = version,
            entries = observationWindow.toList(),
            triggerSequence = triggerSequence,
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

    /**
     * 交互流状态
     *
     * SLEEPING：休眠；ACTIVE：定期观察；FOCUSED：每条新消息都观察。
     */
    enum class FlowState(private val code: Int) {
        SLEEPING(0),
        ACTIVE(1),
        FOCUSED(2);

        fun isAtLeast(other: FlowState): Boolean {
            return this.code >= other.code
        }
    }

    companion object {
        /** Bot 自身消息的对象类型标识（与 Adapter 的消息事件类型区分） */
        const val BOT_MESSAGE_OBJECT_TYPE = "core.bot_text"

        fun idGen(id: Long, streamType: StreamType): String {
            return idGen(streamType, id)
        }

    }
}
