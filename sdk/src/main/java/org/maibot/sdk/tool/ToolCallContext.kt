package org.maibot.sdk.tool

import tools.jackson.databind.JsonNode

/**
 * 一次工具调用的上下文
 *
 * 目标会话由 Core 从当前交互流注入，模型参数里不包含目标，避免消息被发送到别的会话。
 *
 * @param streamId        交互流 ID
 * @param triggerSequence 触发本次观察的消息序列号（可空）
 * @param windowVersion   生成开始时观察窗口的版本，供后续打断/更正策略使用（W/O 只记录）
 * @param toolCallId      模型侧的工具调用 ID（可空）
 * @param arguments       模型侧传入的工具参数（可空）
 */
@JvmRecord
data class ToolCallContext(
    val streamId: String?,
    val triggerSequence: Long?,
    val windowVersion: Long?,
    val toolCallId: String?,
    val arguments: JsonNode?,
)
