package org.maibot.sdk.net

import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class HttpRequestProcessor(
    @JvmField val method: HttpMethod,
    @JvmField val path: String,
    protected val log: Logger
) {
    constructor(method: HttpMethod, path: String, loggerClass: Class<*>) : this(
        method,
        path,
        LoggerFactory.getLogger(loggerClass)
    )

    @Throws(Exception::class)
    fun process(ctx: ChannelHandlerContext, req: FullHttpRequest) {
        handleRequest(req)?.let { resp ->
            log.trace("发送响应 {}", resp.status().code())
            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE)
        } ?: log.trace("无响应数据")
    }

    /**
     * 处理HTTP请求并生成响应
     *
     * 注意：实现类需要确保无状态，以便在多线程环境中安全使用。
     */
    @Throws(Exception::class)  // 抑制警告：声明的异常从不在任何方法实现中抛出
    abstract fun handleRequest(req: FullHttpRequest): HttpResponse?
}
