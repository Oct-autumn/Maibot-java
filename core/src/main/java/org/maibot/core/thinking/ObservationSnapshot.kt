package org.maibot.core.thinking

/**
 * 观察窗口的不可变快照
 *
 * @param streamId        交互流 ID
 * @param version         快照对应的窗口版本（每条外部消息 +1）
 * @param entries         从旧到新的窗口条目
 * @param triggerSequence 本次观察要处理的消息序列号（可空）
 */
data class ObservationSnapshot(
    val streamId: String,
    val version: Long,
    val entries: List<ObservationEntry>,
    val triggerSequence: Long? = null,
) {
    /** 最早一条尚未回复的外部消息 */
    val firstUnrepliedExternal: ObservationEntry?
        get() = entries.firstOrNull { it.isExternal && !it.replied }

    /** 所有尚未回复的外部消息 */
    val unrepliedExternals: List<ObservationEntry>
        get() = entries.filter { it.isExternal && !it.replied }
}
