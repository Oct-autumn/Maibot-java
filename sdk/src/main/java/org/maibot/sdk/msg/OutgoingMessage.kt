package org.maibot.sdk.msg

/**
 * 出站消息（通用消息实例）
 *
 * 由 Core 侧生产、写入该平台对应的 WebSocket 连接管线，由 Adapter 的编码器
 * （如 QQ Adapter 的 `NcProtocolEncoder`）编码成具体协议帧后发出。
 *
 * 这样 Core 不需要认识任何平台协议，消息的具体编码由 Adapter 在管线中完成。
 *
 * @param platform         平台标识，例如 `qq`
 * @param platformTargetId 目标对象在平台内的 ID（私聊为用户 ID，群聊为群 ID）
 * @param isGroup          是否群聊目标（M1 仅支持私聊）
 * @param segments         消息段列表
 */
@JvmRecord
data class OutgoingMessage(
    val platform: String,
    val platformTargetId: String,
    val isGroup: Boolean,
    val segments: List<OutgoingSegment>
) {
    companion object {
        /** 构造一条纯文本私聊消息 */
        @JvmStatic
        fun text(platform: String, platformTargetId: String, text: String): OutgoingMessage {
            return OutgoingMessage(platform, platformTargetId, false, listOf(OutgoingSegment.text(text)))
        }
    }
}
