package org.maibot.core.manager

import jakarta.persistence.EntityManager
import org.maibot.core.ioc.IOCImpl
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.manager.InteractionEntityManager
import org.maibot.sdk.manager.InteractionGroupManager
import org.maibot.sdk.manager.InteractionStreamManager
import org.maibot.sdk.manager.MessageManager
import org.maibot.sdk.storage.db.DatabaseService
import org.maibot.sdk.storage.model.msgevt.AbstractMessageEvent
import org.slf4j.LoggerFactory

@Component
class MessageManagerImpl
@AutoInject
private constructor(
    private val interactionEntityManager: InteractionEntityManager,
    private val interactionGroupManager: InteractionGroupManager,
    private val interactionStreamManager: InteractionStreamManager,
    private val iocImpl: IOCImpl
) : MessageManager {
    override fun saveMessage(em: EntityManager, msg: AbstractMessageEvent) {
        val msgEntity =
            msg.toDatabaseObject(
                em, interactionEntityManager, interactionGroupManager, interactionStreamManager,
                iocImpl
            )
        try {
            DatabaseService.execInTransaction(em) {
                em.persist(msgEntity)
            }
        } catch (e: Exception) {
            log.error("保存消息事件到数据库时发生异常", e)
            throw e
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MessageManagerImpl::class.java)
    }
}