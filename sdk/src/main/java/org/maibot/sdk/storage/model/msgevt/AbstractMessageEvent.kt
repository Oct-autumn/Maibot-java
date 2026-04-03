package org.maibot.sdk.storage.model.msgevt

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.EntityManager
import org.maibot.sdk.SNoGenerator
import org.maibot.sdk.manager.InteractionEntityManager
import org.maibot.sdk.manager.InteractionGroupManager
import org.maibot.sdk.manager.InteractionStreamManager
import org.maibot.sdk.storage.db.dao.Message
import org.maibot.sdk.storage.domain.StreamType
import org.maibot.sdk.util.UnwrapUtils.unwrap
import tools.jackson.databind.ObjectMapper

/**
 * 抽象消息事件接口，定义了将消息转化为提示词字符串和数据库存储对象的方法
 * 
 * 
 * 若想要实现消息事件，请实现此接口。同时，为确保消息能被顺利地从数据库中取出，请实现并注册相应的工厂类。
 * 
 * @author OctAutumn
 */
@Suppress("unused")
abstract class AbstractMessageEvent(
    platform: String, senderInfo: MessageMeta.EntityInfo, streamInfo: MessageMeta.StreamInfo,
    /** 消息时间戳 */
    @field:JsonProperty("timestamp") val timestamp: Long,
    /** 消息序列号（可用于排序） */
    @field:JsonProperty("sequence") val serialNo: SNoGenerator.SerialNo,
    /** 消息事件类的对象类型名称，用于数据库存储和工厂类匹配 */
    private val objectType: String?
) {

    /** 消息来源信息 */
    @JsonProperty("message_meta")
    val messageMeta: MessageMeta = MessageMeta(platform, senderInfo, streamInfo)

    /**
     * 将消息转化为提示词字符串
     */
    abstract fun toPromptString(em: EntityManager): String

    /**
     * 将消息的额外字段转化为数据库存储对象
     * 
     * 
     * 该方法需要在一个活动的实体管理器上下文中运行，仅生成数据库对象，不进行持久化操作。
     * 
     * @param em 实体管理器
     * @return 数据库消息对象
     */
    fun toDatabaseObject(
        em: EntityManager,
        interactionEntityManager: InteractionEntityManager,
        interactionGroupManager: InteractionGroupManager,
        interactionStreamManager: InteractionStreamManager
    ): Message {
        val message = Message()

        // 两个查询：
        // 1. 查询 interaction_entity 表，获取发送者实体的 ID
        // 2. 将 interaction_stream 表和 interaction_entity、interaction_group 表关联起来，通过消息类型辨别要
        //     关联Entity的互动流还是Group的互动流，获取互动流的 ID

        // 查询发送者实体
        message.sender = interactionEntityManager.get(
            em, messageMeta.platform, messageMeta.senderInfo.platformId
        )

        // 查询互动流
        val streamInfo = this.messageMeta.streamInfo
        when (streamInfo.streamType) {
            StreamType.PRIVATE -> {
                message.stream = interactionStreamManager.getOrCreateIfAbsent(
                    em, StreamType.PRIVATE, interactionEntityManager.get(
                        em, this.messageMeta.platform, streamInfo.privateInfo!!.platformId
                    ), null
                )
            }

            StreamType.GROUP -> {
                message.stream = interactionStreamManager.getOrCreateIfAbsent(
                    em, StreamType.GROUP, null, interactionGroupManager.get(
                        em, this.messageMeta.platform, streamInfo.groupInfo!!.platformId
                    )
                )
            }

            StreamType.GROUP_TEMP -> {
                // 暂不做支持，预期行为同 PRIVATE
            }

            StreamType.GROUP_ANONYMOUS -> {
                // 不计划支持
            }
        }

        message.timestamp = this.timestamp
        message.sequence = this.serialNo.sNo
        message.promptStr = this.toPromptString(em)
        message.objectType = this.objectType

        return message
    }

    fun <T> unwrap(clazz: Class<T>): T? {
        return unwrap(clazz, this)
    }

    /**
     * 将消息的类型特定内容（非Abstract基类的字段）转化为 JSON 字符串
     * 
     * @return 类型特定内容的 JSON 字符串
     */
    protected abstract fun toRawContentJson(objectMapper: ObjectMapper): String

    override fun toString(): String {
        return "AbstractMessageEvent[messageSource=$messageMeta, timestamp=$timestamp, sequence=$serialNo]"
    }
}
