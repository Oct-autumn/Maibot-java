package org.maibot.sdk.model

import com.openai.models.responses.ResponseInputItem
import com.openai.models.responses.Tool
import java.util.concurrent.CompletableFuture

interface ModelRequestHandler {
    fun getResponse(
        contextList: ArrayList<ResponseInputItem>,
        toolOptions: MutableList<Tool>?,
        maxTokens: Int?,
        temperature: Double?
        //      RespFormat responseFormat,
        //      StreamResponseHandler streamResponseHandler,
        //      AsyncResponseParser asyncResponseParser,
        //      InterruptFlag interruptFlag
    ): CompletableFuture<APIResponse>
}
