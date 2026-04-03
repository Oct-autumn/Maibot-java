package org.maibot.sdk.eventchannel

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.internal.TypeParameterMatcher

abstract class SimpleEventHandler<T> : ChannelInboundHandlerAdapter {
    /**
     * 需要捕获的事件类型匹配器
     *
     * 使用匹配器的好处是可以在运行时动态判断事件类型，而不需要在编译时确定具体的事件类型，从而提供了更大的灵活性和可扩展性。
     */
    private val evtTypeToCatch: TypeParameterMatcher

    protected constructor() {
        this.evtTypeToCatch = TypeParameterMatcher.find(this, SimpleEventHandler::class.java, "T")
    }

    /**
     * @param evtType 需要捕获的事件类型
     */
    protected constructor(evtType: Class<out T>) {
        this.evtTypeToCatch = TypeParameterMatcher.get(evtType)
    }

    @Throws(Exception::class)
    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evtTypeToCatch.match(evt)) {
            @Suppress("UNCHECKED_CAST")
            val typedEvt = evt as T

            if (handleEvent(ctx, typedEvt)) {
                super.userEventTriggered(ctx, evt)
            }
        } else {
            ctx.fireUserEventTriggered(evt)
        }
    }

    /**
     * 处理事件
     * 
     * @param ctx 处理上下文
     * @param evt 事件对象
     * @return 是否不需要重新触发事件，返回 true 则表示不需要重新触发，返回 false 则表示需要重新触发
     */
    @Throws(Exception::class)
    protected abstract fun handleEvent(ctx: ChannelHandlerContext, evt: T): Boolean
}
