package org.maibot.sdk.net

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import org.maibot.sdk.exceptions.FatalError

class WsProcessors(val path: String, handlers: MutableList<Class<*>>) : ChannelInboundHandlerAdapter() {
    val handlers: MutableList<Class<*>>

    /**
     * @param path     WebSocket路径
     * @param handlers 处理器类列表
     */
    init {
        val finalHandlers = ArrayList<Class<*>>()

        for (handler in handlers) {
            if (handler == WebSocketServerProtocolHandler::class.java) {
                continue
            }
            // 检查Handler类是否是ChannelHandler接口的实现类
            if (!ChannelHandler::class.java.isAssignableFrom(handler)) {
                throw FatalError(
                    "Handler class ${handler.getName()} must implement io.netty.channel.ChannelHandler or its subclass."
                )
            }

            finalHandlers.add(handler)
        }

        this.handlers = finalHandlers
    }
}
