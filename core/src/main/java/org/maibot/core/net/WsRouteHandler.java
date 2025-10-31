package org.maibot.core.net;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.maibot.core.ioc.Instance;
import org.maibot.sdk.ioc.AutoInject;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.net.WsProcessors;
import org.maibot.sdk.net.WsRouter;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

@Component
@ChannelHandler.Sharable
public class WsRouteHandler extends WsRouter {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(WsRouteHandler.class);

    private final ActiveWsManager activeWsManager;

    private final Map<String, WsProcessors> processors = new HashMap<>();

    @AutoInject
    public WsRouteHandler(ActiveWsManager activeWsManager) {
        super();
        this.activeWsManager = activeWsManager;
    }

    @Override
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

            var pipeline = ctx.pipeline();

            {// 添加WebSocket协议处理器
                ChannelHandler handler = new WebSocketServerProtocolHandler(path);
                String uniqueHandlerName = String.format(
                  "%s@%s",
                  handler.getClass().getSimpleName(),
                  System.identityHashCode(handler)
                );
                pipeline.addBefore("exceptionHandler", uniqueHandlerName, handler);
            }

            for (var handler : processors.get(path).getHandlers()) {
                ChannelHandler handlerInst;
                try {
                    handlerInst = (ChannelHandler) Instance.get(handler);
                } catch (Exception e) {
                    log.error("无法实例化WS处理器: PATH: {}, Handler: {}", path, handler.getSimpleName(), e);
                    continue;
                }
                String uniqueHandlerName = String.format(
                  "%s@%s",
                  handler.getSimpleName(),
                  System.identityHashCode(handlerInst)
                );
                ctx.pipeline().addBefore("exceptionHandler", uniqueHandlerName, handlerInst);
            }

            ctx.fireChannelRead(req.retain());

            ctx.pipeline().remove(this); // 移除调度处理器，避免重复处理
        } else {
            log.warn("未找到 PATH: {} 的WS处理器，返回404", path);
            var resp = new DefaultHttpResponse(req.protocolVersion(), HttpResponseStatus.NOT_FOUND);

            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
