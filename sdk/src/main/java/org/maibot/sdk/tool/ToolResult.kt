package org.maibot.sdk.tool

/**
 * 工具执行结果
 */
@JvmRecord
data class ToolResult(
    val content: String,
    val isError: Boolean = false,
)
