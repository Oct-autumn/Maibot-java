package org.maibot.core.net;

import io.netty.channel.ChannelHandlerContext;
import org.maibot.core.cdi.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ActiveWsManager {
    private static final Logger log = LoggerFactory.getLogger(ActiveWsManager.class);

    private final Map<String, ChannelHandlerContext> activeConnections = new ConcurrentHashMap<>();

    /**
     * 添加一个新的WebSocket连接
     * <p>
     * - 当已有相同路径的连接存在时，拒绝新的连接请求<br>
     * - 若连接关闭，则自动从活动连接表中移除
     *
     * @param path 连接路径
     * @param ctx  连接的ChannelHandlerContext
     */
    public void addConnection(String path, ChannelHandlerContext ctx) {
        var v = activeConnections.computeIfAbsent(path, k -> {
            log.debug("PATH: {} 添加新的活动WebSocket连接", path);
            ctx.channel().closeFuture().addListener(future -> {
                activeConnections.remove(path);
                log.debug("PATH: {} 的WebSocket连接已关闭，移除活动连接", path);
            });
            return ctx;
        });

        if (v != ctx) {
            // 已有相同路径的连接存在，关闭新的连接请求
            log.warn("已有PATH: {} 的WebSocket连接存在，拒绝新的连接请求", path);
            ctx.close();
        }
    }

    /**
     * 移除一个WebSocket连接
     *
     * @param path 连接路径
     */
    public void removeConnection(String path) {
        activeConnections.remove(path);
    }

    /**
     * 获取一个活动的WebSocket连接
     *
     * @param path 连接路径
     * @return 连接的ChannelHandlerContext，若不存在则返回null
     */
    public ChannelHandlerContext getConnection(String path) {
        return activeConnections.get(path);
    }
}
