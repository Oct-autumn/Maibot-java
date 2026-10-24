package org.maibot.core.chat.speech

import org.maibot.core.config.CoreConfig
import org.maibot.core.model.ModelCaller
import org.maibot.core.thinking.ObservationSnapshot
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.Value
import org.maibot.sdk.model.ModelCallContext
import org.maibot.sdk.model.ModelCallRequest

/**
 * 使用聊天模型生成发言文本
 *
 * 模型任务沿用 M1 的 `model_tasks.chat`（可由 `[chat].model_task` 覆盖），
 * 每次调用都会带上 stream/trigger 上下文，便于 model_interaction 复盘。
 */
@Component
class ModelSpeechGenerator
@AutoInject private constructor(
    @param:Value($$"${*}") private val config: CoreConfig,
    private val modelCaller: ModelCaller,
    private val promptBuilder: SpeechPromptBuilder,
) : SpeechGenerator {
    override fun generate(snapshot: ObservationSnapshot, triggerSequence: Long): String? {
        val messages = promptBuilder.build(snapshot, triggerSequence)
        val request = ModelCallRequest(
            messageContext = messages,
            callContext = ModelCallContext(
                streamId = snapshot.streamId,
                triggerSequence = triggerSequence,
            ),
        )

        val response = modelCaller.call(
            taskName = config.chat.modelTask,
            request = request,
            timeoutSec = config.chat.replyTimeoutSec,
        )

        return response.response?.contentList?.firstOrNull()?.asText()?.trim()?.takeIf { it.isNotBlank() }
    }

}
