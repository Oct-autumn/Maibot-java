package org.maibot.sdk.network;

import io.netty.handler.codec.http.FullHttpRequest;

public class DispatchObject {
    public record WsDispatchReq(FullHttpRequest req) {
    }

    public record HttpDispatchReq(FullHttpRequest req) {
    }

    private DispatchObject() {
    }
}
