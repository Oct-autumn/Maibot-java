package org.maibot.core.net;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.maibot.sdk.network.HttpRequestProcessor;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class HttpHandler extends SimpleChannelInboundHandler<DispatchObject.HttpDispatchReq> {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(HttpHandler.class);

    private final Map<String, HttpRequestProcessor> processors = new HashMap<>();

    public HttpHandler() {
        super();

        // 注册内置处理器
        //registerProcessor("/status", new StatusRequestProcessor());
    }

    public void registerProcessor(String path, HttpRequestProcessor processor) {
        if (processors.containsKey(path)) {
            log.warn("路径 {} 已有处理器注册，新的处理器将覆盖旧的处理器", path);
        }
        processors.put(path, processor);
        log.debug("注册HTTP请求处理器: {}", path);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DispatchObject.HttpDispatchReq msg) throws Exception {

    }
}
