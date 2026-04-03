package org.maibot.core.thinking

import jakarta.persistence.EntityManager
import org.maibot.core.ioc.Instance
import org.maibot.core.persistence.DatabaseServiceImpl
import org.maibot.core.util.TaskExecuteServiceImpl
import org.maibot.sdk.exceptions.DbOperationException
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.DestroyableComponent
import org.maibot.sdk.storage.db.dao.InteractionEntity
import org.maibot.sdk.storage.db.dao.InteractionGroup
import org.maibot.sdk.storage.db.dao.InteractionStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 交互流管理器
 * 
 * 
 * 我们定义对于某个Adapter的某个Group为一个交互流。
 * 对于一对一交流来说，也可以将其视为一个成员数量为2的交互流。
 * 
 * 
 * 每个交互流都有独立的Observation组件用于观察流内容并做出决策。
 * 
 * 
 * 对于交互流来说，其生命周期存在以下状态：<br></br>
 * 1. 创建：当有新的交互流被创建时，进入创建状态；<br></br>
 * 2. 活跃：当交互流中有成员在进行交流时，该流进入活跃状态；<br></br>
 * 3. 专注：当bot认为交互流中有值得关注的内容时，该流进入专注状态；<br></br>
 * 4. 休眠：当交互流中长时间没有活跃交流时，该流进入休眠状态；<br></br>
 */
@Component
class ThinkingFlowManager
@AutoInject private constructor(
    private val databaseService: DatabaseServiceImpl,
    private val taskExecutorService: TaskExecuteServiceImpl
) : DestroyableComponent {
    /** 当前所有交互流 */
    private val thinkingFlows = ConcurrentHashMap<String, ThinkingFlow>()

    /**
     * 初始化思维流管理器
     */
    fun initialize() {
        this.restoreFromDb()
        this.runInteractionFlowObservers()
    }

    /**
     * 从数据库恢复状态
     */
    private fun restoreFromDb() {
        try {
            databaseService.exec { em: EntityManager ->
                val cb = em.criteriaBuilder
                val cq = cb.createQuery(InteractionStream::class.java)
                val root = cq.from(InteractionStream::class.java)
                cq.select(root)
                val query = em.createQuery(cq)
                val streams = query.getResultList()
                for (stream in streams) {
                    val streamId = stream.id!!
                    val flow = Instance.get(ThinkingFlowFactory::class.java).setFlowId(streamId).build()
                    thinkingFlows[streamId] = flow
                }
            }
        } catch (e: DbOperationException) {
            log.warn("Failed to restore interaction streams from database.", e)
        }
    }

    /**
     * 运行所有交互流的观察者
     */
    private fun runInteractionFlowObservers() {
        for (flow in this.thinkingFlows.values) {
            if (flow.state.isAtLeast(ThinkingFlow.FlowState.ACTIVE)) {
                this.taskExecutorService.submit(true) { flow.observe() }
            }
        }
    }

    /**
     * 关闭思维流管理器
     */
    override fun preDestroy() {
        for (flow in this.thinkingFlows.values) {
            flow.stopObserving()
        }
        this.saveToDb()
    }

    /**
     * 保存当前状态到数据库
     */
    private fun saveToDb() {
        databaseService.exec { em: EntityManager ->
            for (flow in this.thinkingFlows.values) {
                // TODO: 将Flow同步到数据库
            }
        }
    }

    /**
     * 通过用户ID获取或创建一个思维流
     * 
     * @param streamId 流ID
     * @return 交互流实例
     */
    fun getOrCreateInteractionFlow(streamId: String): ThinkingFlow {
        return thinkingFlows.computeIfAbsent(
            streamId
        ) { _: String ->
            require(streamId.matches("^([PG])-\\d+$".toRegex())) { "Invalid stream ID: $streamId" }

            val parts: Array<String> = streamId.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            // 持久化
            databaseService.exec { em: EntityManager ->
                val stream = InteractionStream()
                stream.id = streamId

                if (parts[0] == "P") {
                    // 私聊流
                    val userId = parts[1].toLong()
                    // 在数据库中查询对应的私聊对象是否存在
                    val res =
                        em.find(InteractionEntity::class.java, userId)
                            ?: throw RuntimeException("InteractionEntity with ID $userId does not exist. This shouldn't happen.")
                    stream.entity = res
                } else if (parts[0] == "G") {
                    // 群聊流
                    val groupId = parts[1].toLong()
                    // 在数据库中查询对应的群聊对象是否存在
                    val res =
                        em.find(InteractionGroup::class.java, groupId)
                            ?: throw RuntimeException("InteractionGroup with ID $groupId does not exist. This shouldn't happen.")
                    stream.group = res
                }
                em.persist(stream)
            }
            Instance.get(ThinkingFlowFactory::class.java).setFlowId(streamId).build()
        }
    }

    val flowStatesCount: IntArray
        get() {
            var activeCount = 0
            var focusedCount = 0

            thinkingFlows.values.forEach { flow ->
                when (flow.state) {
                    ThinkingFlow.FlowState.ACTIVE -> activeCount++
                    ThinkingFlow.FlowState.FOCUSED -> focusedCount++
                    else -> {
                        // 其他状态不计入活跃或专注计数
                    }
                }
            }

            val totalCount = this.thinkingFlows.size
            val sleepingCount = totalCount - activeCount - focusedCount
            return intArrayOf(totalCount, sleepingCount, activeCount, focusedCount)
        }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ThinkingFlowManager::class.java)
    }
}
