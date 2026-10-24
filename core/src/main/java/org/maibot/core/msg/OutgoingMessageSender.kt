package org.maibot.core.msg

import org.maibot.sdk.msg.OutgoingMessage

/**
 * 出站消息发送接口
 *
 * 编排器依赖该接口而不是具体实现，方便测试与替换。
 */
interface OutgoingMessageSender {
    /**
     * 发送一条出站消息
     *
     * @return 是否已提交
     */
    fun send(message: OutgoingMessage): Boolean
}
