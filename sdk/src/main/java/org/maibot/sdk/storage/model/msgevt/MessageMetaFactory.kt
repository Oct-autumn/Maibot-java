package org.maibot.sdk.storage.model.msgevt

import org.maibot.sdk.storage.db.dao.GroupMember
import org.maibot.sdk.storage.db.dao.Message
import org.maibot.sdk.storage.domain.StreamType
import java.util.*

@Suppress("unused")
class MessageMetaFactory {
    var platform: String? = null
        private set

    var senderInfo: MessageMeta.EntityInfo? = null
        private set

    var streamInfo: MessageMeta.StreamInfo? = null
        private set

    fun setPlatform(platform: String): MessageMetaFactory {
        this.platform = platform
        return this
    }

    fun setSenderInfo(senderInfo: MessageMeta.EntityInfo): MessageMetaFactory {
        this.senderInfo = senderInfo
        return this
    }

    fun setStreamInfo(streamInfo: MessageMeta.StreamInfo): MessageMetaFactory {
        this.streamInfo = streamInfo
        return this
    }


    /**
     * 从数据库提取 MessageMeta 信息。
     * 
     * 
     * 建议在EntityManager的上下文中调用此方法，以确保延迟加载的属性能够正确获取。<br></br>
     * 若在EntityManager上下文之外调用，则需要提前加载相关属性，避免出现LazyInitializationException异常。
     * 
     * @param message 数据库消息对象
     * @return MessageMetaFactory 实例
     */
    fun fromDatabase(message: Message): MessageMetaFactory {
        val sender = message.sender!!
        val stream = message.stream!!

        this.platform = sender.platform!!

        when (stream.type!!) {
            StreamType.PRIVATE -> {
                val streamEntity = stream.entity!!
                this.streamInfo = MessageMeta.StreamInfo(
                    MessageMeta.EntityInfo(
                        streamEntity.platformUserId!!, streamEntity.nickname!!, null
                    ), null, StreamType.PRIVATE
                )
                this.senderInfo = MessageMeta.EntityInfo(sender.platformUserId!!, sender.nickname!!, null)
            }

            StreamType.GROUP -> {
                val streamEntity = stream.entity!!
                val streamGroup = stream.group!!
                this.streamInfo = MessageMeta.StreamInfo(
                    MessageMeta.EntityInfo(
                        streamEntity.platformUserId!!,
                        streamEntity.nickname!!,
                        streamEntity.groupMembers!!.stream()
                            .filter { gm1: GroupMember? -> gm1!!.groupId == streamGroup.id }
                            .map { obj: GroupMember? -> obj!!.cardName }.findFirst().orElse(null)
                    ),
                    MessageMeta.GroupInfo(streamGroup.platformGroupId!!, streamGroup.groupName),
                    StreamType.PRIVATE
                )
                this.senderInfo = MessageMeta.EntityInfo(
                    sender.platformUserId!!,
                    sender.nickname!!,
                    sender.groupMembers!!.stream().filter { gm: GroupMember? -> gm!!.groupId == streamGroup.id }
                        .map { obj: GroupMember? -> obj!!.cardName }.findFirst().orElse(null)
                )
            }

            StreamType.GROUP_TEMP -> {
                // 暂不做支持，预期行为同 PRIVATE
            }

            StreamType.GROUP_ANONYMOUS -> {
                // 不计划支持
            }
        }

        return this
    }

    fun build(): MessageMeta {
        Objects.requireNonNull<String?>(platform, "platform must not be null")
        Objects.requireNonNull<MessageMeta.EntityInfo?>(senderInfo, "senderInfo must not be null")
        Objects.requireNonNull<MessageMeta.StreamInfo?>(streamInfo, "streamInfo must not be null")

        return MessageMeta(platform!!, senderInfo!!, streamInfo!!)
    }
}

