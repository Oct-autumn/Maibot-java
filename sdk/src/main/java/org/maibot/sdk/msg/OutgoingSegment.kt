package org.maibot.sdk.msg

/**
 * 出站消息段
 *
 * 与具体协议无关的“通用消息实例”的组成部分。M1 只实现文本段，
 * 后续图片、@、表情等类型在此扩展（由 Adapter 的编码器翻译成协议格式）。
 *
 * @param type 段类型，目前仅支持 `text`
 * @param text 文本内容，`type = text` 时有效
 */
@JvmRecord
data class OutgoingSegment(
    val type: String,
    val text: String?
) {
    companion object {
        /** 段类型：纯文本 */
        const val TYPE_TEXT: String = "text"

        /** 构造一个文本消息段 */
        @JvmStatic
        fun text(text: String): OutgoingSegment {
            return OutgoingSegment(TYPE_TEXT, text)
        }
    }
}
