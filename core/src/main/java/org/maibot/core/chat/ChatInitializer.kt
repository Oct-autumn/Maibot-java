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
 * 在启动期注册发言模型任务。
 */
@Component
class ChatInitializer
@AutoInject private constructor(
    @param:Value($$"${*}") private val config: CoreConfig,
    private val modelManager: ModelManager,
) {
    fun initialize() {
        val chatModels = config.modelTasks[config.chat.modelTask]
        if (chatModels.isNullOrEmpty()) {
            if (config.chat.enabled) {
                log.warn("未配置 model_tasks.{}，聊天功能已启用但无法调用模型", config.chat.modelTask)
            }
        } else {
            modelManager[config.chat.modelTask] = chatModels
        }

        val observerTask = config.thinking.observer.modelTask
        val observerModels = config.modelTasks[observerTask]
        if (observerModels.isNullOrEmpty()) {
            if (config.chat.enabled) {
                log.warn("未配置 model_tasks.{}，观察功能已启用但无法调用模型", observerTask)
            }
        } else {
            modelManager[observerTask] = observerModels
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ChatInitializer::class.java)
    }
}
