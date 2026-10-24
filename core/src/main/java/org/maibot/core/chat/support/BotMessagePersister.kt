package org.maibot.core.chat.support

import org.maibot.sdk.storage.db.dao.Message

/**
 * Bot 回复落库接口
 */
interface BotMessagePersister {
    /**
     * 保存一条 Bot 回复，并把 reply_to 元数据写进 raw_content_json
     *
     * @param replyTo 这条回复覆盖的外部消息 sequence 列表
     */
    fun persist(streamId: String, text: String, replyTo: List<Long>): Message
}
