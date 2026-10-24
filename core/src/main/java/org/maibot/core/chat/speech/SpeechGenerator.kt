package org.maibot.core.chat.speech

import org.maibot.core.thinking.ObservationSnapshot

/**
 * 发言文本生成接口
 *
 * 只负责「根据观察窗口生成要说的内容」，发送与落库由调用方处理。
 */
interface SpeechGenerator {
    /**
     * @return 要发送的文本；无法生成时返回 null
     */
    fun generate(snapshot: ObservationSnapshot, triggerSequence: Long): String?
}
