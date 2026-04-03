package org.maibot.core.persistence

import io.netty.channel.ChannelHandlerContext
import org.maibot.core.manager.InteractionEntityManagerImpl
import org.maibot.core.manager.InteractionGroupManagerImpl
import org.maibot.core.manager.InteractionStreamManagerImpl
import org.maibot.sdk.eventchannel.SimpleEventHandler
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.storage.model.msgevt.AbstractMessageEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

@Component
class MsgEvtPersistenceHandler
@AutoInject private constructor(
    private val databaseService: DatabaseServiceImpl,
    private val interactionEntityManager: InteractionEntityManagerImpl,
    private val interactionGroupManager: InteractionGroupManagerImpl,
    private val interactionStreamManager: InteractionStreamManagerImpl
) : SimpleEventHandler<AbstractMessageEvent>() {

    override fun handleEvent(ctx: ChannelHandlerContext, evt: AbstractMessageEvent): Boolean {
        this.databaseService.execAsync { em ->
            MDC.put("sNo", evt.serialNo.toHexString())
            em.persist(
                evt.toDatabaseObject(
                    em, interactionEntityManager, interactionGroupManager, interactionStreamManager
                )
            )
        }.exceptionally { e ->
            log.error("持久化消息事件时发生错误：{}", evt, e)
        }
        return true
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MsgEvtPersistenceHandler::class.java)
    }
}
