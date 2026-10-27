package org.maibot.core.thinking

/**
 * 观察窗口的不可变快照
 *
 * @param streamId              交互流 ID
 * @param version               快照对应的窗口版本（每条外部消息 +1）
 * @param state                 快照生成时的观察状态
 * @param entries               从旧到新的窗口条目
 * @param newSequences          自上次成功观察以来进入窗口的外部消息 sequence
 * @param lastObservedSequence  上次成功观察到的最大 sequence
 * @param lastActivityAt        最后一次活动时间（外部消息或 Bot 发言）
 * @param trigger               本次观察的触发原因
 * @param triggerSequence       本次观察关联的最新新消息 sequence
 * @param lastDecision          上次观察的决策摘要
 */
data class ObservationSnapshot(
    val streamId: String,
    val version: Long,
    val state: FlowState,
    val entries: List<ObservationEntry>,
    val newSequences: Set<Long> = emptySet(),
    val lastObservedSequence: Long = 0,
    val lastActivityAt: Long = 0,
    val trigger: ObservationTrigger = ObservationTrigger.PERIODIC,
    val triggerSequence: Long? = null,
    val lastDecision: String? = null,
) {
    /** 最早一条尚未回复的外部消息 */
    val firstUnrepliedExternal: ObservationEntry?
        get() = entries.firstOrNull { it.isExternal && !it.replied }

    /** 所有尚未回复的外部消息 */
    val unrepliedExternals: List<ObservationEntry>
        get() = entries.filter { it.isExternal && !it.replied }

    /** 本次新进入窗口的外部消息 */
    val newExternalEntries: List<ObservationEntry>
        get() = entries.filter { it.isExternal && it.sequence in newSequences }

    /** 最新一条新消息的 sequence */
    val latestNewSequence: Long?
        get() = newSequences.maxOrNull()
}
