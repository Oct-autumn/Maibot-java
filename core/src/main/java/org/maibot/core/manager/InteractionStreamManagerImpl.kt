package org.maibot.core.manager

import jakarta.persistence.EntityManager
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.manager.InteractionStreamManager
import org.maibot.sdk.storage.db.dao.InteractionEntity
import org.maibot.sdk.storage.db.dao.InteractionGroup
import org.maibot.sdk.storage.db.dao.InteractionStream
import org.maibot.sdk.storage.db.dao.InteractionStream.Companion.idGen
import org.maibot.sdk.storage.domain.StreamType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Component
class InteractionStreamManagerImpl : InteractionStreamManager {
    private val lockMap = ConcurrentHashMap<String, CompletableFuture<InteractionStream>>()

    private fun generateKey(type: StreamType, entityId: Long?, groupId: Long?): String {
        return ("${type.name}:${entityId ?: ""}:${groupId ?: ""}")
    }

    override fun getOrCreateIfAbsent(
        em: EntityManager, type: StreamType, interactionEntity: InteractionEntity?, interactionGroup: InteractionGroup?
    ): InteractionStream? {
        val key = when (type) {
            StreamType.PRIVATE -> {
                require(interactionEntity != null) { "InteractionEntity cannot be null for PRIVATE stream type." }
                generateKey(
                    type, interactionEntity.id, null
                )
            }

            StreamType.GROUP -> {
                require(interactionGroup != null) { "InteractionGroup cannot be null for GROUP stream types." }
                generateKey(
                    type, null, interactionGroup.id
                )
            }

            else -> throw IllegalArgumentException("Unsupported StreamType: $type")
        }

        // 使用锁对象，防止重复查询和创建
        val lockObject = CompletableFuture<InteractionStream>()
        val mappedLock = lockMap.putIfAbsent(key, lockObject)

        if (mappedLock == null) {
            // 当前线程获得锁，执行获取或创建逻辑
            try {
                val stream: InteractionStream
                when (type) {
                    StreamType.PRIVATE -> {
                        // 检查实例是否存在
                        val query = em.createQuery(
                            "SELECT s FROM InteractionStream s WHERE s.type = :type AND s.entity.id = :entityId",
                            InteractionStream::class.java
                        ).apply {
                            setParameter("type", StreamType.PRIVATE)
                            setParameter("entityId", interactionEntity!!.id)
                        }

                        stream = query.resultList.firstOrNull() ?: InteractionStream().apply { // 不存在则创建新实例
                            this.id = idGen(StreamType.PRIVATE, interactionEntity!!.id!!)
                            this.type = StreamType.PRIVATE
                            this.entity = interactionEntity
                            em.persist(this)
                        }
                    }

                    StreamType.GROUP -> {
                        // 检查实例是否存在
                        val query = em.createQuery(
                            "SELECT s FROM InteractionStream s WHERE s.type = :type AND s.group.id = :groupId",
                            InteractionStream::class.java
                        ).apply {
                            setParameter("type", StreamType.GROUP)
                            setParameter("groupId", interactionGroup!!.id)
                        }

                        stream = query.resultList.firstOrNull() ?: InteractionStream().apply { // 不存在则创建新实例
                            this.id = idGen(StreamType.GROUP, interactionGroup!!.id!!)
                            this.type = StreamType.GROUP
                            this.group = interactionGroup
                            em.persist(this)
                        }
                    }
                }
                lockObject.complete(stream)
                return stream
            } catch (e: Exception) {
                log.warn("An exception occurred while creating InteractionStream for key: {}", key, e)
                return null
            } finally {
                lockMap.remove(key)
            }
        } else {
            // 其他线程等待锁完成
            try {
                val stream = mappedLock.get()
                em.merge(stream) // 将实例附加到当前 EntityManager 上
                return stream
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            return null
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(InteractionStreamManagerImpl::class.java)
    }
}
