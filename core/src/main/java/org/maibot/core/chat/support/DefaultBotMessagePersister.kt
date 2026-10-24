package org.maibot.core.chat.support

import org.maibot.core.thinking.ThinkingFlow
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.manager.InteractionEntityManager
import org.maibot.sdk.manager.MessageManager
import org.maibot.sdk.msg.SelfInfoRegistry
import org.maibot.sdk.storage.db.DatabaseService
import org.maibot.sdk.storage.db.dao.InteractionStream
import org.maibot.sdk.storage.db.dao.Message
import org.maibot.sdk.util.MapperUtils

/**
 * 默认实现：通过 manager 层落库，不直接在 Core 业务代码里拼 EntityManager 细节。
 */
@Component
class DefaultBotMessagePersister
@AutoInject private constructor(
    private val databaseService: DatabaseService,
    private val messageManager: MessageManager,
    private val interactionEntityManager: InteractionEntityManager,
    private val selfInfoRegistry: SelfInfoRegistry,
) : BotMessagePersister {
    override fun persist(streamId: String, text: String, replyTo: List<Long>): Message {
        return databaseService.exec { em ->
            val stream = em.find(InteractionStream::class.java, streamId)
                ?: throw IllegalStateException("交互流不存在: $streamId")
            val platform = stream.entity?.platform
                ?: throw IllegalStateException("交互流缺少平台信息: $streamId")
            val selfInfo = selfInfoRegistry.get(platform)
                ?: throw IllegalStateException("平台 $platform 尚未注册 Bot 自身信息")

            val botEntity = interactionEntityManager.getOrCreatIfAbsent(
                em,
                platform,
                selfInfo.platformUserId,
                selfInfo.nickname,
            ) ?: throw IllegalStateException("无法创建 Bot 的交互实体")

            val rawContentJson = MapperUtils.jsonMapper.writeValueAsString(
                mapOf(
                    "text" to text,
                    "reply_to_sequence" to replyTo,
                )
            )

            messageManager.saveBotMessage(
                em = em,
                streamId = streamId,
                text = text,
                sender = botEntity,
                objectType = ThinkingFlow.BOT_MESSAGE_OBJECT_TYPE,
                rawContentJson = rawContentJson,
            )
        }
    }
}
