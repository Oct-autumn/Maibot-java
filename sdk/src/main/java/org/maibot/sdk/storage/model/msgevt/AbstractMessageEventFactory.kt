package org.maibot.sdk.storage.model.msgevt

import org.maibot.sdk.SNoGenerator
import org.maibot.sdk.SNoGenerator.from
import org.maibot.sdk.storage.db.dao.Message
import tools.jackson.databind.ObjectMapper

@Suppress("unused")
abstract class AbstractMessageEventFactory
/**
 * @param objectType 消息事件类的对象类型名称
 */
protected constructor(val objectType: String) {
    var messageMeta: MessageMeta? = null
        protected set
    var timestamp: Long? = null
        protected set
    var sequence: SNoGenerator.SerialNo? = null
        protected set

    fun setMessageMeta(messageMeta: MessageMeta): AbstractMessageEventFactory {
        this.messageMeta = messageMeta
        return this
    }

    fun setTimestamp(timestamp: Long): AbstractMessageEventFactory {
        this.timestamp = timestamp
        return this
    }

    fun setSequence(serialNo: SNoGenerator.SerialNo): AbstractMessageEventFactory {
        this.sequence = serialNo
        return this
    }

    /**
     * 从数据库提取 MessageEvent 信息
     * 
     * 
     * 建议在EntityManager的上下文中调用此方法，以确保延迟加载的属性能够正确获取。<br></br>
     * 若在EntityManager上下文之外调用，则需要提前加载相关属性，避免出现LazyInitializationException异常。
     * 
     * @param message 数据库消息对象
     * @return 消息事件工厂实例
     * @throws IllegalArgumentException 如果消息的对象类型与当前工厂不匹配
     */
    fun fromDatabase(objectMapper: ObjectMapper, message: Message): AbstractMessageEventFactory {
        require(message.objectType == this.objectType) {
            "Cannot build message event from ${message.objectType} to ${this.javaClass.getName()}"
        }

        this.messageMeta = MessageMetaFactory().fromDatabase(message).build()
        this.timestamp = message.timestamp
        this.sequence = from(message.sequence!!)

        return fromRawContentJson(objectMapper, message.rawContentJson!!)
    }

    /**
     * 从类型特定内容 JSON 字符串中提取 MessageEvent 信息
     * 
     * @param objectMapper JSON 对象映射器
     * @param jsonString   类型特定内容的 JSON 字符串
     * @return 消息事件工厂实例
     */
    protected abstract fun fromRawContentJson(
        objectMapper: ObjectMapper, jsonString: String
    ): AbstractMessageEventFactory

    /**
     * 构建消息事件对象
     * 
     * @return 消息事件对象
     */
    abstract fun build(): AbstractMessageEvent
}
