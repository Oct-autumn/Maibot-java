package org.maibot.core.net.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import org.maibot.core.net.ExceptionHandler;
import org.maibot.core.util.TaskExecuteServiceImpl;
import org.maibot.sdk.ioc.AutoInject;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.ioc.DestroyableComponent;
import org.maibot.sdk.net.HttpClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

@Component
public class HttpClientProviderImpl implements DestroyableComponent, HttpClientProvider {
    private final static Logger log = LoggerFactory.getLogger(HttpClientProviderImpl.class);

    private final Bootstrap      bootstrap;
    private final EventLoopGroup eventLoopGroup;

    @AutoInject
    HttpClientProviderImpl(TaskExecuteServiceImpl taskExecutorService, ExceptionHandler exceptionHandler) {
        this.eventLoopGroup = new SingleThreadIoEventLoop(
          null,
          taskExecutorService.executor(),
          tae -> NioIoHandler.newFactory().newHandler(tae)
        );

        this.bootstrap = new Bootstrap()
          .group(eventLoopGroup)
          .channel(NioSocketChannel.class)
          .option(ChannelOption.SO_KEEPALIVE, true)
          .handler(new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(SocketChannel ch) {
                  ch.pipeline()
                    .addLast("httpCodec", new HttpClientCodec())
                    .addLast("httpAggregator", new HttpObjectAggregator(1024 * 1024 * 50))
                    .addLast("exceptionHandler", exceptionHandler);
              }
          });
    }

    @Override
    public CompletableFuture<FullHttpResponse> request(URL url, FullHttpRequest request) {
        CompletableFuture<FullHttpResponse> future = new CompletableFuture<>();
        try {
            URI uri = url.toURI();
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 80 : uri.getPort();

            bootstrap.connect(host, port).addListener((ChannelFutureListener) connectFuture -> {
                if (!connectFuture.isSuccess()) {
                    future.completeExceptionally(connectFuture.cause());
                    return;
                }

                Channel channel = connectFuture.channel();
                // 动态添加响应处理器
                channel.pipeline().addBefore("exceptionHandler", "responseHandler", new HttpResponseHandler(future));

                // 将CompletableFuture与Channel关闭关联
                future.whenComplete((resp, ex) -> {
                    if (future.isCancelled() && channel.isOpen()) {
                        channel.close();
                    }
                });

                channel.writeAndFlush(request);
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public void preDestroy() {
        shutdown();
    }

    public void shutdown() {
        eventLoopGroup.shutdownGracefully();
    }

    private static class HttpResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
        private final CompletableFuture<FullHttpResponse> future;

        HttpResponseHandler(CompletableFuture<FullHttpResponse> future) {
            this.future = future;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
            // 复制一份响应（避免 ByteBuf 被释放）
            FullHttpResponse copy = msg.copy();
            future.complete(copy);
            ctx.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            future.completeExceptionally(cause);
            ctx.close();
        }
    }
}
