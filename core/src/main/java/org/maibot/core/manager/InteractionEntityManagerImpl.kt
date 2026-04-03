package org.maibot.core.manager

import jakarta.persistence.EntityManager
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.manager.InteractionEntityManager
import org.maibot.sdk.storage.db.dao.InteractionEntity
import org.maibot.sdk.storage.db.dao.Person
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Component
class InteractionEntityManagerImpl : InteractionEntityManager {
    // 锁对象映射，用于防止 交互实体 的重复创建
    private val lockMap = ConcurrentHashMap<String, CompletableFuture<InteractionEntity>>()

    private fun generateKey(platform: String, platformUserId: String): String {
        return "$platform:$platformUserId"
    }

    override fun getOrCreatIfAbsent(
        em: EntityManager,
        platform: String,
        platformUserId: String,
        nickname: String?
    ): InteractionEntity? {
        val key = generateKey(platform, platformUserId)
        // 使用锁对象，防止重复查询和创建
        val lockObject = CompletableFuture<InteractionEntity>()
        val mappedLock = lockMap.putIfAbsent(key, lockObject)

        if (mappedLock == null) {
            // 当前线程获得锁，执行获取或创建逻辑
            try {
                // 检查实体是否存在，防止重复创建
                val entity = this.get(em, platform, platformUserId) ?: run { // 不存在则创建新实例
                    // 先创建Person实体
                    val person = Person().apply {
                        name = nickname
                        em.persist(this)
                    }
                    // 再创建InteractionEntity实体
                    InteractionEntity().apply {
                        this.platform = platform
                        this.platformUserId = platformUserId
                        this.person = person
                        this.nickname = nickname
                        em.persist(this)
                    }
                }

                // 完成锁对象，通知等待的线程
                lockObject.complete(entity)
                return entity
            } catch (e: Exception) {
                log.warn(
                    "An exception occurred while creating InteractionEntity for platform: {}, platformUserId: {}",
                    platform,
                    platformUserId,
                    e
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
                val entity = mappedLock.get()
                em.merge(entity) // 将entity注册到本地EntityManager上下文中
                return entity
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            return null
        }
    }

    override fun get(em: EntityManager, platform: String, platformUserId: String): InteractionEntity? {
        // 直接查询数据库
        try {
            return em.createQuery(
                "SELECT e FROM InteractionEntity e WHERE e.platform = :platform AND e.platformUserId = :platformUserId",
                InteractionEntity::class.java
            ).apply {
                setParameter("platform", platform)
                setParameter("platformUserId", platformUserId)
            }.resultList.firstOrNull()
        } catch (e: Exception) {
            log.warn(
                "An exception occurred while retrieving InteractionEntity for platform: {}, platformUserId: {}",
                platform,
                platformUserId,
                e
            )
            return null
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(InteractionEntityManagerImpl::class.java)
    }
}
