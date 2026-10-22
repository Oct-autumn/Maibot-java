package org.maibot.core.chat

import io.netty.channel.ChannelHandlerContext
import org.maibot.core.config.CoreConfig
import org.maibot.core.msg.OutgoingMessageService
import org.maibot.sdk.eventchannel.SimpleEventHandler
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.IOC
import org.maibot.sdk.ioc.Value
import org.maibot.sdk.msg.OutgoingMessage
import org.maibot.sdk.storage.db.DatabaseService
import org.maibot.sdk.storage.domain.StreamType
import org.maibot.sdk.storage.model.msgevt.AbstractMessageEvent
import org.maibot.sdk.task.TaskExecuteService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 聊天事件处理器
 *
 * 挂在全局事件管线的「消息持久化处理器」之后，保证事件到达时消息已经写入数据库。
 *
 * M1 阶段只处理私聊消息：
 * - `debug_echo = true` 时走回环分支，把渲染结果原样发回，用于验证 Core → Adapter 出站管线；
 * - 否则交给 [ChatService] 组装上下文、调用模型并生成回复。
 */
@Component
class ChatHandler
@AutoInject private constructor(
    @param:Value($$"${chat}") private val config: CoreConfig.Chat,
    private val chatService: ChatService,
    private val outgoingMessageService: OutgoingMessageService,
    private val databaseService: DatabaseService,
    private val ioc: IOC,
    private val taskExecuteService: TaskExecuteService
) : SimpleEventHandler<AbstractMessageEvent>() {

    /**
     * 事件入口：只做分流与过滤，耗时操作提交到虚拟线程执行，避免阻塞事件管线
     */
    override fun handleEvent(ctx: ChannelHandlerContext, evt: AbstractMessageEvent): Boolean {
        if (!config.enabled) {
            return true
        }

        val streamInfo = evt.messageMeta.streamInfo
        // M1 只处理私聊流，群聊等直接放行给后续处理器
        if (streamInfo.streamType != StreamType.PRIVATE) {
            return true
        }

        val peerInfo = streamInfo.privateInfo ?: run {
            log.warn("私聊消息缺少对端信息，已忽略 (sNo: {})", evt.serialNo.toHexString())
            return true
        }

        // 白名单为空表示不限制；非空时只响应名单内的用户
        if (config.allowedUsers.isNotEmpty() && evt.messageMeta.senderInfo.platformId !in config.allowedUsers) {
            log.debug("发送者 {} 不在聊天白名单内，已忽略", evt.messageMeta.senderInfo.platformId)
            return true
        }

        val platform = evt.messageMeta.platform
        val peerPlatformId = peerInfo.platformId

        if (config.debugEcho) {
            // 回环调试模式：只验证出站管线，不走模型
            taskExecuteService.submit(true) {
                this.echoPrivateMessage(evt, platform, peerPlatformId)
                return@submit Unit
            }
        } else {
            // 正常聊天：按会话串行处理，避免连发消息时上下文错乱
            chatService.submit("$platform:$peerPlatformId") {
                chatService.reply(evt)
            }
        }

        return true
    }

    /**
     * 回环调试：把渲染好的消息内容原样发回
     */
    private fun echoPrivateMessage(evt: AbstractMessageEvent, platform: String, peerPlatformId: String) {
        // 复用事件自身的渲染结果（消息持久化时已渲染过一次，这里为幂等的缓存读取）
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
        private val log: Logger = LoggerFactory.getLogger(ChatHandler::class.java)
    }
}
