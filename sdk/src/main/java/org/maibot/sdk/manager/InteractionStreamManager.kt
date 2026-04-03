package org.maibot.sdk.manager

import jakarta.persistence.EntityManager
import org.maibot.sdk.storage.db.dao.InteractionEntity
import org.maibot.sdk.storage.db.dao.InteractionGroup
import org.maibot.sdk.storage.db.dao.InteractionStream
import org.maibot.sdk.storage.domain.StreamType

interface InteractionStreamManager {
    fun getOrCreateIfAbsent(
        em: EntityManager,
        type: StreamType,
        interactionEntity: InteractionEntity?,
        interactionGroup: InteractionGroup?
    ): InteractionStream?
}
