package org.maibot.core.model

import org.maibot.core.config.CoreConfig
import org.maibot.core.util.ModelCostCalculator
import org.maibot.core.util.TextTruncator
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.Value
import org.maibot.sdk.storage.db.DatabaseService
import org.maibot.sdk.storage.db.dao.ModelApiRequest
import org.maibot.sdk.storage.db.dao.ModelInteraction
import org.maibot.sdk.util.MapperUtils
import java.time.Instant

/**
 * 模型调用日志存储
 *
 * 一次 LLM 任务对应 `model_api_request` 的一行：
 * - 开始时插入 IN_PROGRESS；
 * - 所有候选模型/重试结束后更新为 SUCCESS / FAILED；
 * - 只有最终 SUCCESS 且任务在 `[model_log].persist_content_tasks` 中时，
 *   才写对应的 `model_interaction` 内容存档。
 *
 * 中间单次请求的失败不单独落行。
 */
@Component
class ModelCallLogStore
@AutoInject private constructor(
    @param:Value($$"${*}") private val coreConfig: CoreConfig,
    private val databaseService: DatabaseService,
) {
    /**
     * 任务开始：插入 IN_PROGRESS 记录
     *
     * @return 记录 ID
     */
    fun start(event: ModelCallEvent): Long? {
        require(event.status == ModelCallStatus.IN_PROGRESS) { "start 只接受 IN_PROGRESS 状态" }

        return databaseService.exec { em ->
            DatabaseService.execInTransaction(em) {
                val request = ModelApiRequest().apply {
                    this.timestamp = Instant.now()
                    this.taskName = event.taskName
                    this.modelName = event.model.name
                    this.apiProvider = event.model.apiProvider
                    this.inputTokens = 0L
                    this.outputTokens = 0L
                    this.totalTokens = 0L
                    this.totalCost = 0.0
                    this.durationMs = 0L
                    this.status = event.status.name
                    this.cachedInputTokens = null
                    this.cacheHitRate = null
                }
                em.persist(request)
                // IDENTITY 主键需要 flush 后才有值
                em.flush()
                request.id
            }
        }
    }

    /**
     * 任务结束：更新同一行；若开始记录缺失则补插最终记录
     */
    fun finish(recordId: Long?, event: ModelCallEvent) {
        require(event.status == ModelCallStatus.SUCCESS || event.status == ModelCallStatus.FAILED) {
            "finish 只接受 SUCCESS / FAILED 状态"
        }

        val modelLog = coreConfig.modelLog
        val tokens = event.response?.tokenStatistics

        databaseService.exec { em ->
            DatabaseService.execInTransaction(em) {
                val request = recordId?.let { em.find(ModelApiRequest::class.java, it) }
                    ?: ModelApiRequest().apply {
                        this.timestamp = Instant.now()
                        this.taskName = event.taskName
                        em.persist(this)
                    }

                request.timestamp = request.timestamp ?: Instant.now()
                request.taskName = event.taskName
                request.modelName = event.model.name
                request.apiProvider = event.model.apiProvider
                request.inputTokens = tokens?.inputTokens ?: 0L
                request.outputTokens = tokens?.outputTokens ?: 0L
                request.totalTokens = tokens?.totalTokens
                    ?: (request.inputTokens!! + request.outputTokens!!)
                request.totalCost = ModelCostCalculator.calculate(event.model, tokens)
                request.durationMs = event.durationMs
                request.status = event.status.name

                // 缓存命中：provider 未上报时保持 null，不要把“不支持统计”写成 0
                request.cachedInputTokens = tokens?.cachedInputTokens
                request.cacheHitRate = tokens?.cachedInputTokens
                    ?.takeIf { tokens.inputTokens > 0 }
                    ?.let { it.toDouble() / tokens.inputTokens }

                // 补插记录时还没有 ID，先 flush
                if (request.id == null) {
                    em.flush()
                }
                val requestId = request.id ?: return@execInTransaction

                if (event.status != ModelCallStatus.SUCCESS || event.taskName !in modelLog.persistContentTasks) {
                    return@execInTransaction
                }

                val interaction = em.find(ModelInteraction::class.java, requestId)
                    ?: ModelInteraction().apply {
                        this.requestId = requestId
                        // created_at 是 NOT NULL，必须在 persist 之前赋值；Hibernate 会在 persist 时检查
                        this.createdAt = Instant.now()
                        em.persist(this)
                    }

                interaction.streamId = event.callContext?.streamId
                interaction.triggerSequence = event.callContext?.triggerSequence
                interaction.reasoning = TextTruncator.truncate(
                    event.response?.reasoning, modelLog.contentMaxChars, TRUNCATED_MARKER
                )
                interaction.content = TextTruncator.truncate(
                    event.response?.response?.contentList?.firstOrNull()?.asText()?.trim(),
                    modelLog.contentMaxChars, TRUNCATED_MARKER
                )
                interaction.toolCallsJson = TextTruncator.truncate(
                    event.response?.toolCalls?.takeIf { it.isNotEmpty() }
                        ?.let { MapperUtils.jsonMapper.writeValueAsString(it) },
                    modelLog.contentMaxChars, TRUNCATED_MARKER
                )
                interaction.finishReason = null
                interaction.createdAt = Instant.now()
            }
        }
    }

    companion object {
        private const val TRUNCATED_MARKER = "…（已截断）"
    }
}
