package org.maibot.core.chat.speech

/**
 * 人设来源
 *
 * 抽成接口是为了让 prompt 构建可以脱离文件系统单测；
 * 默认实现见 [FilePersonaProvider]。
 */
interface PersonaProvider {
    fun load(): String
}
