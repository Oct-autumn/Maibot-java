package org.maibot.core.event

import io.netty.channel.ChannelHandlerContext
import org.maibot.sdk.eventchannel.SimpleEventHandler
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.manager.MessageManager
import org.maibot.sdk.storage.db.DatabaseService
import org.maibot.sdk.storage.model.msgevt.AbstractMessageEvent
import org.slf4j.LoggerFactory

@Component
class MsgPersistentHandler
@AutoInject
private constructor(
    private val databaseService: DatabaseService,
    private val messageManager: MessageManager,
) : SimpleEventHandler<AbstractMessageEvent>() {
    override fun handleEvent(
        ctx: ChannelHandlerContext,
        evt: AbstractMessageEvent
    ): Boolean {
        log.trace("收到消息事件 (sNo: {})，准备持久化", evt.serialNo)

        try {
            databaseService.exec { em ->
                messageManager.saveMessage(em, evt)
            }
        } catch (e: Exception) {
            log.error("消息事件 (sNo: {}) 持久化失败", evt.serialNo, e)
            return false
        }

        log.trace("消息事件 (sNo: {}) 持久化完成", evt.serialNo)

        return true
    }

    companion object {
        private val log = LoggerFactory.getLogger(MsgPersistentHandler::class.java)
    }
}
