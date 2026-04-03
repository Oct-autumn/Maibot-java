package org.maibot.sdk.storage.domain

import io.netty.util.internal.UnstableApi

/**
 * 消息类型
 */
enum class StreamType(@JvmField val prefix: String) {
    /** 私聊 */
    PRIVATE("P-"),

    /** 群聊 */
    GROUP("G-"),

    /** 群临时会话
     *
     * 不稳定API: 该类型消息可能不被所有平台支持 */
    @UnstableApi
    GROUP_TEMP("GT-"),

    /** 群匿名消息
     *
     * 不稳定API: 该类型消息可能不被所有平台支持 */
    @UnstableApi
    GROUP_ANONYMOUS("GA-");
}
