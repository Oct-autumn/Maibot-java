package org.maibot.sdk.manager

import jakarta.persistence.EntityManager
import org.maibot.sdk.storage.db.dao.GroupMember
import org.maibot.sdk.storage.db.dao.InteractionEntity
import org.maibot.sdk.storage.db.dao.InteractionGroup

interface GroupMemberManager {
    /**
     * 获取或创建组成员对象
     * 
     * 
     * 注意：该方法需要在实体管理器的上下文中调用。
     * 
     * @param em       实体管理器
     * @param group    交互组对象
     * @param entity   交互实体对象
     * @param cardName 成员卡片名称
     * @return 组成员对象，若创建失败则返回 null
     */
    fun getOrCreatIfAbsent(
        em: EntityManager,
        group: InteractionGroup,
        entity: InteractionEntity,
        cardName: String?
    ): GroupMember?

    /**
     * 获取组成员对象
     * 
     * 
     * 注意：该方法需要在实体管理器的上下文中调用。
     * 
     * @param em     实体管理器
     * @param group  交互组对象
     * @param entity 交互实体对象
     * @return 组成员对象，若不存在则返回 null
     */
    fun get(em: EntityManager, group: InteractionGroup, entity: InteractionEntity): GroupMember?
}
