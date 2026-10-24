package org.maibot.sdk.model

import org.maibot.sdk.exceptions.ModelRequestFailed
import java.util.concurrent.CompletableFuture
import kotlin.jvm.Throws

interface ModelRequestHandler {
    /**
     * 请求一次模型响应
     *
     * @param request 本次调用请求（上下文、工具、结构化输出类型、业务上下文）
     */
    @Throws(ModelRequestFailed::class)
    fun getResponse(request: ModelCallRequest): CompletableFuture<APIResponse>

    fun getEmbedding(
        input: List<String>,
    ): CompletableFuture<List<List<Double>>>
}
