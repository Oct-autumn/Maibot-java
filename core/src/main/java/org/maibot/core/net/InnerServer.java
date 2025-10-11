package org.maibot.core.net;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.IoEventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import org.maibot.core.config.MainConfig;
import org.maibot.core.cdi.annotation.AutoInject;
import org.maibot.core.cdi.annotation.Component;
import org.maibot.core.cdi.annotation.Value;
import org.maibot.core.util.TaskExecutorService;
import org.slf4j.Logger;

@Component
public class InnerServer {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(InnerServer.class);

    private final ServerBootstrap bootstrap;
    private final TaskExecutorService taskExecutorService;

    private IoEventLoopGroup bossGroup;
    private IoEventLoopGroup workerGroup;

    @AutoInject
    public InnerServer(@Value("${network}") MainConfig.Network conf, TaskExecutorService taskExecutorService) {
        this.bootstrap = new ServerBootstrap();
        bootstrap.channel(NioServerSocketChannel.class)
                .childHandler(
                        new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ChannelPipeline pipeline = ch.pipeline();

                                // HTTP编解码器 与 HTTP消息聚合器（最大消息长度为5MB）
                                pipeline.addLast("httpDecoder", new HttpRequestDecoder());
                                pipeline.addLast("httpEncoder", new HttpResponseEncoder());
                                pipeline.addLast("httpAggregator", new HttpObjectAggregator(1024 * 1024 * 5));
                                // 分发器
                                pipeline.addLast("dispatcher", new DispatchHandler());
                                // HTTP处理器 TODO: 支持HTTPS
                                pipeline.addLast("httpHandler", new HttpHandler());
                                // WS处理器 TODO: 支持WSS
                                // pipeline.addLast("wsHandler", new WsHandler());
                            }
                        }
                )
                .localAddress(conf.host, conf.port);

        this.taskExecutorService = taskExecutorService;
    }

    public void run() {
        try {
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
            var channelFuture = this.bootstrap.bind().sync();
            log.info("网络服务启动成功，监听地址: {}", channelFuture.channel().localAddress());
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("网络服务运行中断", e);
            Thread.currentThread().interrupt();
        } finally {
            this.shutdown();
        }
    }

    public void shutdown() {
        try {
            this.bossGroup.shutdownGracefully().sync();
            this.workerGroup.shutdownGracefully().sync();
        } catch (Exception e) {
            log.error("网络服务关闭时发生错误", e);
        }
    }
}
