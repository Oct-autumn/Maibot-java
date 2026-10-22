package org.maibot.core.net

import io.netty.channel.ChannelHandlerContext
import io.netty.util.concurrent.GenericFutureListener
import org.maibot.sdk.ioc.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 活动WebSocket连接管理器
 * 
 * 
 * 管理所有当前活动的WebSocket连接，确保每个路径只有一个活动连接。
 */
@Component
class ActiveWsManager {
    private val activeConnections = ConcurrentHashMap<String, ChannelHandlerContext>()

    /** 平台标识 -> WebSocket 路径（由 Adapter 注册处理器时登记） */
    private val platformPaths = ConcurrentHashMap<String, String>()

    /**
     * 登记某个平台使用的 WebSocket 路径
     *
     * Core 发送出站消息时，需要据此找到平台对应的连接。
     *
     * @param platform 平台标识
     * @param path     连接路径
     */
    fun registerPlatform(platform: String, path: String) {
        val previous = platformPaths.put(platform, path)
        if (previous != null && previous != path) {
            log.warn("平台 {} 的 WebSocket 路径被重新登记：{} -> {}", platform, previous, path)
        }
        log.debug("登记平台 {} 的 WebSocket 路径: {}", platform, path)
    }

    /**
     * 添加一个新的WebSocket连接
     * 
     * 
     * - 当已有相同路径的连接存在时，拒绝新的连接请求<br></br>
     * - 若连接关闭，则自动从活动连接表中移除
     * 
     * @param path 连接路径
     * @param ctx  连接的ChannelHandlerContext
     */
    fun addConnection(path: String, ctx: ChannelHandlerContext) {
        val v = activeConnections.computeIfAbsent(
            path
        ) {
            log.debug("PATH: {} 添加新的活动WebSocket连接", path)
            ctx.channel().closeFuture().addListener(GenericFutureListener {
                activeConnections.remove(path)
                log.debug("PATH: {} 的WebSocket连接已关闭，移除活动连接", path)
            })
            ctx
        }

        if (v !== ctx) {
            // 已有相同路径的连接存在，关闭新的连接请求
            log.warn("已有PATH: {} 的WebSocket连接存在，拒绝新的连接请求", path)
            ctx.close()
        }
    }

    /**
     * 移除一个WebSocket连接
     * 
     * @param path 连接路径
     */
    fun removeConnection(path: String) {
        activeConnections.remove(path)
    }

    /**
     * 获取一个活动的WebSocket连接
     * 
     * @param path 连接路径
     * @return 连接的ChannelHandlerContext，若不存在则返回null
     */
    fun getConnection(path: String): ChannelHandlerContext? {
        return activeConnections[path]
    }

    /**
     * 向某个平台的活动连接发送出站消息
     *
     * 消息会从管线尾部写入，由 Adapter 装配的编码器（如 QQ 的 `NcProtocolEncoder`）
     * 编码成具体协议帧后发出。
     *
     * @param platform 平台标识
     * @param message  出站消息（通用消息实例）
     * @return 是否已提交发送（false 表示该平台未登记或当前没有活动连接）
     */
    fun send(platform: String, message: Any): Boolean {
        val path = platformPaths[platform] ?: run {
            log.debug("平台 {} 未登记 WebSocket 路径，无法发送消息", platform)
            return false
        }
        val ctx = activeConnections[path] ?: run {
            log.debug("平台 {} 当前没有活动连接（PATH: {}），无法发送消息", platform, path)
            return false
        }

        // 从 channel 尾部写入，保证经过 Adapter 编码器
        ctx.channel().writeAndFlush(message)
        return true
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ActiveWsManager::class.java)
    }
}
