package org.maibot.core.thinking

import org.maibot.core.ioc.Instance
import org.maibot.core.persistence.DatabaseServiceImpl
import org.maibot.core.thinking.observer.FlowObserver
import org.maibot.core.thinking.observer.ObservationStrategy
import org.maibot.core.util.TaskExecuteServiceImpl
import org.maibot.sdk.exceptions.DbOperationException
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.DestroyableComponent
import org.maibot.sdk.ioc.Value
import org.maibot.sdk.manager.MessageManager
import org.maibot.sdk.manager.ThinkingFlowStateStore
import org.maibot.sdk.storage.db.DatabaseService
import org.maibot.sdk.storage.db.dao.InteractionStream
import org.maibot.sdk.storage.db.dao.Message
import org.maibot.sdk.storage.db.dao.ThinkingFlowState
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 交互流管理器
 *
 * 我们定义对于某个 Adapter 的某个 Group 为一个交互流；私聊视为成员数为 2 的流。
 * 每个交互流有一个 [ThinkingFlow] 和一个观察线程：
 * - 入站消息由 [onExternalMessage] 投递进窗口；
 * - Bot 自己的消息由 [onBotMessage] 追加进窗口，但不会触发观察。
 *
 * 观察循环的时序/决策分别在 FlowObserver 与 ObservationStrategy 中，
 * 本类只负责生命周期（创建、恢复、启动、停止）。
 */
