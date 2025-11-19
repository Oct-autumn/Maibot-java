package org.maibot.sdk.net;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;

import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

public final class HttpClient {
    private static final AtomicReference<HttpClientProvider> PROVIDER = new AtomicReference<>();

    public static void registerProvider(HttpClientProvider provider) {
        requireNonNull(provider);
        if (!PROVIDER.compareAndSet(null, provider)) {
            throw new IllegalStateException("HttpClientProvider has already been registered");
        }
    }

    public static CompletableFuture<FullHttpResponse> request(String url, FullHttpRequest request) {
        requireNonNull(url);
        requireNonNull(request);
        HttpClientProvider provider = PROVIDER.get();
        var future = new CompletableFuture<FullHttpResponse>();
        if (provider == null) {
            future.completeExceptionally(new IllegalStateException("No HttpClientProvider registered"));
            return future;
        }
        URL parsedUrl;
        try {
            parsedUrl = new URI(url).toURL();
        } catch (Exception e) {
            future.completeExceptionally(new IllegalArgumentException("Unable to parse URL: " + url, e));
            return future;
        }
        return provider.request(parsedUrl, request);
    }
}
