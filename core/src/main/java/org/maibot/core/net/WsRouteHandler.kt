package org.maibot.core.net

import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import org.maibot.core.ioc.Instance
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.net.WsProcessors
import org.maibot.sdk.net.WsRouter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component
@ChannelHandler.Sharable
class WsRouteHandler
@AutoInject private constructor(
    private val activeWsManager: ActiveWsManager
) : WsRouter() {
    private val processors = HashMap<String, WsProcessors>()

    override fun registerProcessor(wsProcessors: WsProcessors) {
        val path = wsProcessors.path

        processors[path]?.let {
            log.warn("已有WS处理器注册，覆盖旧的处理器: PATH: {}", path)
        }

        processors[path] = wsProcessors
        log.debug("注册WS处理器: PATH: {}", path)
    }

    override fun channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest) {
        val parser = QueryStringDecoder(req.uri())
        val path = parser.path()

        processors[path]?.let { handlerList ->
            log.trace("收到WS请求: PATH: {}", path)

            activeWsManager.addConnection(path, ctx)

            ctx.pipeline().apply {
                // 添加WebSocket协议处理器
                val handler: ChannelHandler = WebSocketServerProtocolHandler(path)
                val uniqueHandlerName = "${handler.javaClass.getSimpleName()}@${System.identityHashCode(handler)}"
                addBefore("exceptionHandler", uniqueHandlerName, handler)

                // 添加用户自定义的处理器
                handlerList.handlers.forEach { handler ->
                    val handlerInst: ChannelHandler
                    try {
                        handlerInst = Instance.get(handler) as ChannelHandler
                    } catch (e: Exception) {
                        log.error("无法实例化WS处理器: PATH: {}, Handler: {}", path, handler.getSimpleName(), e)
                        return@forEach
                    }

                    addBefore(
                        "exceptionHandler",
                        "${handler.getSimpleName()}@${System.identityHashCode(handlerInst)}",
                        handlerInst
                    )
                }

                ctx.fireChannelRead(req.retain()) // 此处使用retain()方法增加引用计数，确保请求对象在后续处理器中仍然有效

                remove(this@WsRouteHandler) // 移除调度处理器，避免重复处理
            }
        } ?: run {
            log.warn("收到WS请求: PATH: {}，但未找到对应的处理器，返回404", path)
            ctx.writeAndFlush(DefaultHttpResponse(req.protocolVersion(), HttpResponseStatus.NOT_FOUND))
                .addListener(ChannelFutureListener.CLOSE)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(WsRouteHandler::class.java)
    }
}
