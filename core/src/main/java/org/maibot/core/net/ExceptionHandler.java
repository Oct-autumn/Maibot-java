package org.maibot.core.net;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.maibot.core.cdi.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ChannelHandler.Sharable
public class ExceptionHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ExceptionHandler.class);

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("处理消息时发生异常", cause);
        ctx.close();
    }
}
