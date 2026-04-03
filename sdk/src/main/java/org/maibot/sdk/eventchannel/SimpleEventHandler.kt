package org.maibot.sdk.eventchannel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.internal.TypeParameterMatcher;

public abstract class SimpleEventHandler<T> extends ChannelInboundHandlerAdapter {
    private final TypeParameterMatcher evtTypeToCatch;

    protected SimpleEventHandler() {
        this.evtTypeToCatch = TypeParameterMatcher.find(this, SimpleEventHandler.class, "T");
    }

    /**
     * @param evtType 需要捕获的事件类型
     */
    protected SimpleEventHandler(Class<? extends T> evtType) {
        this.evtTypeToCatch = TypeParameterMatcher.get(evtType);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
    throws Exception {
        if (evtTypeToCatch.match(evt)) {
            @SuppressWarnings("unchecked")
            T typedEvt = (T) evt;
            var doRefire = handleEvent(ctx, typedEvt);

            if (doRefire) {
                super.userEventTriggered(ctx, evt);
            }
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    /**
     * 处理事件
     *
     * @param ctx 处理上下文
     * @param evt 事件对象
     * @return 是否不需要重新触发事件，返回 true 则表示不需要重新触发，返回 false 则表示需要重新触发
     */
    protected abstract boolean handleEvent(ChannelHandlerContext ctx, T evt)
    throws Exception;
}
