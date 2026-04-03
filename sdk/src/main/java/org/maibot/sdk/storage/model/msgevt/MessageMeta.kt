package org.maibot.sdk.storage.model.msgevt

import org.maibot.sdk.storage.domain.StreamType

/**
 * 消息来源信息
 * 
 * @param platform   平台标识
 * @param senderInfo 发送者信息
 * @param streamInfo 消息流信息
 */
@JvmRecord
data class MessageMeta(
    val platform: String,
    val senderInfo: EntityInfo,
    val streamInfo: StreamInfo
) {
    /**
     * 实体信息
     * 
     * @param platformId   实体在平台的ID
     * @param nickname     昵称
     * @param cardNickname 备注名(私聊)/群名片(群聊/群临时会话)
     */
    @JvmRecord
    data class EntityInfo(
        val platformId: String,
        val nickname: String,
        val cardNickname: String?
    )

    /**
     * 群组信息
     * 
     * @param platformId 群组在平台的ID
     * @param groupName  群名称
     */
    @JvmRecord
    data class GroupInfo(
        val platformId: String,
        val groupName: String?
    )

    /**
     * 消息流信息
     * 
     * @param privateInfo 私聊信息
     * @param groupInfo   群聊信息
     * @param streamType  消息类型
     */
    @JvmRecord
    data class StreamInfo(
        val privateInfo: EntityInfo?,
        val groupInfo: GroupInfo?,
        val streamType: StreamType
    )
}
