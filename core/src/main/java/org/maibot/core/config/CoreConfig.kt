package org.maibot.core.config

import com.fasterxml.jackson.annotation.JsonProperty
import org.maibot.sdk.config.ChoosableModel

/*
 * 主配置类，映射 config.toml 文件结构
 *
 * 请在配置项中使用包装类（如 Integer、Boolean）以支持 null 值
 * 请将字段设为final以防止意外修改
 */
@Suppress("unused") // 抑制警告：未使用
data class CoreConfig(
    @field:JsonProperty(value = "version", required = true) val version: String,
    @field:JsonProperty(value = "bot_info", required = true) val botInfo: BotInfo,
    @field:JsonProperty(value = "log") val log: Log = Log(),
    @field:JsonProperty(value = "network", required = true) val network: Network,
    @field:JsonProperty(
        value = "model_tasks",
        required = true
    ) val modelTasks: Map<String, List<ChoosableModel>> = mapOf(),
    @field:JsonProperty(value = "thinking") val thinking: Thinking = Thinking(),
) {
    data class BotInfo(
        @field:JsonProperty(value = "name", required = true) val name: String,
        @field:JsonProperty(value = "aliases") val aliases: List<String> = listOf()
    )

    data class Log(
        @field:JsonProperty(value = "console") val console: ConsoleLogSettings = ConsoleLogSettings(),
        @field:JsonProperty(value = "file") val file: FileLogSettings = FileLogSettings(),
        @field:JsonProperty(value = "enable_mdc_track") val enableMdcTrack: Boolean = false
    ) {
        data class ConsoleLogSettings(
            @field:JsonProperty(value = "level") val level: String = "INFO",
            @field:JsonProperty(value = "filter_rule") val filterRule: List<String> = listOf()
        )

        data class FileLogSettings(
            @field:JsonProperty(value = "level") val level: String = "INFO",
            @field:JsonProperty(value = "filter_rule") val filterRule: List<String> = listOf(),
            @field:JsonProperty(value = "max_rolling_files") val maxRollingFiles: Int = 5,
            @field:JsonProperty(value = "max_total_size_mb") val maxTotalSizeMb: Int = 100
        )
    }

    data class Network(
        @field:JsonProperty(value = "host", required = true) val host: String,
        @field:JsonProperty(value = "port", required = true) val port: Int,
        @field:JsonProperty(value = "auth_token") val authToken: String? = null
    )

    data class Thinking(
        @field:JsonProperty(value = "observation_window_size") val observationWindowSize: Int = 30
    )
}
