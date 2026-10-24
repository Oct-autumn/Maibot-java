package org.maibot.core.model

import org.maibot.sdk.model.APIResponse
import org.maibot.sdk.model.ModelCallContext
import org.maibot.sdk.model.ModelConfig

/**
 * 一次 LLM 任务调用事件
 *
 * 由 [org.maibot.core.model.ModelRequestHandlerImpl] 在任务开始/结束时产生：
 * - 开始：status = IN_PROGRESS，duration = 0，response = null；
 * - 成功/失败：status = SUCCESS/FAILED，携带最终模型、总耗时与响应。
 *
 * 中间的单次请求失败、重试、候选模型切换不单独记录。
 */
@JvmRecord
data class ModelCallEvent(
    val taskName: String,
    val model: ModelConfig,
    val callContext: ModelCallContext?,
    val status: ModelCallStatus,
    val durationMs: Long,
    val response: APIResponse? = null,
    val error: Throwable? = null,
)
