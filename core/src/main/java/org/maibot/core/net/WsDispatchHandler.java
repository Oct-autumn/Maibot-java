package org.maibot.core.net;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.maibot.core.cdi.annotation.AutoInject;
import org.maibot.core.cdi.annotation.Component;
import org.maibot.sdk.network.WsProcessors;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

@Component
@ChannelHandler.Sharable
public class WsDispatchHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(WsDispatchHandler.class);

    private final ActiveWsManager activeWsManager;

    private final Map<String, WsProcessors> processors = new HashMap<>();

    @AutoInject
    public WsDispatchHandler(ActiveWsManager activeWsManager) {
        super();
        this.activeWsManager = activeWsManager;
    }

    public void registerProcessor(WsProcessors processor) {
        var path = processor.getPath();
        if (processors.containsKey(path)) {
            log.warn("已有WS处理器注册，覆盖旧的处理器: PATH: {}", path);
        }
        processors.put(path, processor);
        log.debug("注册WS处理器: PATH: {}", path);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        var parser = new QueryStringDecoder(req.uri());
        var path = parser.path();
        if (processors.containsKey(path)) {
            log.trace("找到 PATH: {} 的WS处理器，开始处理", path);

            activeWsManager.addConnection(path, ctx);

            for (var handler : processors.get(path).getHandlers()) {
                String uniqueHandlerName = handler.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(handler));
                ctx.pipeline().addBefore("exceptionHandler", uniqueHandlerName, handler);
            }

            ctx.fireChannelRead(req);

            ctx.pipeline().remove(this); // 移除调度处理器，避免重复处理
        } else {
            log.warn("未找到 PATH: {} 的WS处理器，返回404", path);
            var resp = new DefaultHttpResponse(
                    req.protocolVersion(),
                    HttpResponseStatus.NOT_FOUND
            );

            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
