package org.maibot.mods;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EchoHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(EchoHandler.class);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)
    throws Exception {
        // 仅处理文本消息
        if (msg instanceof TextWebSocketFrame textFrame) {
            String receivedText = textFrame.text();

            // 使用WsProcessor处理接收到的消息
            log.info("Received message: {}", receivedText);

            // 回显处理后的消息
            ctx.writeAndFlush(new TextWebSocketFrame(receivedText));
        } else {
            // 如果不是文本消息，直接传递给下一个Handler
            super.channelRead(ctx, msg);
        }
    }

}
