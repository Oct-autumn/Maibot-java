package org.maibot.sdk.net;

import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;

public abstract class WsRouter extends SimpleChannelInboundHandler<FullHttpRequest> {
    abstract public void registerProcessor(WsProcessors wsProcessors);
}
