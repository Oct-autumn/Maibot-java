package org.maibot.core.chat.speech

import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.Value
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * 从配置的 persona_file 读取人设；文件不存在或读取失败时退回内置默认人设。
 */
@Component
class FilePersonaProvider
@AutoInject private constructor(
    @param:Value($$"${chat.persona_file}") private val personaFile: String,
) : PersonaProvider {
    override fun load(): String {
        val path = try {
            Path.of(personaFile)
        } catch (e: Exception) {
            log.warn("人设文件路径无效: {}", personaFile, e)
            return DEFAULT_PERSONA
        }

        return try {
            if (Files.exists(path)) {
                Files.readString(path).trim().ifBlank { DEFAULT_PERSONA }
            } else {
                log.warn("人设文件不存在: {}，使用默认人设", path.toAbsolutePath())
                DEFAULT_PERSONA
            }
        } catch (e: Exception) {
            log.warn("读取人设文件失败: {}，使用默认人设", path.toAbsolutePath(), e)
            DEFAULT_PERSONA
        }
    }

    companion object {
        /** 默认人设（人设文件缺失时使用） */
        private const val DEFAULT_PERSONA = "你是一个有自己性格、说话自然的聊天伙伴。"

        private val log: Logger = LoggerFactory.getLogger(FilePersonaProvider::class.java)
    }
}
