package org.maibot.core.model

import org.maibot.sdk.SNoGenerator
import org.maibot.sdk.exceptions.ModelRequestFailed
import org.maibot.sdk.model.APIResponse
import org.maibot.sdk.model.ModelCallRequest
import org.maibot.sdk.model.ModelClientBase
import org.maibot.sdk.model.ModelConfig
import org.maibot.sdk.model.ModelRequestHandler
import org.maibot.sdk.task.ManagedTask
import org.maibot.sdk.task.TaskExecuteService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.concurrent.CompletableFuture

class ModelRequestHandlerImpl(
    private val taskName: String,
    private val taskExecuteService: TaskExecuteService,
    private val providers: MutableMap<String, ModelClientBase>,
    private val choosableModels: MutableList<ModelConfig>,
    private val recorder: ModelCallRecorder,
) : ModelRequestHandler {
    override fun getResponse(request: ModelCallRequest): CompletableFuture<APIResponse> {
        return taskExecuteService.submit(false, RequestTask(request))
    }

    override fun getEmbedding(input: List<String>): CompletableFuture<List<List<Double>>> {
        TODO("Not implemented yet")
    }

    /**
     * RequestTask 负责「一次 LLM 任务」：
     *
     * - 任务开始时写一条 IN_PROGRESS 审计记录；
     * - 依次尝试候选模型（客户端内部还会按配置重试）；
     * - 全部失败后只写一条 FAILED，成功则写 SUCCESS；
     * - 中间单次请求失败不单独落库。
     */
    inner class RequestTask(
        private val request: ModelCallRequest,
    ) : ManagedTask<APIResponse>() {
        override fun invoke(): APIResponse {
            val initialModel = choosableModels.firstOrNull()
                ?: throw ModelRequestFailed("No choosable models configured for task: $taskName")

            // 在请求开始时生成一个唯一的请求ID，并将其放入MDC中，方便后续日志追踪
            MDC.put("ModelRequestId", SNoGenerator.nextSeq().toHexString())
            val startNanos = System.nanoTime()

            val auditId = recorder.start(
                ModelCallEvent(
                    taskName = taskName,
                    model = initialModel,
                    callContext = request.callContext,
                    status = ModelCallStatus.IN_PROGRESS,
                    durationMs = 0,
                )
            )

            var lastModel = initialModel
            var lastError: Throwable? = null
            var finished = false

            try {
                for (choosableModel in choosableModels) {
                    lastModel = choosableModel

                    try {
                        val client = providers[choosableModel.apiProvider]
                            ?: throw IllegalStateException("API provider ${choosableModel.apiProvider} not found")

                        val response = subTask(true, PerModelRequestTask(client, choosableModel)).get()
                        if (response != null) {
                            log.debug("模型 {} 调用成功", choosableModel.modelIdentifier)
                            recorder.finish(
                                auditId,
                                ModelCallEvent(
                                    taskName = taskName,
                                    model = choosableModel,
                                    callContext = request.callContext,
                                    status = ModelCallStatus.SUCCESS,
                                    durationMs = elapsedMillis(startNanos),
                                    response = response,
                                )
                            )
                            finished = true
                            return response
                        }
                    } catch (e: Exception) {
                        lastError = e
                        log.warn("模型 ${choosableModel.modelIdentifier} 调用失败，正在尝试下一个模型", e)
                    }
                }

                log.warn("所有模型调用失败，无法获取响应")
                val failure = ModelRequestFailed("All choosable model requests failed")
                recorder.finish(
                    auditId,
                    ModelCallEvent(
                        taskName = taskName,
                        model = lastModel,
                        callContext = request.callContext,
                        status = ModelCallStatus.FAILED,
                        durationMs = elapsedMillis(startNanos),
                        error = lastError ?: failure,
                    )
                )
                finished = true
                throw failure
            } finally {
                // 兜底：出现 Error 等未被上面路径覆盖的情况时也要留下最终状态
                if (!finished) {
                    recorder.finish(
                        auditId,
                        ModelCallEvent(
                            taskName = taskName,
                            model = lastModel,
                            callContext = request.callContext,
                            status = ModelCallStatus.FAILED,
                            durationMs = elapsedMillis(startNanos),
                            error = lastError,
                        )
                    )
                }
                MDC.remove("ModelRequestId")
            }
        }

        /**
         * PerModelRequestTask 负责单个候选模型的一次完整客户端调用。
         *
         * 客户端内部的重试仍由 ModelClientBase 负责；这里不再产生审计记录，
         * 审计粒度是外层的一次 LLM 任务。
         */
        inner class PerModelRequestTask(
            private val client: ModelClientBase,
            private val modelConfig: ModelConfig
        ) : ManagedTask<APIResponse>() {
            override fun invoke(): APIResponse {
                return client.getResponse(
                    modelConfig.modelIdentifier,
                    request.messageContext,
                    request.availableFunc,
                    request.formatClass,
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
        private fun elapsedMillis(startNanos: Long): Long {
            return (System.nanoTime() - startNanos) / 1_000_000
        }

        private val log: Logger = LoggerFactory.getLogger(ModelRequestHandlerImpl::class.java)
    }
}
