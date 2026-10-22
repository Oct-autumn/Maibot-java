package org.maibot.core.msg

import org.maibot.sdk.ioc.Component
import org.maibot.sdk.msg.SelfInfo
import org.maibot.sdk.msg.SelfInfoRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 平台自身（Bot）信息注册表的 Core 实现
 *
 * Adapter 在启用时注册自身账号信息；Core 用它给 Bot 自己发出的消息标注为 assistant。
 */
@Component
class SelfInfoRegistryImpl : SelfInfoRegistry {
    private val entries = ConcurrentHashMap<String, SelfInfo>()

    override fun register(platform: String, selfInfo: SelfInfo) {
        val previous = entries.put(platform, selfInfo)
        if (previous != null) {
            log.warn("平台 {} 的自身信息被重复注册，已覆盖旧值", platform)
        }
        log.info("平台 {} 的自身信息已注册：{}（{}）", platform, selfInfo.platformUserId, selfInfo.nickname)
    }

    override fun get(platform: String): SelfInfo? {
        return entries[platform]
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SelfInfoRegistryImpl::class.java)
    }
}
