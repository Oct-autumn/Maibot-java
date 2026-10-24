package org.maibot.sdk.manager

import jakarta.persistence.EntityManager
import org.maibot.sdk.storage.db.dao.InteractionEntity
import org.maibot.sdk.storage.db.dao.Message
import org.maibot.sdk.storage.model.msgevt.AbstractMessageEvent

interface MessageManager {
    /**
     * 保存消息事件到数据库
     *
     * @param em  实体管理器（调用方负责事务）
     * @param msg 消息事件
     */
    fun saveMessage(em: EntityManager, msg: AbstractMessageEvent)

    /**
     * 保存一条 Bot 自己生成的回复消息
     *
     * 复用 message 表，sender 指向 Bot 自身的交互实体，objectType 标记消息来源。
     *
     * @param em           实体管理器（调用方负责事务）
     * @param streamId     交互流 ID
     * @param text         消息文本
     * @param sender       Bot 自身的交互实体
     * @param objectType   对象类型标识（如 `core.bot_text`）
     * @param rawContentJson 原始内容 JSON（可携带 reply_to_sequence 等元数据）
     * @return 持久化后的消息实体
     */
    fun saveBotMessage(
        em: EntityManager,
        streamId: String,
        text: String,
        sender: InteractionEntity,
        objectType: String,
        rawContentJson: String,
    ): Message

    /**
     * 查询某个交互流最近的若干条消息
     *
     * @param em       实体管理器
     * @param streamId 交互流 ID（数据库主键，如 `P-1`，不是平台侧用户 ID）
     * @param limit    最多返回的条数
     * @return 按序列号升序（从旧到新）排列的消息列表；不存在时返回空列表
     */
    fun listRecentMessages(em: EntityManager, streamId: String, limit: Int): List<Message>

    /**
     * 根据消息序列号查询消息实体
     *
     * @param em       实体管理器
     * @param sequence 消息序列号
     * @return 消息实体；消息不存在时返回 null
     *
     * 消息事件里只有平台侧信息，没有数据库主键；入站处理器需要拿到落库后的
     * [Message]（含 stream / sender）才能投递给观察窗口。
     */
    fun findMessageBySequence(em: EntityManager, sequence: Long): Message?

    /**
     * 根据消息序列号查询其所属的交互流 ID
     *
     * @param em       实体管理器
     * @param sequence 消息序列号（[org.maibot.sdk.SNoGenerator.SerialNo.sNo]）
     * @return 交互流 ID；消息不存在时返回 null
     */
    fun findStreamIdBySequence(em: EntityManager, sequence: Long): String?
}
