package org.maibot.core.net

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import org.maibot.core.config.CoreConfig
import org.maibot.core.util.TaskExecuteServiceImpl
import org.maibot.sdk.exceptions.FatalError
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.DestroyableComponent
import org.maibot.sdk.ioc.Value
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.net.BindException
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

@Component
class InnerServer
@AutoInject private constructor(
    @Value($$"${network}") private val conf: CoreConfig.Network,
    private val taskExecutorService: TaskExecuteServiceImpl,
    dispatchHandler: DispatchHandler?,
    exceptionHandler: ExceptionHandler?
) : DestroyableComponent {
    private val bootstrap = ServerBootstrap()

    private var bossGroup: IoEventLoopGroup? = null
    private var workerGroup: IoEventLoopGroup? = null

    init {
        bootstrap.channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    MDC.put("connId", Integer.toHexString(System.identityHashCode(ch)))

                    ch.pipeline()
                        // HTTP编解码器 与 HTTP消息聚合器（最大消息长度为5MB）
                        .addLast("httpCodec", HttpServerCodec())
                        .addLast("httpAggregator", HttpObjectAggregator(1024 * 1024 * 5))
                        // 分发器
                        .addLast("dispatcher", dispatchHandler)
                        // 异常处理兜底
                        .addLast("exceptionHandler", exceptionHandler)

                }
            })
    }

    fun start() {
        // 使用线程池，创建一个单线程的bossGroup和多线程的workerGroup
        bossGroup = MultiThreadIoEventLoopGroup(
            1, taskExecutorService.virtualExecutor()
        ) { tae -> NioIoHandler.newFactory().newHandler(tae) }

        workerGroup = MultiThreadIoEventLoopGroup(
            4, taskExecutorService.virtualExecutor()
        ) { tae -> NioIoHandler.newFactory().newHandler(tae) }

        val bindFuture = CompletableFuture<Channel>()
        bootstrap.apply {
            group(bossGroup, workerGroup)
            localAddress(conf.host, conf.port)
            bind().addListener(ChannelFutureListener { future ->
                if (!future.isSuccess) {
                    val cause = future.cause()
                    bindFuture.completeExceptionally(cause)
                } else {
                    bindFuture.complete(future.channel())
                }
            })
        }

        try {
            val channel = bindFuture.join()
            log.info("网络服务启动成功，监听地址: {}", channel.localAddress())
            channel.closeFuture().sync()
        } catch (e: Throwable) {
            when (e) {
                is CancellationException -> {
                    log.warn("网络服务绑定过程中被取消")
                    throw FatalError("Failed to start InnerServer", e)
                }

                is CompletionException -> {
                    val cause = e.cause
                    if (cause is BindException) {
                        log.warn("网络服务绑定端口失败，端口 {} 可能已被占用", this.conf.port)
                        throw FatalError("Failed to start InnerServer due to port binding failure", cause)
                    } else {
                        log.warn("网络服务绑定过程中发生异常")
                        throw FatalError("Failed to start InnerServer due to unknown error", cause)
                    }
                }

                is InterruptedException -> {
                    log.warn("网络服务运行中断")
                    throw FatalError("InnerServer interrupted", e)
                }

                else -> {
                    log.warn("网络服务发生未知异常")
                    throw FatalError("An unknown error occurred while starting/running InnerServer", e)
                }
            }
        } finally {
            preDestroy()
        }
    }

    override fun preDestroy() {
        try {
            bossGroup?.shutdownGracefully()?.sync()
            workerGroup?.shutdownGracefully()?.sync()

            bossGroup = null
            workerGroup = null
        } catch (e: Exception) {
            log.error("关闭网络服务时发生错误", e)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(InnerServer::class.java)
    }
}
