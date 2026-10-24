package org.maibot.core.model

import org.maibot.core.config.CoreConfig
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.DestroyableComponent
import org.maibot.sdk.ioc.Value
import org.maibot.sdk.storage.db.DatabaseService
import org.maibot.sdk.storage.db.dao.ModelInteraction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 过期模型调用内容清理任务
 *
 * 启动时清理一次，之后按 `[model_log].cleanup_interval_minutes` 定时清理；
 * 只删内容表，不碰统计表。
 */
@Component
class ModelInteractionCleaner
@AutoInject private constructor(
    @param:Value($$"${*}") private val coreConfig: CoreConfig,
    private val databaseService: DatabaseService,
) : DestroyableComponent {
    private var scheduler: ScheduledExecutorService? = null
    private val initialized = AtomicBoolean(false)

    /**
     * 启动清理任务（幂等），由 MaibotMain 显式调用
     */
    fun initialize() {
        if (!initialized.compareAndSet(false, true)) {
            return
        }

        this.cleanupExpired()

        val intervalMinutes = coreConfig.modelLog.cleanupIntervalMinutes
        if (intervalMinutes <= 0 || coreConfig.modelLog.contentRetentionDays <= 0) {
            return
        }

        val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "model-log-cleanup").apply { isDaemon = true }
        }
        executor.scheduleWithFixedDelay(
            {
                try {
                    this.cleanupExpired()
                } catch (e: Exception) {
                    log.warn("清理过期模型调用内容失败", e)
                }
            },
            intervalMinutes.toLong(),
            intervalMinutes.toLong(),
            TimeUnit.MINUTES
        )
        scheduler = executor
    }

    override fun preDestroy() {
        scheduler?.shutdownNow()
        scheduler = null
    }

    /**
     * 清理过期的内容存档
     */
    private fun cleanupExpired() {
        val retentionDays = coreConfig.modelLog.contentRetentionDays
        if (retentionDays <= 0) {
            return
        }

        val cutoff = Instant.now().minus(retentionDays.toLong(), ChronoUnit.DAYS)
        val deleted = databaseService.exec { em ->
            DatabaseService.execInTransaction(em) {
                em.createQuery("DELETE FROM ModelInteraction m WHERE m.createdAt < :cutoff")
                    .setParameter("cutoff", cutoff)
                    .executeUpdate()
            }
        }

        if (deleted > 0) {
            log.info("已清理 {} 条过期模型调用内容（保留 {} 天）", deleted, retentionDays)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ModelInteractionCleaner::class.java)
    }
}
