package org.maibot.core.net;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.maibot.core.cdi.annotation.Component;
import org.maibot.sdk.network.DispatchObject;

@Component
@ChannelHandler.Sharable
public class WsHandler extends SimpleChannelInboundHandler<DispatchObject.WsDispatchReq> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DispatchObject.WsDispatchReq msg) throws Exception {

    }
}
