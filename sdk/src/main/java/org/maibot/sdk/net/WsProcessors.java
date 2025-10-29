package org.maibot.sdk.net;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.Getter;
import org.maibot.sdk.exceptions.FatalError;

import java.util.ArrayList;
import java.util.List;

public class WsProcessors extends ChannelInboundHandlerAdapter {
    @Getter
    private final String         path;
    @Getter
    private final List<Class<?>> handlers;

    /**
     * @param path     WebSocket路径
     * @param handlers 处理器类列表
     */
    public WsProcessors(String path, List<Class<?>> handlers) {
        this.path = path;

        List<Class<?>> finalHandlers = new ArrayList<>();

        for (Class<?> handler : handlers) {
            if (handler == WebSocketServerProtocolHandler.class) {
                continue;
            }
            // 检查Handler类是否是ChannelHandler接口的实现类
            if (!ChannelHandler.class.isAssignableFrom(handler)) {
                throw new FatalError(
                  "Handler class %s must implement io.netty.channel.ChannelHandler or its subclass.",
                  handler.getName()
                );
            }
            finalHandlers.add(handler);
        }

        this.handlers = finalHandlers;
    }
}
