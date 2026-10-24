package org.maibot.core.model

/**
 * 一次模型调用失败的统一异常
 *
 * 由 [ModelCaller] 负责把超时、Future 异常包装成业务可读的错误，
 * 上层只关心成功或失败，不需要理解线程池细节。
 */
class ModelCallException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
