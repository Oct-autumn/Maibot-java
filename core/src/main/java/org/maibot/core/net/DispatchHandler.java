package org.maibot.core.net;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.maibot.core.cdi.annotation.AutoInject;
import org.maibot.core.cdi.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ChannelHandler.Sharable
public class DispatchHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger log = LoggerFactory.getLogger(DispatchHandler.class);

    private final HttpDispatchHandler httpDispatchHandler;
    private final WsDispatchHandler wsDispatchHandler;

    @AutoInject
    public DispatchHandler(HttpDispatchHandler httpDispatchHandler, WsDispatchHandler wsDispatchHandler) {
        this.httpDispatchHandler = httpDispatchHandler;
        this.wsDispatchHandler = wsDispatchHandler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest request) {
            if (isWebSocketUpgrade(request)) {
                // WebSocket升级请求
                log.debug("收到WebSocket升级请求: URI：{}", request.uri());
                ctx.pipeline().addBefore("exceptionHandler", "wsUpgradeHandler", wsDispatchHandler);
            } else {
                // 普通HTTP请求
                log.debug("收到HTTP请求: METHOD: {}, URI: {}", request.method(), request.uri());
                ctx.pipeline().addBefore("exceptionHandler", "httpDispatchHandler", httpDispatchHandler);
            }
            ctx.fireChannelRead(request.retain());

            // 对于Ws，由于后续数据均为WebSocket帧，分发器失去作用
            // 对于Http，由于Http请求完成后即关闭连接，分发器不会二次利用
            // 所以移除分发器
            ctx.pipeline().remove(this);
        } else {
            // 其他消息类型
            // TODO: 支持裸Socket
            ctx.fireChannelRead(msg);
        }
    }

    private boolean isWebSocketUpgrade(HttpRequest request) {
        return request.method().equals(HttpMethod.GET)
                && request.headers().contains(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true)
                && request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true);
    }
}