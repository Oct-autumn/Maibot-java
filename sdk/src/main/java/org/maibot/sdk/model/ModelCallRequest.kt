package org.maibot.sdk.model

import org.maibot.sdk.model.payload.AvailableFunctionItem
import org.maibot.sdk.model.payload.MessageContextItem
import org.maibot.sdk.model.payload.ToolChoice

/**
 * 一次模型调用请求
 *
 * 把调用参数聚合成一个对象，避免 `getResponse` 的参数列表持续膨胀，
 * 后续增加选项时也不需要修改接口签名。
 *
 * @param messageContext 对话上下文
 * @param availableFunc  可用的工具定义（可选）
 * @param formatClass    结构化输出的目标类型（可选）
 * @param toolChoice     工具选择策略（可选），见 [ToolChoice]
 * @param callContext    调用方业务上下文（可选，仅用于存档关联）
 */
@JvmRecord
data class ModelCallRequest(
    val messageContext: List<MessageContextItem>,
    val availableFunc: List<AvailableFunctionItem>? = null,
    val formatClass: Class<*>? = null,
    val callContext: ModelCallContext? = null,
    val toolChoice: ToolChoice? = null,
)
