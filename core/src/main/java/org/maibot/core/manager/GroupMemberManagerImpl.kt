package org.maibot.core.manager

import jakarta.persistence.EntityManager
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.manager.GroupMemberManager
import org.maibot.sdk.storage.db.dao.GroupMember
import org.maibot.sdk.storage.db.dao.GroupMember.GroupMemberId
import org.maibot.sdk.storage.db.dao.InteractionEntity
import org.maibot.sdk.storage.db.dao.InteractionGroup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Component
class GroupMemberManagerImpl : GroupMemberManager {
    // 锁对象映射，用于防止 交互实体 的重复创建
    private val lockMap = ConcurrentHashMap<String, CompletableFuture<GroupMember>>()

    private fun generateKey(groupId: Long, userId: Long): String {
        return "$groupId:$userId"
    }

    override fun getOrCreatIfAbsent(
        em: EntityManager, group: InteractionGroup, entity: InteractionEntity, cardName: String?
    ): GroupMember? {
        val key = generateKey(group.id!!, entity.id!!)
        // 使用锁对象，防止重复查询和创建
        val lockObject = CompletableFuture<GroupMember>()
        val mappedLock = lockMap.putIfAbsent(key, lockObject)

        if (mappedLock == null) {
            // 当前线程获得锁，执行获取或创建逻辑
            try {
                // 检查实体是否存在，防止重复创建
                val groupMember = this.get(em, group, entity) ?: GroupMember().apply { // 不存在则创建新实例
                    this.group = group
                    this.entity = entity
                    this.cardName = cardName
                    em.persist(this)
                }

                // 完成锁对象，通知等待的线程
                lockObject.complete(groupMember)
                return groupMember
            } catch (e: Exception) {
                log.warn(
                    "获取或创建 GroupMember (groupId:{}, entityId:{}) 时发生异常", group.id, entity.id, e
                )
                // 出现异常，取消锁对象，通知等待的线程
                lockObject.cancel(false)
                return null
            } finally {
                // 移除锁对象
                lockMap.remove(key)
            }
        } else {
            // 其他线程等待结果
            try {
                val groupMember = mappedLock.get()
                em.merge(groupMember) // 将entity注册到本地EntityManager上下文中
                return groupMember
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            return null
        }
    }

    override fun get(em: EntityManager, group: InteractionGroup, entity: InteractionEntity): GroupMember? {
        // 直接查询数据库
        try {
            return em.createQuery(
                "SELECT e FROM GroupMember e WHERE e.id = :groupMemberId", GroupMember::class.java
            ).apply {
                setParameter("groupMemberId", GroupMemberId(group.id!!, entity.id!!))
            }.resultList.firstOrNull()
        } catch (e: Exception) {
            log.warn(
                "获取 GroupMember (groupId:{}, entityId:{}) 时发生异常", group.id, entity.id, e
            )
            return null
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GroupMemberManagerImpl::class.java)
    }
}
