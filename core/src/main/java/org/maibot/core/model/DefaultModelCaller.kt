package org.maibot.core.model

import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.model.APIResponse
import org.maibot.sdk.model.ModelCallRequest
import org.maibot.sdk.model.ModelManager
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 默认实现：从 [ModelManager] 取任务 handler 并等待结果
 */
@Component
class DefaultModelCaller
@AutoInject private constructor(
    private val modelManager: ModelManager,
) : ModelCaller {
    override fun call(taskName: String, request: ModelCallRequest, timeoutSec: Long): APIResponse {
        val handler = modelManager[taskName]
            ?: throw ModelCallException("模型任务未注册: $taskName")

        return try {
            handler.getResponse(request).get(timeoutSec, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
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
}
