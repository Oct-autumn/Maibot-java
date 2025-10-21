package org.maibot.sdk.network;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;

public abstract class HttpRequestProcessor {
    private final HttpMethod method;
    private final String uriPath;
    protected final Logger log;


    public HttpRequestProcessor(HttpMethod method, String uriPath, Logger logger) {
        this.method = method;
        this.uriPath = uriPath;
        this.log = logger;
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

    public HttpMethod getMethod() {
        return this.method;
    }

    public String getUriPath() {
        return this.uriPath;
    }

    @SuppressWarnings("RedundantThrows") // 抑制警告：声明的异常从不在任何方法实现中抛出
    abstract public HttpResponse handleRequest(FullHttpRequest req) throws Exception;
}
