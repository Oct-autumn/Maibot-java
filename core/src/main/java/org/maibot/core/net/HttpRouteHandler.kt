package org.maibot.core.net

import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.net.HttpRequestProcessor
import org.maibot.sdk.net.HttpRouter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component
@Sharable // 无状态处理器，可以在多个ChannelPipeline中共享
class HttpRouteHandler : HttpRouter() {
    private val processors = HashMap<String, HttpRequestProcessor>()

    override fun registerProcessor(processor: HttpRequestProcessor) {
        val method = processor.method.toString()
        val path = processor.path
        val key = "$method $path"

        processors[key]?.let {
            log.warn("已有HTTP请求处理器注册，覆盖旧的请求处理器: PATH: {}", key)
        }
        processors[key] = processor
        log.debug("注册HTTP请求处理器: PATH: {}", key)
    }

    override fun channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest) {
        val method = req.method().toString()
        val parser = QueryStringDecoder(req.uri())
        val path = parser.path()
        val key = "$method $path"

        processors[key]?.let {
            log.trace("收到HTTP请求: PATH: {}", key)
            try {
                processors[key]!!.process(ctx, req)
            } catch (e: Throwable) {
                log.error("处理 PATH: {} 的HTTP请求时发生异常", key, e)
                val resp = DefaultHttpResponse(
                    req.protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR
                )

                ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE)
            }
        } ?: run {
            log.warn("未找到HTTP请求处理器: PATH: {}，但未找到对应的处理器，返回404", key)
            ctx.writeAndFlush(DefaultHttpResponse(req.protocolVersion(), HttpResponseStatus.NOT_FOUND))
                .addListener(ChannelFutureListener.CLOSE)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(HttpRouteHandler::class.java)
    }
}
