package org.maibot.mods;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.maibot.sdk.net.HttpRequestProcessor;

import java.nio.charset.StandardCharsets;

public class PingPongHandler extends HttpRequestProcessor {
    public PingPongHandler() {
        // 声明：这是一个处理GET请求的处理器，路径为"/ping"
        super(HttpMethod.GET, "/ping", PingPongHandler.class);
    }

    public HttpResponse handleRequest(FullHttpRequest req) {
        // 返回一个包含"pong"的HTTP响应
        var resp = new DefaultFullHttpResponse(
          req.protocolVersion(),
          HttpResponseStatus.OK,
          Unpooled.copiedBuffer("pong", StandardCharsets.UTF_8)
        );
        resp.headers().set("Content-Type", "text/plain; charset=UTF-8");
        return resp;
    }
}
