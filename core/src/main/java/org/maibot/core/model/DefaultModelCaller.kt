package org.maibot.core.model

import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.model.APIResponse
import org.maibot.sdk.model.ModelCallRequest
import org.maibot.sdk.model.ModelManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 默认实现：从 [ModelManager] 取任务 handler 并等待结果
 *
 * 通过 [ModelCallGate] 做单飞：本地模型同时只接收一个请求；前一个请求超时但
 * 底层还没结束时，后续调用会快速失败，由观察线程退避后重试，而不是继续往
 * LM Studio 堆请求。
 */
@Component
class DefaultModelCaller
@AutoInject private constructor(
    private val modelManager: ModelManager,
    private val modelCallGate: ModelCallGate,
) : ModelCaller {
    override fun call(taskName: String, request: ModelCallRequest, timeoutSec: Long): APIResponse {
        val handler = modelManager[taskName]
            ?: throw ModelCallException("模型任务未注册: $taskName")

        if (!modelCallGate.tryAcquire()) {
            throw ModelCallException("模型正在处理上一个请求，跳过本次调用（task: $taskName）")
        }

        val future = try {
            handler.getResponse(request)
        } catch (e: Throwable) {
            modelCallGate.release()
            throw ModelCallException("模型调用提交失败（task: $taskName）: ${e.message}", e)
        }

        // 门要等底层请求真正结束才释放；调用方超时只代表等待超时，不代表请求结束。
        future.whenComplete { _, _ -> modelCallGate.release() }

        return try {
            future.get(timeoutSec, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            log.warn("模型调用等待超时（task: {}, {}s），底层请求仍在运行，暂不发起新请求", taskName, timeoutSec)
            throw ModelCallException("模型调用超时（task: $taskName, ${timeoutSec}s）", e)
        } catch (e: ExecutionException) {
            val cause = e.cause ?: e
            throw ModelCallException("模型调用失败（task: $taskName）: ${cause.message}", cause)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ModelCallException("模型调用被中断（task: $taskName）", e)
        } catch (e: RuntimeException) {
            // ModelManager 取 handler 时的配置错误等，统一成调用异常
            throw ModelCallException("模型调用失败（task: $taskName）: ${e.message}", e)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(DefaultModelCaller::class.java)
    }
}
