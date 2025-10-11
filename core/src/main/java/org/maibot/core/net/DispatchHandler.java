package org.maibot.core.net;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DispatchHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger log = LoggerFactory.getLogger(DispatchHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest request) {
            if (isWebSocketUpgrade(request)) {
                // WebSocket升级请求
                log.debug("收到WebSocket升级请求: URI：{}", request.uri());
                ctx.fireChannelRead(new DispatchObject.WsDispatchReq(request));
            } else {
                // 普通HTTP请求，交给HttpHandler处理
                log.debug("收到HTTP请求: METHOD: {}, URI: {}", request.method(), request.uri());
                ctx.fireChannelRead(new DispatchObject.HttpDispatchReq(request));
            }
        } else {
            // 其他消息类型
            // TODO: 支持裸Socket
            log.warn("收到未知类型消息: {}", msg.getClass().getName());
        }
    }

    private boolean isWebSocketUpgrade(HttpRequest request) {
        return request.method().equals(HttpMethod.GET)
                && request.headers().contains(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true)
                && request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true);
    }
}