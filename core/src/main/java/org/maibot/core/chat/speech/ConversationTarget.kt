package org.maibot.core.chat.speech

/**
 * 一次发言的目标会话
 *
 * 从消息所属的交互流推出，模型参数里不包含目标，避免把消息发到别的会话。
 */
data class ConversationTarget(
    val streamId: String,
    val platform: String,
    val platformTargetId: String,
    val isGroup: Boolean,
)
