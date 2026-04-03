package org.maibot.core.net.client

import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import org.maibot.core.net.ExceptionHandler
import org.maibot.core.util.TaskExecuteServiceImpl
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.DestroyableComponent
import org.maibot.sdk.net.HttpClientProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer

@Component
class HttpClientProviderImpl
@AutoInject internal constructor(
    taskExecutorService: TaskExecuteServiceImpl,
    exceptionHandler: ExceptionHandler
) : DestroyableComponent, HttpClientProvider {
    private val bootstrap: Bootstrap = Bootstrap()
    private val eventLoopGroup = SingleThreadIoEventLoop(
        null,
        taskExecutorService.executor()
    ) { tae -> NioIoHandler.newFactory().newHandler(tae) }

    init {
        bootstrap.group(eventLoopGroup)
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline()
                        // HTTP编解码器 与 HTTP消息聚合器（最大消息长度为50MB）
                        .addLast("httpCodec", HttpClientCodec())
                        .addLast("httpAggregator", HttpObjectAggregator(1024 * 1024 * 50))
                        // 异常处理兜底
                        .addLast("exceptionHandler", exceptionHandler)
                }
            })
    }

    override fun request(url: URL, request: FullHttpRequest): CompletableFuture<FullHttpResponse?> {
        val res = CompletableFuture<FullHttpResponse?>()
        try {
            val uri = url.toURI()
            val host = uri.host
            val port = if (uri.port == -1) 80 else uri.port

            bootstrap.connect(host, port).addListener(ChannelFutureListener { connectFuture ->
                if (!connectFuture.isSuccess) {
                    res.completeExceptionally(connectFuture.cause())
                    return@ChannelFutureListener
                }
                val channel = connectFuture.channel()
                // 动态添加响应处理器
                channel.pipeline().addBefore("exceptionHandler", "responseHandler", HttpResponseHandler(res))

                // 将CompletableFuture与Channel关闭关联
                // 无论请求成功还是失败，只要CompletableFuture完成，就检查是否需要关闭Channel
                res.whenComplete(BiConsumer { _: FullHttpResponse?, _: Throwable? ->
                    if (channel.isOpen) {
                        channel.close()
                    }
                })

                // 发送HTTP请求
                channel.writeAndFlush(request)
            })
        } catch (e: Exception) {
            res.completeExceptionally(e)
        }
        return res
    }

    override fun preDestroy() {
        shutdown()
    }

    fun shutdown() {
        eventLoopGroup.shutdownGracefully()
    }

    private class HttpResponseHandler(private val future: CompletableFuture<FullHttpResponse?>) :
        SimpleChannelInboundHandler<FullHttpResponse>() {
        override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse) {
            // 复制一份响应（避免 ByteBuf 被释放）
            val copy = msg.copy()
            future.complete(copy)
            ctx.close()
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
            future.completeExceptionally(cause)
            ctx.close()
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(HttpClientProviderImpl::class.java)
    }
}
