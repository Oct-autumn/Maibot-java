package org.maibot.sdk.net

import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.FullHttpRequest

abstract class WsRouter : SimpleChannelInboundHandler<FullHttpRequest>() {
    abstract fun registerProcessor(wsProcessors: WsProcessors)
}
