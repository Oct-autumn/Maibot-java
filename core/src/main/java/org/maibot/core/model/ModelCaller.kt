package org.maibot.core.model

import org.maibot.sdk.model.APIResponse
import org.maibot.sdk.model.ModelCallRequest
import org.maibot.sdk.model.ModelManager

/**
 * 模型调用器
 *
 * 统一处理：按任务名找 handler、超时等待、异常归一化。
 * 发言生成与后续 ObserverAI 都依赖这个接口，而不是直接依赖 [ModelManager]。
 */
interface ModelCaller {
    /**
     * 调用一次模型
     *
     * @param taskName   模型任务名
     * @param request    调用请求
     * @param timeoutSec 等待超时（秒）
     * @throws ModelCallException 任务未注册、超时或调用失败
     */
    @Throws(ModelCallException::class)
    fun call(taskName: String, request: ModelCallRequest, timeoutSec: Long): APIResponse
}
