package org.maibot.core.chat.speech

import org.maibot.core.thinking.ObservationSnapshot

/**
 * 发言文本生成接口
 *
 * 只负责「根据观察窗口、目标消息和发言意图生成要说的内容」；
 * 发送与落库由调用方处理。
 */
interface SpeechGenerator {
    /**
     * @param snapshot        观察窗口快照
     * @param targetSequences 本次要回复的消息 sequence；空表示主动发言
     * @param intent          发言意图（想说什么的大体方向），可空
     * @return 要发送的文本；无法生成时返回 null
     */
    fun generate(
        snapshot: ObservationSnapshot,
        targetSequences: List<Long>,
        intent: String?,
    ): String?
}
