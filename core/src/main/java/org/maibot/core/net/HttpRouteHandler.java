package org.maibot.core.net;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.net.HttpRequestProcessor;
import org.maibot.sdk.net.HttpRouter;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

@Component
@ChannelHandler.Sharable
public class HttpRouteHandler extends HttpRouter {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(HttpRouteHandler.class);

    private final Map<String, HttpRequestProcessor> processors = new HashMap<>();

    public HttpRouteHandler() {
        super();
    }

    @Override
    public void registerProcessor(HttpRequestProcessor processor) {
        var method = processor.getMethod().toString();
        var path = processor.getPath();
        var key = method + " " + path;
        if (processors.containsKey(key)) {
            log.warn("已有HTTP请求处理器注册，覆盖旧的请求处理器: METHOD: {}, PATH: {}", method, path);
        }
        processors.put(key, processor);
        log.debug("注册HTTP请求处理器: METHOD: {}, PATH: {}", method, path);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        var method = req.method().toString();
        var parser = new QueryStringDecoder(req.uri());
        var path = parser.path();
        var key = method + " " + path;
        if (processors.containsKey(key)) {
            log.trace("找到 METHOD: {}, PATH: {} 的HTTP请求处理器，开始处理", method, path);
            try {
                processors.get(key).process(ctx, req);
            } catch (Throwable e) {
                log.error("处理 METHOD: {}, PATH: {} 的HTTP请求时发生异常: {}", method, path, e.getMessage(), e);
                var resp = new DefaultHttpResponse(
                  req.protocolVersion(),
                  HttpResponseStatus.INTERNAL_SERVER_ERROR
                );

                ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
            }
        } else {
            log.warn("未找到 METHOD:{}, PATH: {} 的HTTP请求处理器，返回404", method, path);
            var resp = new DefaultHttpResponse(
              req.protocolVersion(),
              HttpResponseStatus.NOT_FOUND
            );

            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
