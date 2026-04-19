package org.maibot.sdk.manager

import jakarta.persistence.EntityManager
import org.maibot.sdk.storage.model.msgevt.AbstractMessageEvent

interface MessageManager {
    /**
     * 保存消息事件到数据库
     */
    fun saveMessage(em: EntityManager, msg: AbstractMessageEvent)
}
