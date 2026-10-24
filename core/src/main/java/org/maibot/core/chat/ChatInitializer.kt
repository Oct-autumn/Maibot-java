package org.maibot.core.chat

import org.maibot.core.config.CoreConfig
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.Value
import org.maibot.sdk.model.ModelManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 聊天启动初始化
 *
 * 在启动期注册发言模型任务，避免像 M1 那样等到第一条消息才在 ChatService 构造函数里注册。
 */
@Component
class ChatInitializer
@AutoInject private constructor(
    @param:Value($$"${*}") private val config: CoreConfig,
    private val modelManager: ModelManager,
) {
    fun initialize() {
        val taskName = config.chat.modelTask
        val models = config.modelTasks[taskName]

        if (models.isNullOrEmpty()) {
            if (config.chat.enabled) {
                log.warn("未配置 model_tasks.{}，聊天已启用但无法生成回复", taskName)
            }
            return
        }

        modelManager[taskName] = models
        log.info("聊天任务模型已注册: task={}, models={}", taskName, models.joinToString { it.modelName })
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ChatInitializer::class.java)
    }
}
