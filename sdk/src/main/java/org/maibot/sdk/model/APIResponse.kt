package org.maibot.sdk.model

import org.maibot.sdk.model.payload.MessageContextItem
import tools.jackson.databind.JsonNode

@JvmRecord
data class APIResponse(
    val oriResp: Any,
    val reasoning: String?,
    val response: MessageContextItem?,
    val toolCalls: List<ToolCallInfo>?,
    val tokenStatistics: TokenStatistics?
) {
    data class ToolCallInfo(
        val toolName: String,
        val toolArgs: JsonNode?,
        val id: String?
    )

    @JvmRecord
    data class TokenStatistics(
        val inputTokens: Long,
        val outputTokens: Long,
        val totalTokens: Long
    )
}
