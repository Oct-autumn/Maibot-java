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
import org.maibot.sdk.storage.db.dao.InteractionStream
import org.maibot.sdk.storage.db.dao.Message
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
    @param:Value($$"${chat.history_limit}") private val historyLimit: Int,
) : DestroyableComponent {

    /**
     * 初始化思维流管理器：从数据库恢复已有的交互流并启动观察线程
     */
    fun initialize() {
        restoreFromDb()
    }

    /**
     * 从数据库恢复交互流
     */
    private fun restoreFromDb() {
        try {
            val streamIds = databaseService.exec { em ->
                val cb = em.criteriaBuilder
                val cq = cb.createQuery(String::class.java)
                val root = cq.from(InteractionStream::class.java)
                cq.select(root.get<String>("id"))
                em.createQuery(cq).resultList
            }

            for (streamId in streamIds) {
                getOrCreateInteractionFlow(streamId)
            }
        } catch (e: DbOperationException) {
            log.warn("从数据库恢复交互流失败", e)
        }
    }

    /**
     * 获取或创建一个思维流，并确保它的观察线程已经启动
     */
    fun getOrCreateInteractionFlow(streamId: String): ThinkingFlow {
        return flowRegistry.get(streamId) ?: flowRegistry.computeIfAbsent(streamId) {
            createFlow(streamId)
        }
    }

    private fun createFlow(streamId: String): ThinkingFlow {
        require(streamId.matches(STREAM_ID_PATTERN)) { "Invalid stream ID: $streamId" }

        val flow = Instance.get(ThinkingFlowFactory::class.java).setFlowId(streamId).build()

        // 用最近的历史消息初始化观察窗口：初始载入受 history_limit 限制，
        // 后续消息继续追加，窗口总量由 observation_window_size 控制
        val restoreLimit = if (historyLimit > 0) minOf(historyLimit, flow.capacity) else flow.capacity
        val history = databaseService.exec { em ->
            messageManager.listRecentMessages(em, streamId, restoreLimit)
        }
        flow.restoreHistory(history)

        taskExecutorService.submit(true) {
            FlowObserver(flow, observationStrategy).run()
        }

        log.debug("已创建思维流并启动观察线程: {}", streamId)
        return flow
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
     */
    override fun preDestroy() {
        for (flow in flowRegistry.all()) {
            flow.stopObserving()
        }
    }

    val flowStatesCount: IntArray
        get() {
            var activeCount = 0
            var focusedCount = 0
            val flows = flowRegistry.all()

            flows.forEach { flow ->
                when (flow.state) {
                    ThinkingFlow.FlowState.ACTIVE -> activeCount++
                    ThinkingFlow.FlowState.FOCUSED -> focusedCount++
                    else -> {
                        // 其他状态不计入活跃或专注计数
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
