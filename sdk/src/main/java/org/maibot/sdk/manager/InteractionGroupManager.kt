package org.maibot.sdk.manager

import jakarta.persistence.EntityManager
import org.maibot.sdk.storage.db.dao.InteractionGroup

interface InteractionGroupManager {
    fun getOrCreatIfAbsent(
        em: EntityManager,
        platform: String,
        platformGroupId: String,
        groupName: String?
    ): InteractionGroup?

    fun get(em: EntityManager, platform: String, platformGroupId: String): InteractionGroup?
}
