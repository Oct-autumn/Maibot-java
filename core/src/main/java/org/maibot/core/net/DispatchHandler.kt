package org.maibot.core.net

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.*
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component
@Sharable
class DispatchHandler
@AutoInject constructor(
    private val httpRouteHandler: HttpRouteHandler, private val wsRouteHandler: WsRouteHandler
) : SimpleChannelInboundHandler<Any?>() {
    override fun channelRead0(ctx: ChannelHandlerContext, msg: Any?) {
        when (msg) {
            is FullHttpRequest -> ctx.pipeline().apply {
                if (isWebSocketUpgrade(msg)) {
                    // WebSocket升级请求
                    log.debug("收到WebSocket升级请求: URI：{}", msg.uri())
                    addBefore("exceptionHandler", "wsUpgradeHandler", wsRouteHandler)
                } else {
                    // 普通HTTP请求
                    log.debug("收到HTTP请求: METHOD: {}, URI: {}", msg.method(), msg.uri())
                    addBefore("exceptionHandler", "httpDispatchHandler", httpRouteHandler)
                }
                ctx.fireChannelRead(msg.retain())

                // 对于Ws，由于后续数据均为WebSocket帧，分发器失去作用
                // 对于Http，由于Http请求完成后即关闭连接，分发器不会二次利用
                // 所以移除分发器
                remove(this@DispatchHandler)
            }
            // 其他消息类型
            // TODO: 支持裸Socket

            else -> ctx.fireChannelRead(msg)
        }
    }

    private fun isWebSocketUpgrade(request: HttpRequest): Boolean {
        return request.method() == HttpMethod.GET
                && request.headers().contains(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true)
                && request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true)
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(DispatchHandler::class.java)
    }
}