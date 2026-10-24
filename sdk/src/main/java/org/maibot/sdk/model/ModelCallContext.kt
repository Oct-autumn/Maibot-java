package org.maibot.sdk.model

/**
 * 模型调用的业务上下文（可选）
 *
 * 只用于把一次模型调用关联回交互流 / 触发消息，便于调用存档与事后复盘；
 * 不参与模型请求本身的构造，也不要求调用方提供。
 *
 * @param streamId        交互流 ID（如 `P-1`）
 * @param triggerSequence 触发本次调用的消息序列号
 */
@JvmRecord
data class ModelCallContext(
    val streamId: String? = null,
    val triggerSequence: Long? = null,
)
