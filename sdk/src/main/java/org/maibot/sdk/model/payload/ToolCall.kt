package org.maibot.sdk.model.payload

import tools.jackson.databind.JsonNode

/**
 * 模型侧发起的一次工具调用
 *
 * 用于把 assistant 的 tool_calls 放回下一轮上下文；工具结果再通过
 * [MessageContextItem.ofTool] 以 TOOL 角色回灌。
 *
 * @param id        模型生成的工具调用 ID，工具结果需要用它关联
 * @param name      工具名
 * @param arguments 工具参数（JSON）
 */
@JvmRecord
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JsonNode? = null,
)
