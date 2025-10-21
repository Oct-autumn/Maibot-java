package org.maibot.core.net.http;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.maibot.sdk.network.HttpRequestProcessor;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class PingPongHandler extends HttpRequestProcessor {
    public PingPongHandler() {
        super(HttpMethod.GET, "/ping", LoggerFactory.getLogger(PingPongHandler.class));
    }

    public HttpResponse handleRequest(FullHttpRequest req) {
        var resp = new DefaultFullHttpResponse(
                req.protocolVersion(),
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer("pong", StandardCharsets.UTF_8)
        );
        resp.headers().set("Content-Type", "text/plain; charset=UTF-8");
        return resp;
    }
}