@Component
class ThinkingFlowManager
@AutoInject private constructor(
    private val databaseService: DatabaseServiceImpl,
    private val taskExecutorService: TaskExecuteServiceImpl,
    private val messageManager: MessageManager,
    private val flowRegistry: ThinkingFlowRegistry,
    private val observationStrategy: ObservationStrategy,
    private val stateStore: ThinkingFlowStateStore,
    @param:Value($$"${chat.history_limit}") private val historyLimit: Int,
) : DestroyableComponent, FlowStatePersister {

    /**
     * 初始化思维流管理器：从数据库恢复已有的交互流和观察状态，并启动观察线程
     */
    fun initialize() {
        val persistedStates = try {
            loadAllStates()
        } catch (e: Exception) {
            log.warn("从数据库加载思维流状态失败，全部按未持久化处理", e)
            emptyMap()
        }
        restoreFromDb(persistedStates)
    }

    /**
     * 从数据库恢复交互流
     */
    private fun restoreFromDb(persistedStates: Map<String, PersistedFlowState>) {
        try {
            val streamIds = databaseService.exec { em ->
                val cb = em.criteriaBuilder
                val cq = cb.createQuery(String::class.java)
                val root = cq.from(InteractionStream::class.java)
                cq.select(root.get("id"))
                em.createQuery(cq).resultList
            }

            for (streamId in streamIds) {
                getOrCreateInteractionFlow(streamId, persistedStates[streamId])
            }

            log.info("已从数据库恢复 {} 个交互流", streamIds.size)
        } catch (e: DbOperationException) {
            log.warn("从数据库恢复交互流失败", e)
        }
    }

    /**
     * 获取或创建一个思维流，并确保它的观察线程已经启动
     */
    fun getOrCreateInteractionFlow(streamId: String): ThinkingFlow {
        return flowRegistry.get(streamId) ?: flowRegistry.computeIfAbsent(streamId) {
            createFlow(streamId, null)
        }
    }

    private fun getOrCreateInteractionFlow(streamId: String, persisted: PersistedFlowState?): ThinkingFlow {
        return flowRegistry.get(streamId) ?: flowRegistry.computeIfAbsent(streamId) {
            createFlow(streamId, persisted)
        }
    }

    private fun createFlow(streamId: String, persisted: PersistedFlowState?): ThinkingFlow {
        require(streamId.matches(STREAM_ID_PATTERN)) { "Invalid stream ID: $streamId" }

        val flow = Instance.get(ThinkingFlowFactory::class.java).setFlowId(streamId).build()

        // 用最近的历史消息初始化观察窗口：初始载入受 history_limit 限制，
        // 后续消息继续追加，窗口总量由 observation_window_size 控制
        val restoreLimit = if (historyLimit > 0) minOf(historyLimit, flow.capacity) else flow.capacity
        val history = databaseService.exec { em ->
            messageManager.listRecentMessages(em, streamId, restoreLimit)
        }
        flow.restoreHistory(history)

        val restored = restoreFlowState(flow, persisted)
        flow.restoreState(restored)

        // 状态校正后立即回写，保证下次重启读到的是一致的快照
        try {
            saveState(streamId, restored)
        } catch (e: Exception) {
            log.warn("回写思维流状态失败: {}", streamId, e)
        }

        taskExecutorService.submit(true) {
            FlowObserver(flow, observationStrategy, this).run()
        }

        log.debug("已创建思维流并启动观察线程: {}, state={}", streamId, restored.state)
        return flow
    }

    /**
     * 恢复持久化状态
     *
     * 状态退出不再依赖 idle 时长，因此这里只补齐活动时间/观察位置；
     * 间隔由 FlowObserver 从当前状态的 base 重新开始。
     */
    private fun restoreFlowState(flow: ThinkingFlow, persisted: PersistedFlowState?): PersistedFlowState {
        val now = System.currentTimeMillis()

        if (persisted == null) {
            return PersistedFlowState(
                state = FlowState.INACTIVE,
                lastActivityAt = maxOf(flow.lastActivityAt, now),
                lastObservedSequence = flow.lastObservedSequence,
                lastDecision = null,
            )
        }

        var lastActivityAt = maxOf(persisted.lastActivityAt, flow.lastActivityAt)
        if (persisted.state != FlowState.INACTIVE && lastActivityAt <= 0) {
            lastActivityAt = now
        }

        return persisted.copy(
            lastActivityAt = lastActivityAt,
            // 0 视为“没有持久化观察位置”，退回窗口内最大 sequence，避免重放旧消息
            lastObservedSequence = if (persisted.lastObservedSequence > 0) {
                persisted.lastObservedSequence
            } else {
                flow.lastObservedSequence
            },
        )
    }

    /**
     * 投递一条外部消息
     */
    fun onExternalMessage(streamId: String, message: Message) {
        getOrCreateInteractionFlow(streamId).addExternalMessage(message)
    }

    /**
     * 追加上一条 Bot 消息（不触发观察）
     *
     * @param replyTo 该回复覆盖的外部消息 sequence 列表
     */
    fun onBotMessage(streamId: String, message: Message, replyTo: List<Long>) {
        getOrCreateInteractionFlow(streamId).addBotMessage(message, replyTo)
    }

    /**
     * 关闭思维流管理器
     *
     * 先 flush 当前状态，再停止观察线程；停机期间正在执行的观察不保证完成。
     */
    override fun preDestroy() {
        val states = flowRegistry.all()
            .mapNotNull { flow -> flow.id?.let { it to flow.persistedState() } }
            .toMap()

        try {
            saveAllStates(states)
        } catch (e: Exception) {
            log.warn("关闭时保存思维流状态失败", e)
        }

        for (flow in flowRegistry.all()) {
            flow.stopObserving()
        }
    }

    /**
     * 持久化单个流；实现 [FlowStatePersister]，供观察线程在观察结束后调用。
     */
    override fun saveThinkingFlow(flow: ThinkingFlow) {
        val streamId = flow.id ?: return
        saveState(streamId, flow.persistedState())
    }

    private fun loadAllStates(): Map<String, PersistedFlowState> {
        return databaseService.exec { em ->
            stateStore.loadAll(em)
                .mapNotNull { entity ->
                    val streamId = entity.streamId ?: entity.stream?.id ?: return@mapNotNull null
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

    private fun saveState(streamId: String, state: PersistedFlowState) {
        databaseService.exec { em ->
            DatabaseService.execInTransaction(em) {
                val entity = em.find(ThinkingFlowState::class.java, streamId)
                    ?: ThinkingFlowState().apply { this.streamId = streamId }
                applyState(entity, state)
                stateStore.saveThinkingFlow(em, entity)
            }
        }
    }

    private fun saveAllStates(states: Map<String, PersistedFlowState>) {
        if (states.isEmpty()) {
            return
        }

        databaseService.exec { em ->
            DatabaseService.execInTransaction(em) {
                val entities = states.map { (streamId, state) ->
                    val entity = em.find(ThinkingFlowState::class.java, streamId)
                        ?: ThinkingFlowState().apply { this.streamId = streamId }
                    applyState(entity, state)
                    entity
                }
                stateStore.saveAll(em, entities)
            }
        }
    }

    private fun applyState(entity: ThinkingFlowState, state: PersistedFlowState) {
        entity.state = state.state.name
        entity.lastActivityAt = state.lastActivityAt
        entity.lastObservedSequence = state.lastObservedSequence
        entity.lastDecision = state.lastDecision
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

    val flowStatesCount: IntArray
        get() {
            var activeCount = 0
            var focusedCount = 0
            val flows = flowRegistry.all()

            flows.forEach { flow ->
                when (flow.state) {
                    FlowState.ACTIVE -> activeCount++
                    FlowState.FOCUSED -> focusedCount++
                    FlowState.INACTIVE -> {
                        // 不活跃流单独统计
                    }
                }
            }

            val totalCount = flows.size
            val sleepingCount = totalCount - activeCount - focusedCount
            return intArrayOf(totalCount, sleepingCount, activeCount, focusedCount)
        }

    companion object {
        private val STREAM_ID_PATTERN = "^([PG])-\\d+$".toRegex()

        private val log: Logger = LoggerFactory.getLogger(ThinkingFlowManager::class.java)
    }
}
