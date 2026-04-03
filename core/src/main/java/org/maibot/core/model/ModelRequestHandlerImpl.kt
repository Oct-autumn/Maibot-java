package org.maibot.core.model

import com.openai.client.OpenAIClient
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputItem
import com.openai.models.responses.Tool
import org.maibot.sdk.SNoGenerator
import org.maibot.sdk.TaskExecuteService
import org.maibot.sdk.model.APIResponse
import org.maibot.sdk.model.APIResponse.TokenStatistics
import org.maibot.sdk.model.ModelConfig
import org.maibot.sdk.model.ModelRequestHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.concurrent.CompletableFuture

// TODO: 需要重构，目前的实现过于臃肿，职责不够清晰，后续可以考虑将模型选择、请求构建、响应处理等逻辑拆分到不同的类中，以提高代码的可维护性和可测试性
class ModelRequestHandlerImpl(
    private val taskExecuteService: TaskExecuteService,
    private val providers: MutableMap<String, OpenAIClient>,
    private val choosableModels: MutableList<ModelConfig>
) : ModelRequestHandler {
    override fun getResponse(
        contextList: ArrayList<ResponseInputItem>,
        toolOptions: MutableList<Tool>?,
        maxTokens: Int?,
        temperature: Double?
        //      RespFormat responseFormat,
        //      StreamResponseHandler streamResponseHandler,
        //      AsyncResponseParser asyncResponseParser,
        //      InterruptFlag interruptFlag
    ): CompletableFuture<APIResponse> {
        // 为每次模型请求生成一个唯一ID，便于日志追踪
        val responseFuture = CompletableFuture<APIResponse>()

        taskExecuteService.submit(true) {
            MDC.put("ModelRequestId", SNoGenerator.nextSeq().toHexString())
            try {
                for (choosableModel in choosableModels) {
                    val client: OpenAIClient = providers[choosableModel.apiProvider]!!
                    val responseCreateParams = ResponseCreateParams.builder()
                        .model(choosableModel.modelIdentifier)
                        .inputOfResponse(contextList)
                        .temperature(temperature ?: choosableModel.temperature)
                        .maxOutputTokens((maxTokens ?: choosableModel.maxTokens).toLong())
                        .tools(toolOptions!!).build()

                    for (retry in 0..choosableModel.maxRetry) {
                        var resp: Response?
                        try {
                            resp = client.responses().create(responseCreateParams)

                            if (resp.isValid()) {
                                // 成功获取响应，记录日志并返回结果
                                log.info("Model {} succeeded on attempt {}.", choosableModel.name, retry)
                                var tokenStats: TokenStatistics? = null
                                if (resp.usage().isPresent) {
                                    val usageStat = resp.usage().get()
                                    log.info(
                                        "Model {} usage - prompt tokens: {}, completion tokens: {}, total tokens: {}.",
                                        choosableModel.name,
                                        usageStat.inputTokens(),
                                        usageStat.outputTokens(),
                                        usageStat.totalTokens()
                                    )

                                    tokenStats = TokenStatistics(
                                        usageStat.inputTokens(), usageStat.outputTokens(), usageStat.totalTokens()
                                    )
                                }
                                responseFuture.complete(APIResponse(resp, tokenStats!!))
                            } else {
                                // 响应为null，记录警告日志并继续重试
                                log.warn(
                                    "Model {} returned null response on attempt {}. Retrying...",
                                    choosableModel.name,
                                    retry
                                )
                            }
                        } catch (e: Exception) {
                            if (retry == choosableModel.maxRetry) {
                                // 最后一次重试失败，记录日志并继续尝试下一个模型
                                log.warn(
                                    "Model {} failed after {} attempts: {}. Moving to next model if available.",
                                    choosableModel.name,
                                    retry,
                                    e.message
                                )
                            } else {
                                // 记录重试日志
                                log.info(
                                    "Model {} attempt {} failed: {}. Retrying...",
                                    choosableModel.name,
                                    retry,
                                    e.message
                                )
                            }
                        }
                    }
                }

                responseFuture.completeExceptionally(
                    RuntimeException(
                        "All models failed to provide a valid response after maximum retries."
                    )
                )
            } finally {
                // 无论请求成功与否，最后都要清理MDC中的请求ID，避免对后续请求造成干扰
                MDC.remove("ModelRequestId") // 请求结束后清除MDC中的请求ID
            }
        }

        return responseFuture
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ModelRequestHandlerImpl::class.java)
    }
}
