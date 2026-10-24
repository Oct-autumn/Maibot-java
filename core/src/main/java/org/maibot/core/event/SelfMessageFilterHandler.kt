package org.maibot.core.event

import io.netty.channel.ChannelHandlerContext
import org.maibot.sdk.eventchannel.SimpleEventHandler
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.msg.SelfInfoRegistry
import org.maibot.sdk.storage.model.msgevt.AbstractMessageEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Bot 自身消息回显过滤器
 *
 * NapCat 可能把自己发出的消息也作为 message 事件回传。这类消息既不应该落库
 * （Bot 回复已由 Core 自己写入 message 表），也不应该进入观察窗口，否则会自激。
 *
 * 必须放在「消息持久化处理器」之前。
 */
@Component
class SelfMessageFilterHandler
@AutoInject private constructor(
    private val selfInfoRegistry: SelfInfoRegistry,
) : SimpleEventHandler<AbstractMessageEvent>() {
    override fun handleEvent(ctx: ChannelHandlerContext, evt: AbstractMessageEvent): Boolean {
        val platform = evt.messageMeta.platform
        val selfInfo = selfInfoRegistry.get(platform) ?: return true

        if (evt.messageMeta.senderInfo.platformId == selfInfo.platformUserId) {
            log.debug("丢弃 Bot 自身消息回显 (sNo: {})", evt.serialNo.toHexString())
            return false
        }

        return true
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SelfMessageFilterHandler::class.java)
    }
}
