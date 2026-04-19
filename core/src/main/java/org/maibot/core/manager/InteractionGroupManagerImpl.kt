package org.maibot.core.manager

import jakarta.persistence.EntityManager
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.manager.InteractionGroupManager
import org.maibot.sdk.storage.db.DatabaseService
import org.maibot.sdk.storage.db.dao.InteractionGroup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Component
class InteractionGroupManagerImpl : InteractionGroupManager {
    // 锁对象映射，用于防止 交互实体 的重复创建
    private val lockMap = ConcurrentHashMap<String, CompletableFuture<InteractionGroup>>()

    private fun generateKey(platform: String, platformGroupId: String): String {
        return "$platform:$platformGroupId"
    }

    override fun getOrCreatIfAbsent(
        em: EntityManager, platform: String, platformGroupId: String, groupName: String?
    ): InteractionGroup? {
        val key = generateKey(platform, platformGroupId)
        // 使用锁对象，防止重复查询和创建
        val lockObject = CompletableFuture<InteractionGroup>()
        val mappedLock = lockMap.putIfAbsent(key, lockObject)

        if (mappedLock == null) {
            // 当前线程获得锁，执行获取或创建逻辑
            try {
                // 检查实体是否存在
                val group = this.get(em, platform, platformGroupId) ?: run { // 不存在则创建新实例
                    DatabaseService.execInTransaction(em) {
                        InteractionGroup().apply {
                            this.platform = platform
                            this.platformGroupId = platformGroupId
                            this.groupName = groupName
                            em.persist(this)
                        }
                    }
                }

                // 完成锁对象，通知等待的线程
                lockObject.complete(group)
                return group
            } catch (e: Exception) {
                log.warn(
                    "An exception occurred while creating InteractionGroup for platform: {}, platformGroupId: {}",
                    platform,
                    platformGroupId,
                    e
                )
                lockObject.cancel(false)
                throw e
            } finally {
                lockMap.remove(key)
            }
        } else {
            // 其他线程等待锁完成
            try {
                val group = mappedLock.get()
                em.merge(group) // 将实体附加到当前 EntityManager 上
                return group
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            return null
        }
    }

    override fun get(em: EntityManager, platform: String, platformGroupId: String): InteractionGroup? {
        // 直接查询数据库
        try {
            return em.createQuery(
                "SELECT g FROM InteractionGroup g WHERE g.platform = :platform AND g.platformGroupId = :platformGroupId",
                InteractionGroup::class.java
            ).apply {
                setParameter("platform", platform)
                setParameter("platformGroupId", platformGroupId)
            }.resultList.firstOrNull()
        } catch (e: Exception) {
            log.warn(
                "An exception occurred while retrieving InteractionGroup for platform: {}, platformGroupId: {}",
                platform,
                platformGroupId,
                e
            )
            throw e
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(InteractionGroupManagerImpl::class.java)
    }
}
