package org.maibot.core.net;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import org.maibot.core.cdi.annotation.Component;
import org.maibot.core.net.http.PingPongHandler;
import org.maibot.sdk.network.HttpRequestProcessor;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

@Component
@ChannelHandler.Sharable
public class HttpDispatchHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(HttpDispatchHandler.class);

    private final Map<String, HttpRequestProcessor> processors = new HashMap<>();

    public HttpDispatchHandler() {
        super();

        // 注册内置处理器
        registerProcessor(new PingPongHandler());
    }

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
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        var method = req.method().toString();
        var parser = new QueryStringDecoder(req.uri());
        var path = parser.path();
        var key = method + " " + path;
        if (processors.containsKey(key)) {
            log.trace("找到 METHOD: {}, PATH: {} 的HTTP请求处理器，开始处理", method, path);
            try {
                processors.get(key).process(ctx, req);
            } catch (Exception e) {
                log.error("处理 METHOD: {}, PATH: {} 的HTTP请求时发生异常: {}", method, path, e.getMessage(), e);
                var resp = new DefaultHttpResponse(
                        req.protocolVersion(),
                        HttpResponseStatus.INTERNAL_SERVER_ERROR
                );

                ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
            } finally {
                req.release();
            }
        } else {
            log.warn("未找到 METHOD:{}, PATH: {} 的HTTP请求处理器，返回404", method, path);
            var resp = new DefaultHttpResponse(
                    req.protocolVersion(),
                    HttpResponseStatus.NOT_FOUND
            );

            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
            req.release();
        }
    }
}
