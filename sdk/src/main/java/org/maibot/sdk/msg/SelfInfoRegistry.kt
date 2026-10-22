package org.maibot.sdk.msg

/**
 * 平台自身（Bot）信息注册表
 *
 * Adapter 启用时注册自身账号信息，Core 需要用它在消息表里标注 Bot 自己发出的消息
 * （作为对话上下文中的 assistant 角色），以及在日志中展示身份。
 */
interface SelfInfoRegistry {
    /**
     * 注册某个平台的 Bot 自身信息
     *
     * @param platform 平台标识
     * @param selfInfo Bot 在该平台上的信息
     */
    fun register(platform: String, selfInfo: SelfInfo)

    /**
     * 获取某个平台的 Bot 自身信息
     *
     * @param platform 平台标识
     * @return 自身信息，未注册时返回 null
     */
    fun get(platform: String): SelfInfo?
}
