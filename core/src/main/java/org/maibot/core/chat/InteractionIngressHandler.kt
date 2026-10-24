package org.maibot.core.chat

import io.netty.channel.ChannelHandlerContext
import org.maibot.core.config.CoreConfig
import org.maibot.core.msg.OutgoingMessageService
import org.maibot.core.thinking.ThinkingFlowManager
import org.maibot.sdk.eventchannel.SimpleEventHandler
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.IOC
import org.maibot.sdk.ioc.Value
import org.maibot.sdk.manager.MessageManager
import org.maibot.sdk.msg.OutgoingMessage
import org.maibot.sdk.storage.db.DatabaseService
import org.maibot.sdk.storage.model.msgevt.AbstractMessageEvent
import org.maibot.sdk.task.TaskExecuteService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 交互流入站处理器
 *
 * 挂在「消息持久化处理器」之后：只做过滤、定位消息所属的交互流，
 * 然后把消息投递给 [ThinkingFlowManager]。真正的回复时机/内容由观察线程决定，
 * 入站处理器不再直接调用模型。
 */
@Component
class InteractionIngressHandler
@AutoInject private constructor(
    private val accessPolicy: ChatAccessPolicy,
    private val thinkingFlowManager: ThinkingFlowManager,
    private val messageManager: MessageManager,
    private val databaseService: DatabaseService,
    private val outgoingMessageService: OutgoingMessageService,
    private val ioc: IOC,
    private val taskExecuteService: TaskExecuteService,
    @param:Value($$"${chat}") private val config: CoreConfig.Chat,
) : SimpleEventHandler<AbstractMessageEvent>() {

    override fun handleEvent(ctx: ChannelHandlerContext, evt: AbstractMessageEvent): Boolean {
        if (!accessPolicy.enabled) {
            return true
        }

        if (!accessPolicy.isSupportedStream(evt)) {
            return true
        }

        if (!accessPolicy.isAllowedSender(evt)) {
            log.debug("发送者 {} 不在聊天白名单内，已忽略", evt.messageMeta.senderInfo.platformId)
            return true
        }

        // 防止 Adapter 回显的 Bot 自身消息再次触发观察（自激）
        if (accessPolicy.isSelfMessage(evt)) {
            log.debug("忽略 Bot 自身消息回显 (sNo: {})", evt.serialNo.toHexString())
            return true
        }

        val peerInfo = evt.messageMeta.streamInfo.privateInfo ?: run {
            log.warn("私聊消息缺少对端信息，已忽略 (sNo: {})", evt.serialNo.toHexString())
            return true
        }

        val platform = evt.messageMeta.platform

        if (config.debugEcho) {
            taskExecuteService.submit(true) {
                this.echoPrivateMessage(evt, platform, peerInfo.platformId)
                return@submit Unit
            }
            return true
        }

        try {
            val message = databaseService.exec { em ->
                messageManager.findMessageBySequence(em, evt.serialNo.sNo)
            } ?: run {
                log.warn("未找到已持久化的消息，跳过投递 (sNo: {})", evt.serialNo.toHexString())
                return true
            }

            val streamId = message.stream?.id ?: run {
                log.warn("消息缺少交互流，跳过投递 (sNo: {})", evt.serialNo.toHexString())
                return true
            }

            thinkingFlowManager.onExternalMessage(streamId, message)
        } catch (e: Exception) {
            log.error("投递消息到观察流失败 (sNo: {})", evt.serialNo.toHexString(), e)
        }

        return true
    }

    /**
     * 回环调试：把渲染好的消息内容原样发回
     */
    private fun echoPrivateMessage(evt: AbstractMessageEvent, platform: String, peerPlatformId: String) {
        val promptStr = databaseService.exec { em -> evt.toPromptString(em, ioc) } ?: run {
            log.warn("消息内容渲染失败，跳过处理 (sNo: {})", evt.serialNo.toHexString())
            return
        }

        if (promptStr.isBlank()) {
            log.debug("消息内容为空，跳过处理 (sNo: {})", evt.serialNo.toHexString())
            return
        }

        val sent = outgoingMessageService.send(OutgoingMessage.text(platform, peerPlatformId, "echo: $promptStr"))
        if (sent) {
            log.info("回环消息已提交发送，目标: {}", peerPlatformId)
        } else {
            log.warn("回环消息发送失败，目标: {}", peerPlatformId)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(InteractionIngressHandler::class.java)
    }
}
