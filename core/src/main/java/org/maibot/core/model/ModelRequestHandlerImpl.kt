package org.maibot.core.model

import org.maibot.sdk.SNoGenerator
import org.maibot.sdk.task.TaskExecuteService
import org.maibot.sdk.exceptions.ModelRequestFailed
import org.maibot.sdk.model.APIResponse
import org.maibot.sdk.model.ModelClientBase
import org.maibot.sdk.model.ModelConfig
import org.maibot.sdk.model.ModelRequestHandler
import org.maibot.sdk.model.payload.AvailableFunctionItem
import org.maibot.sdk.model.payload.MessageContextItem
import org.maibot.sdk.task.ManagedTask
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.concurrent.CompletableFuture

class ModelRequestHandlerImpl(
    private val taskExecuteService: TaskExecuteService,
    private val providers: MutableMap<String, ModelClientBase>,
    private val choosableModels: MutableList<ModelConfig>
) : ModelRequestHandler {
    override fun getResponse(
        messageContext: List<MessageContextItem>, availableFunc: List<AvailableFunctionItem>?, formatClass: Class<*>?
    ): CompletableFuture<APIResponse> {
        return taskExecuteService.submit(false, RequestTask(messageContext, availableFunc, formatClass))
    }

    override fun getEmbedding(input: List<String>): CompletableFuture<List<List<Double>>> {
        TODO("Not implemented yet")
    }

    /**
     * RequestTask 是一个内部类，负责处理单次模型请求的逻辑。
     *
     * 它会按照配置的可选模型列表依次尝试调用模型接口，直到成功获取响应或所有模型调用失败。
     */
    inner class RequestTask(
        private val messageContext: List<MessageContextItem>,
        private val availableFunc: List<AvailableFunctionItem>?,
        private val formatClass: Class<*>?
    ) : ManagedTask<APIResponse>() {
        override fun invoke(): APIResponse {
            // 在请求开始时生成一个唯一的请求ID，并将其放入MDC中，方便后续日志追踪
            MDC.put("ModelRequestId", SNoGenerator.nextSeq().toHexString())
            try {
                for (choosableModel in choosableModels) {
                    val client = providers[choosableModel.apiProvider]!!
                    val responseFuture = subTask(true, PerModelRequestTask(client, choosableModel))

                    try {
                        responseFuture.get()?.let {
                            log.debug("模型 ${choosableModel.modelIdentifier} 调用成功")
                            return it
                        }
                    } catch (e: Exception) {
                        log.warn("模型 ${choosableModel.modelIdentifier} 调用失败，正在尝试下一个模型", e)
                    }
                }
            } finally {
                // 无论请求成功与否，最后都要清理MDC中的请求ID，避免对后续请求追踪造成干扰
                MDC.remove("ModelRequestId") // 请求结束后清除MDC中的请求ID
            }

            log.warn("所有模型调用失败，无法获取响应")
            throw ModelRequestFailed("All choosable model requests failed")
        }

        /**
         * PerModelRequestTask 是一个内部类，负责处理单个模型的请求逻辑。
         *
         * 它会调用指定模型的API接口，并根据模型配置中的参数进行重试和错误处理。
         */
        inner class PerModelRequestTask(
            private val client: ModelClientBase,
            private val modelConfig: ModelConfig
        ) : ManagedTask<APIResponse>() {
            override fun invoke(): APIResponse {
                return client.getResponse(
                    modelConfig.modelIdentifier,
                    messageContext,
                    availableFunc,
                    formatClass,
                    modelConfig.retryDelayBase,
                    modelConfig.maxRetry,
                    modelConfig.maxTokens,
                    modelConfig.temperature,
                    modelConfig.enableThinking,
                    modelConfig.forceStreamMode
                )
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ModelRequestHandlerImpl::class.java)
    }
}
