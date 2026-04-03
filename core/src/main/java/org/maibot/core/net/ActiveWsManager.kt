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

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ActiveWsManager::class.java)
    }
}
