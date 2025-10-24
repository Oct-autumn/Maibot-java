package org.maibot.sdk.network;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HttpRequestProcessor {
    protected final Logger log;
    @Getter
    private final HttpMethod method;
    @Getter
    private final String path;


    public HttpRequestProcessor(HttpMethod method, String path, Logger logger) {
        this.method = method;
        this.path = path;
        this.log = logger;
    }

    public HttpRequestProcessor(HttpMethod method, String path, Class<?> loggerClass) {
        this(method, path, LoggerFactory.getLogger(loggerClass));
    }

    public void process(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        var resp = handleRequest(req);
        if (resp == null) {
            log.trace("无响应数据");
        } else {
            log.trace("发送响应 {}", resp.status().code());
            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @SuppressWarnings("RedundantThrows") // 抑制警告：声明的异常从不在任何方法实现中抛出
    abstract public HttpResponse handleRequest(FullHttpRequest req) throws Exception;
}
