package org.maibot.sdk.net

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import org.maibot.sdk.exceptions.FatalError

/**
 * WebSocket 处理器集合
 *
 * 描述某个 WS 路径上要装配的处理器链，以及该路径对应的平台标识。
 * 平台标识用于 Core 把出站消息（[org.maibot.sdk.msg.OutgoingMessage]）路由到正确的连接。
 *
 * @param path     WebSocket 路径
 * @param handlers 处理器类列表（可同时包含入站解码/构造处理器与出站编码处理器）
 * @param platform 该路径所属的平台标识，例如 `qq`
 */
class WsProcessors(
    val path: String,
    handlers: MutableList<Class<*>>,
    val platform: String
) : ChannelInboundHandlerAdapter() {
    val handlers: MutableList<Class<*>>

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
