package org.maibot.sdk.model

import com.openai.models.responses.Response

@JvmRecord
data class APIResponse(
    val response: Response,
    val tokenStatistics: TokenStatistics
) {
    @JvmRecord
    data class TokenStatistics(
        val inputTokens: Long,
        val outputTokens: Long,
        val totalTokens: Long
    )
}
