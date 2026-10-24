package org.maibot.core.model

import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 模型调用记录器
 *
 * 模型调用层只依赖 [ModelCallRecorder] 接口；本实现把落库细节委托给
 * [ModelCallLogStore]，并保证任何存档失败都不会影响调用结果。
 */
@Component
class ModelInteractionRecorder
@AutoInject private constructor(
    private val logStore: ModelCallLogStore,
) : ModelCallRecorder {
    override fun start(event: ModelCallEvent): Long? {
        return try {
            logStore.start(event)
        } catch (e: Exception) {
            log.warn(
                "写入模型任务开始记录失败（task: {}, model: {}, status: {}）",
                event.taskName, event.model.name, event.status, e
            )
            null
        }
    }

    override fun finish(recordId: Long?, event: ModelCallEvent) {
        try {
            logStore.finish(recordId, event)
        } catch (e: Exception) {
            log.warn(
                "写入模型任务结束记录失败（task: {}, model: {}, status: {}）",
                event.taskName, event.model.name, event.status, e
            )
        }

        event.error?.let {
            log.debug(
                "模型任务最终失败（task: {}, model: {}）: {}",
                event.taskName, event.model.name, it.message
            )
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ModelInteractionRecorder::class.java)
    }
}
