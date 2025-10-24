package org.maibot.sdk.network;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.Getter;

import java.util.List;

public class WsProcessors extends ChannelInboundHandlerAdapter {
    @Getter
    private final String path;
    @Getter
    private final List<ChannelHandler> handlers;

    public WsProcessors(String path, List<ChannelHandler> handlers) {
        this.path = path;
        this.handlers = handlers;

        // 确保第一个处理器是WebSocketServerProtocolHandler
        if (handlers.isEmpty() || !(handlers.getFirst() instanceof WebSocketServerProtocolHandler)) {
            // 第一个不是WebSocketServerProtocolHandler，检查用户是否已在列表中添加
            int tgtIdx = -1;
            for (int idx = 0; idx < handlers.size(); idx++) {
                ChannelHandler handler = handlers.get(idx);
                if (handler instanceof WebSocketServerProtocolHandler) {
                    tgtIdx = idx;
                    break;
                }
            }

            if (tgtIdx != -1) {
                // 找到则移动到第一个位置
                ChannelHandler handler = handlers.remove(tgtIdx);
                handlers.addFirst(handler);
            } else {
                // 否则添加一个新的WebSocketServerProtocolHandler到第一个位置
                handlers.addFirst(new WebSocketServerProtocolHandler(path));
            }
        }
    }
}
