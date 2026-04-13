package org.maibot.sdk.model

import org.maibot.sdk.exceptions.ModelRequestFailed
import org.maibot.sdk.model.payload.AvailableFunctionItem
import org.maibot.sdk.model.payload.MessageContextItem
import java.util.concurrent.CompletableFuture
import kotlin.jvm.Throws

interface ModelRequestHandler {
    @Throws(ModelRequestFailed::class)
    fun getResponse(
        messageContext: List<MessageContextItem>,
        availableFunc: List<AvailableFunctionItem>? = null,
        formatClass: Class<*>? = null,
    ): CompletableFuture<APIResponse>

    fun getEmbedding(
        input: List<String>,
    ): CompletableFuture<List<List<Double>>>
}
