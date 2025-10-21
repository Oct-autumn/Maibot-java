package org.maibot.core.net;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import org.maibot.core.cdi.annotation.Component;
import org.maibot.core.net.http.PingPongHandler;
import org.maibot.sdk.network.DispatchObject;
import org.maibot.sdk.network.HttpRequestProcessor;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

@Component
@ChannelHandler.Sharable
public class HttpHandler extends SimpleChannelInboundHandler<DispatchObject.HttpDispatchReq> {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(HttpHandler.class);

    private final Map<String, HttpRequestProcessor> processors = new HashMap<>();

    public HttpHandler() {
        super();

        // 注册内置处理器
        registerProcessor(new PingPongHandler());
    }

    public void registerProcessor(HttpRequestProcessor processor) {
        var method = processor.getMethod().toString();
        var uriPath = processor.getUriPath();
        var key = method + " " + uriPath;
        if (processors.containsKey(key)) {
            log.warn("已有处理器注册，覆盖旧的处理器: METHOD: {}, URI: {}", method, uriPath);
        }
        processors.put(key, processor);
        log.debug("注册HTTP请求处理器: METHOD: {}, URI: {}", method, uriPath);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DispatchObject.HttpDispatchReq req) throws Exception {
        var reqInst = req.req();
        var method = reqInst.method().toString();
        var uriPath = reqInst.uri();
        var key = method + " " + uriPath;
        if (processors.containsKey(key)) {
            log.trace("找到 METHOD: {}, URI: {} 的处理器，开始处理请求", method, uriPath);
            processors.get(key).process(ctx, req.req());
        } else {
            log.warn("未找到 METHOD:{}, URI: {} 的处理器，返回404", method, uriPath);
            var resp = new DefaultHttpResponse(
                    req.req().protocolVersion(),
                    HttpResponseStatus.NOT_FOUND
            );

            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
