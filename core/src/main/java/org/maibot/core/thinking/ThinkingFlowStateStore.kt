package org.maibot.core.thinking

import org.maibot.core.persistence.DatabaseServiceImpl
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.storage.db.DatabaseService
import org.maibot.sdk.storage.db.dao.ThinkingFlowState
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 思维流状态存储
 *
 * 负责把状态机元数据落到 `thinking_flow_state`，供启动时恢复。
 * 只做持久化，不参与状态机决策。
 */
@Component
class ThinkingFlowStateStore
@AutoInject private constructor(
    private val databaseService: DatabaseServiceImpl,
) : FlowStatePersister {
    /**
     * 持久化一个思维流的当前状态
     */
    override fun persist(flow: ThinkingFlow) {
        val streamId = flow.id ?: return
        save(streamId, flow.persistedState())
    }

    /**
     * 加载全部流状态
     */
    fun loadAll(): Map<String, PersistedFlowState> {
        return databaseService.exec { em ->
            em.createQuery("SELECT s FROM ThinkingFlowState s", ThinkingFlowState::class.java)
                .resultList
                .mapNotNull { entity ->
                    val streamId = entity.streamId ?: return@mapNotNull null
                    val state = parseState(streamId, entity.state) ?: return@mapNotNull null
                    streamId to PersistedFlowState(
                        state = state,
                        lastActivityAt = entity.lastActivityAt,
                        lastObservedSequence = entity.lastObservedSequence,
                        lastDecision = entity.lastDecision,
                    )
                }
                .toMap()
        }
    }

    /**
     * 保存单个流状态；不存在时插入，存在时更新。
     */
    fun save(streamId: String, state: PersistedFlowState) {
        databaseService.exec { em ->
            DatabaseService.execInTransaction(em) {
                val now = System.currentTimeMillis()
                val entity = em.find(ThinkingFlowState::class.java, streamId)
                if (entity == null) {
                    em.persist(
                        ThinkingFlowState().apply {
                            this.streamId = streamId
                            applyState(this, state, now)
                        }
                    )
                } else {
                    applyState(entity, state, now)
                    em.merge(entity)
                }
            }
        }
    }

    /**
     * 批量保存，关闭时使用。
     */
    fun saveAll(states: Map<String, PersistedFlowState>) {
        if (states.isEmpty()) {
            return
        }

        databaseService.exec { em ->
            DatabaseService.execInTransaction(em) {
                val now = System.currentTimeMillis()
                for ((streamId, state) in states) {
                    val entity = em.find(ThinkingFlowState::class.java, streamId)
                    if (entity == null) {
                        em.persist(
                            ThinkingFlowState().apply {
                                this.streamId = streamId
                                applyState(this, state, now)
                            }
                        )
                    } else {
                        applyState(entity, state, now)
                        em.merge(entity)
                    }
                }
            }
        }
    }

    private fun applyState(
        entity: ThinkingFlowState,
        state: PersistedFlowState,
        now: Long,
    ) {
        entity.state = state.state.name
        entity.lastActivityAt = state.lastActivityAt
        entity.lastObservedSequence = state.lastObservedSequence
        entity.lastDecision = state.lastDecision
        entity.updatedAt = now
    }

    private fun parseState(streamId: String, value: String?): FlowState? {
        if (value == null) {
            log.warn("思维流 {} 的状态为空，忽略该状态行", streamId)
            return null
        }
        return runCatching { FlowState.valueOf(value) }.getOrElse {
            log.warn("思维流 {} 的状态值非法（{}），按 INACTIVE 处理", streamId, value)
            null
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ThinkingFlowStateStore::class.java)
    }
}
