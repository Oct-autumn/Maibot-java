package org.maibot.core.thinking

import org.maibot.sdk.storage.db.dao.Message

/**
 * 观察窗口条目
 *
 * 条目本身不可变；标记「已回复」时由 [ThinkingFlow] 替换为新条目，
 * 这样快照可以被观察线程安全读取。
 *
 * @param message  已落库的消息
 * @param origin   来源
 * @param replied  外部消息是否已经被回复
 * @param replyTo  仅 SELF 条目使用：这条回复覆盖的消息 sequence 列表
 */
data class ObservationEntry(
    val message: Message,
    val origin: Origin,
    val replied: Boolean = origin == Origin.SELF,
    val replyTo: List<Long> = emptyList(),
) {
    /**
     * 观察窗口条目的来源
     */
    enum class Origin {
        /** 来自交互流外部的消息（用户发送） */
        EXTERNAL,

        /** Bot 自己发出并落库的消息 */
        SELF,
    }

    /** 消息序列号（落库后一定存在） */
    val sequence: Long
        get() = requireNotNull(message.sequence) { "观察窗口中的消息缺少 sequence" }

    val isExternal: Boolean
        get() = origin == Origin.EXTERNAL
}
