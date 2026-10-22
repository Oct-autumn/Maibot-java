package org.maibot.sdk.msg

/**
 * 平台自身（Bot）信息
 *
 * 由 Adapter 在注册 [MessageSender] 时提供，Core 需要用它把 Bot 自己发出的消息
 * 也写入消息表（作为 assistant 角色的上下文），因此必须包含平台内的用户 ID。
 *
 * @param platformUserId Bot 在平台内的用户 ID，例如 QQ 号
 * @param nickname       Bot 在平台内的昵称
 */
@JvmRecord
data class SelfInfo(
    val platformUserId: String,
    val nickname: String
)
