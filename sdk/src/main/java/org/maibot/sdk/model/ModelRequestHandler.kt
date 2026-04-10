package org.maibot.sdk.model

import org.maibot.sdk.model.payload.AvailableFunctionItem
import org.maibot.sdk.model.payload.MessageContextItem
import java.util.concurrent.CompletableFuture

interface ModelRequestHandler {
    fun getResponse(
        messageContext: List<MessageContextItem>,
        availableFunc: List<AvailableFunctionItem>? = null,
        formatClass: Class<*>? = null,
    ): CompletableFuture<APIResponse>

    fun getEmbedding(
        input: List<String>,
    ): CompletableFuture<List<List<Double>>>
}
