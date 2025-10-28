package org.maibot.core.net;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.maibot.core.cdi.annotation.AutoInject;
import org.maibot.core.cdi.annotation.Component;
import org.maibot.core.cdi.annotation.Value;
import org.maibot.core.config.MainConfig;
import org.maibot.core.util.TaskExecutorService;
import org.maibot.sdk.exceptions.FatalError;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.net.BindException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Component
public class InnerServer {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(InnerServer.class);

    private final ServerBootstrap     bootstrap;
    private final TaskExecutorService taskExecutorService;

    private final MainConfig.Network conf;

    private IoEventLoopGroup bossGroup;
    private IoEventLoopGroup workerGroup;

    @AutoInject
    public InnerServer(
      @Value("${network}") MainConfig.Network conf,
      TaskExecutorService taskExecutorService,
      DispatchHandler dispatchHandler,
      ExceptionHandler exceptionHandler
    ) {
        this.bootstrap = new ServerBootstrap();
        bootstrap.channel(NioServerSocketChannel.class)
                 .childHandler(
                   new ChannelInitializer<SocketChannel>() {
                       @Override
                       protected void initChannel(SocketChannel ch) {
                           ChannelPipeline pipeline = ch.pipeline();

                           MDC.put("connId", Integer.toHexString(System.identityHashCode(ch)));

                           // HTTP编解码器 与 HTTP消息聚合器（最大消息长度为5MB）
                           pipeline.addLast("httpCodec", new HttpServerCodec());
                           pipeline.addLast("httpAggregator", new HttpObjectAggregator(1024 * 1024 * 5));
                           // 分发器
                           pipeline.addLast("dispatcher", dispatchHandler);
                           // 异常处理兜底
                           pipeline.addLast("exceptionHandler", exceptionHandler);
                       }
                   }
                 );

        this.taskExecutorService = taskExecutorService;
        this.conf = conf;
    }

    public void run() {
        // 使用线程池，创建一个单线程的bossGroup和多线程的workerGroup
        this.bossGroup = new MultiThreadIoEventLoopGroup(
          1,
          this.taskExecutorService.getExecutor(),
          tae -> NioIoHandler.newFactory().newHandler(tae)
        );

        this.workerGroup = new MultiThreadIoEventLoopGroup(
          2,
          this.taskExecutorService.getExecutor(),
          tae -> NioIoHandler.newFactory().newHandler(tae)
        );


        this.bootstrap.group(bossGroup, workerGroup);
        this.bootstrap.localAddress(this.conf.host, this.conf.port);

        var bindFuture = new CompletableFuture<Channel>();
        this.bootstrap.bind().addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                Throwable cause = future.cause();
                bindFuture.completeExceptionally(cause);
            } else {
                bindFuture.complete(future.channel());
            }
        });

        try {
            var channel = bindFuture.join();
            log.info("网络服务启动成功，监听地址: {}", channel.localAddress());
            channel.closeFuture().sync();
        } catch (CancellationException e) {
            log.warn("网络服务绑定过程中被取消");
            throw new FatalError("Failed to start InnerServer", e);
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BindException) {
                log.warn("网络服务绑定端口失败，端口 {} 可能已被占用", this.conf.port);
                throw new FatalError("Failed to start InnerServer due to port binding failure", cause);
            } else {
                log.warn("网络服务绑定过程中发生异常");
                throw new FatalError("Failed to start InnerServer", cause);
            }
        } catch (InterruptedException e) {
            log.warn("网络服务运行中断");
            throw new FatalError("InnerServer interrupted", e);
        } finally {
            this.shutdown();
        }
    }

    public void shutdown() {
        try {
            if (this.bossGroup != null) {
                this.bossGroup.shutdownGracefully().sync();
            }
            if (this.workerGroup != null) {
                this.workerGroup.shutdownGracefully().sync();
            }
        } catch (Exception e) {
            log.error("关闭网络服务时发生错误", e);
        }
    }
}
