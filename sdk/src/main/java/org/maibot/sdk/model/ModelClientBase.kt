package org.maibot.sdk.model

import org.maibot.sdk.model.payload.AvailableFunctionItem
import org.maibot.sdk.model.payload.MessageContextItem
import java.util.concurrent.CompletableFuture

/**
 * BaseModelClient 是一个抽象类，定义了与LLM模型交互的基本接口。
 */
abstract class ModelClientBase protected constructor(
    baseURL: String,
    apiKey: String,
    connectTimeout: Long
) {
    abstract fun getResponse(
        modelIdentifier: String,
        messageContext: List<MessageContextItem>,
        availableFunc: List<AvailableFunctionItem>? = null,
        formatClass: Class<*>? = null,
        retryDelayBase: Long,
        maxRetry: Int,
        maxTokens: Long,
        temperature: Double,
        enableThinking: Boolean = false,
        forceStreamMode: Boolean = false,
    ): APIResponse

    abstract fun getEmbedding(
        modelIdentifier: String,
        input: List<String>,
        embeddingFuture: CompletableFuture<List<List<Double>>>
    )
}
