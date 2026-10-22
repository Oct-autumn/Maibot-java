package org.maibot.sdk.manager

import jakarta.persistence.EntityManager
import org.maibot.sdk.storage.db.dao.Message
import org.maibot.sdk.storage.model.msgevt.AbstractMessageEvent

interface MessageManager {
    /**
     * 保存消息事件到数据库
     */
    fun saveMessage(em: EntityManager, msg: AbstractMessageEvent)

    /**
     * 查询某个交互流最近的若干条消息
     *
     * @param em       实体管理器
     * @param streamId 交互流 ID（如 `P-1929596784`）
     * @param limit    最多返回的条数
     * @return 按序列号升序（从旧到新）排列的消息列表；不存在时返回空列表
     */
    fun listRecentMessages(em: EntityManager, streamId: String, limit: Int): List<Message>

    /**
     * 根据消息序列号查询其所属的交互流 ID
     *
     * 消息事件里只有平台侧的信息，没有数据库交互流主键，因此需要反查。
     *
     * @param em       实体管理器
     * @param sequence 消息序列号（[org.maibot.sdk.SNoGenerator.SerialNo.sNo]）
     * @return 交互流 ID；消息不存在时返回 null
     */
    fun findStreamIdBySequence(em: EntityManager, sequence: Long): String?
}
