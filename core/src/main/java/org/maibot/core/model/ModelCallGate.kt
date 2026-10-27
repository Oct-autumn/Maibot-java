package org.maibot.core.model

import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import java.util.concurrent.Semaphore

/**
 * 模型调用串行门
 *
 * 本地模型（LM Studio / llama.cpp 等）通常只能处理一个推理请求。旧实现里
 * `DefaultModelCaller` 超时后底层 HTTP 请求仍在跑，观察线程重试就会叠出多个请求，
 * 本地模型卡在 prompt 预处理队列上。
 *
 * 这里用全局单飞：同一时刻只允许一个模型任务进入底层请求；超时后门不会提前释放，
 * 而是等底层 Future 真正结束后再放行，避免客户端超时造成请求堆积。
 */
@Component
class ModelCallGate
@AutoInject private constructor() {
    private val semaphore = Semaphore(1, true)

    /**
     * 尝试占用模型调用名额；已有请求在跑时立即返回 false，不排队、不新增 HTTP 请求。
     */
    fun tryAcquire(): Boolean {
        return semaphore.tryAcquire()
    }

    /**
     * 底层请求真正结束后释放名额。
     */
    fun release() {
        semaphore.release()
    }
}
