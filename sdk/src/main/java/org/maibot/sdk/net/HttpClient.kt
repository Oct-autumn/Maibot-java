package org.maibot.sdk.net

import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import java.net.URI
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

object HttpClient {
    private val PROVIDER = AtomicReference<HttpClientProvider?>()

    fun registerProvider(provider: HttpClientProvider) {
        check(PROVIDER.compareAndSet(null, provider)) { "HttpClientProvider has already been registered" }
    }

    @JvmStatic
    @Suppress("unused")
    fun request(url: String, request: FullHttpRequest): CompletableFuture<FullHttpResponse?> {
        val provider = PROVIDER.get()
        val future = CompletableFuture<FullHttpResponse?>()

        if (provider == null) {
            future.completeExceptionally(IllegalStateException("No HttpClientProvider registered"))
            return future
        }
        val parsedUrl: URL
        try {
            parsedUrl = URI(url).toURL()
        } catch (e: Exception) {
            future.completeExceptionally(IllegalArgumentException("Unable to parse URL: $url", e))
            return future
        }
        return provider.request(parsedUrl, request)
    }
}
