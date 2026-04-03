package org.maibot.sdk.manager

import jakarta.persistence.EntityManager
import org.maibot.sdk.storage.db.dao.InteractionEntity

interface InteractionEntityManager {
    /**
     * 创建交互实体（若不存在）
     * 
     * 
     * 注意：该方法需要在实体管理器的上下文中调用。
     * 
     * @param em             实体管理器
     * @param platform       平台标识
     * @param platformUserId 平台用户ID
     * @param nickname       昵称
     * @return 交互实体对象
     */
    fun getOrCreatIfAbsent(
        em: EntityManager,
        platform: String,
        platformUserId: String,
        nickname: String?
    ): InteractionEntity?

    /**
     * 获取交互实体
     * 
     * 
     * 注意：该方法需要在实体管理器的上下文中调用。
     * 
     * @param em             实体管理器
     * @param platform       平台标识
     * @param platformUserId 平台用户ID
     * @return 交互实体对象，若不存在则返回 null
     */
    fun get(em: EntityManager, platform: String, platformUserId: String): InteractionEntity?
}
