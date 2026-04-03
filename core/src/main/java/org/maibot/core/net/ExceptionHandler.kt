package org.maibot.core.net

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.maibot.sdk.ioc.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component
@Sharable // 无状态的处理器可以被多个Channel共享
class ExceptionHandler : ChannelInboundHandlerAdapter() {
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
        log.warn("处理消息时发生异常", cause)
        ctx.close()
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ExceptionHandler::class.java)
    }
}
