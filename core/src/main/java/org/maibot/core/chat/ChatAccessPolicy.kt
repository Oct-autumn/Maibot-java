package org.maibot.core.chat

import org.maibot.core.config.CoreConfig
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.Value
import org.maibot.sdk.msg.SelfInfoRegistry
import org.maibot.sdk.storage.domain.StreamType
import org.maibot.sdk.storage.model.msgevt.AbstractMessageEvent

/**
 * 聊天访问策略
 *
 * 把「是否处理这条消息」从事件处理器里抽出来，便于单测和后续群聊扩展。
 */
@Component
class ChatAccessPolicy
@AutoInject private constructor(
    @param:Value($$"${chat}") private val config: CoreConfig.Chat,
    private val selfInfoRegistry: SelfInfoRegistry,
) {
    val enabled: Boolean
        get() = config.enabled

    /**
     * W/O 阶段只处理私聊纯文本
     */
    fun isSupportedStream(evt: AbstractMessageEvent): Boolean {
        return evt.messageMeta.streamInfo.streamType == StreamType.PRIVATE
    }

    /**
     * 白名单为空表示不限制
     */
    fun isAllowedSender(evt: AbstractMessageEvent): Boolean {
        return config.allowedUsers.isEmpty() ||
                evt.messageMeta.senderInfo.platformId in config.allowedUsers
    }

    /**
     * 平台回显的 Bot 自身消息不能触发观察，否则会自激
     */
    fun isSelfMessage(evt: AbstractMessageEvent): Boolean {
        val platform = evt.messageMeta.platform
        val selfInfo = selfInfoRegistry.get(platform) ?: return false
        return evt.messageMeta.senderInfo.platformId == selfInfo.platformUserId
    }
}
