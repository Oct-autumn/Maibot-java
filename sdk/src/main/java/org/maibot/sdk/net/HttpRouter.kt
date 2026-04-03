package org.maibot.sdk.net

import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.FullHttpRequest

abstract class HttpRouter : SimpleChannelInboundHandler<FullHttpRequest>() {
    abstract fun registerProcessor(processor: HttpRequestProcessor)
}
