package org.maibot.core.event

import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.embedded.EmbeddedChannel
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.DestroyableComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component
class SystemEventService : DestroyableComponent {
    private val channel: Channel = EmbeddedChannel()

    init {
        log.info("系统事件服务已启动")
    }

    fun addHandler(name: String?, handler: ChannelHandler?) {
        this.channel.pipeline().addLast(name, handler)
    }

    fun removeHandler(name: String?) {
        this.channel.pipeline().remove(name)
    }

    fun fireEvent(event: Any?) {
        this.channel.pipeline().fireUserEventTriggered(event)
    }

    override fun preDestroy() {
        try {
            this.channel.close()
        } catch (e: Exception) {
            log.error("关闭系统事件服务时发生错误", e)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SystemEventService::class.java)
    }
}
