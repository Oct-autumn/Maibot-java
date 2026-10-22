package org.maibot.core.msg

import org.maibot.core.net.ActiveWsManager
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.msg.OutgoingMessage
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 出站消息服务
 *
 * Core 侧统一出口：把通用消息实例（[OutgoingMessage]）写入对应平台的 WebSocket 连接，
 * 具体的协议编码由 Adapter 在管线中完成。Core 不需要认识任何平台协议。
 */
@Component
class OutgoingMessageService
@AutoInject private constructor(
    private val activeWsManager: ActiveWsManager
) {
    /**
     * 发送一条出站消息
     *
     * @param message 通用消息实例
     * @return 是否已提交到连接（false 表示平台未登记或当前没有活动连接）
     */
    fun send(message: OutgoingMessage): Boolean {
        if (message.segments.isEmpty()) {
            log.warn("出站消息没有消息段，已忽略（平台: {}，目标: {}）", message.platform, message.platformTargetId)
            return false
        }

        if (!activeWsManager.send(message.platform, message)) {
            log.warn(
                "平台 {} 没有可用连接，出站消息未发送（目标: {}）",
                message.platform, message.platformTargetId
            )
            return false
        }

        log.debug(
            "出站消息已提交（平台: {}，目标: {}，消息段数: {}）",
            message.platform, message.platformTargetId, message.segments.size
        )
        return true
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(OutgoingMessageService::class.java)
    }
}
