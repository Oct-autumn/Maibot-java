package org.maibot.core.event

import io.netty.channel.Channel
import io.netty.channel.embedded.EmbeddedChannel
import org.maibot.core.chat.InteractionIngressHandler
import org.maibot.sdk.eventchannel.GlobalEventService
import org.maibot.sdk.eventchannel.SimpleEventHandler
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.DestroyableComponent
import org.maibot.sdk.task.TaskExecuteService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component
class GlobalEventServiceImpl : GlobalEventService, DestroyableComponent {
    private val channel: Channel = EmbeddedChannel()
    private val taskExecuteService: TaskExecuteService;

    @AutoInject
    private constructor(
        taskExecuteService: TaskExecuteService,
        selfMessageFilterHandler: SelfMessageFilterHandler,
        msgPersistentHandler: MsgPersistentHandler,
        interactionIngressHandler: InteractionIngressHandler
    ) {
        this.taskExecuteService = taskExecuteService

        log.info("正在注册系统事件处理器...")

        // 自身消息回显必须在落库之前过滤，否则会污染消息表并在重启后被当成外部消息
        channel.pipeline().addLast("self_message_filter", selfMessageFilterHandler)
        channel.pipeline().addLast("msg_persistent_handler", msgPersistentHandler)
        // 入站处理器必须在持久化之后：它依赖消息已经落库并拿到 Message 实体
        channel.pipeline().addLast("interaction_ingress_handler", interactionIngressHandler)

        log.info("系统事件服务已启动")
    }

    override fun listHandlers(): List<String> {
        return this.channel.pipeline().names()
    }

    override fun addHandler(name: String?, handler: SimpleEventHandler<*>?) {
        this.channel.pipeline().addLast(name, handler)
    }

    override fun removeHandler(name: String?) {
        this.channel.pipeline().remove(name)
    }

    override fun fireEvent(event: Any?) {
        taskExecuteService.submit(false) {
            // 在独立线程中触发事件，避免阻塞调用者线程
            this.channel.pipeline().fireUserEventTriggered(event)
        }
    }

    override fun preDestroy() {
        try {
            this.channel.close()
        } catch (e: Exception) {
            log.error("关闭系统事件服务时发生错误", e)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GlobalEventServiceImpl::class.java)
    }
}
