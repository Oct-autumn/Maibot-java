package org.maibot.sdk.net;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;

import java.net.URL;
import java.util.concurrent.CompletableFuture;

public interface HttpClientProvider {
    CompletableFuture<FullHttpResponse> request(URL url, FullHttpRequest request);
}
