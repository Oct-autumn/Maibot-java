package org.maibot.core.event

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandler

abstract class EventHandler : ChannelInboundHandler {
    @Throws(Exception::class)
    override fun channelRegistered(ctx: ChannelHandlerContext) {
        ctx.fireChannelRegistered()
    }

    @Throws(Exception::class)
    override fun channelUnregistered(ctx: ChannelHandlerContext?) {
    }

    @Throws(Exception::class)
    override fun channelActive(ctx: ChannelHandlerContext?) {
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext?) {
    }

    @Throws(Exception::class)
    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
    }

    @Throws(Exception::class)
    override fun channelReadComplete(ctx: ChannelHandlerContext?) {
    }

    @Throws(Exception::class)
    override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
    }

    @Throws(Exception::class)
    override fun channelWritabilityChanged(ctx: ChannelHandlerContext?) {
    }

    @Throws(Exception::class)
    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
    }

    @Throws(Exception::class)
    override fun handlerAdded(ctx: ChannelHandlerContext?) {
    }

    @Throws(Exception::class)
    override fun handlerRemoved(ctx: ChannelHandlerContext?) {
    }
}