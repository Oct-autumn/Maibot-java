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
    @field:JsonProperty(value = "chat") val chat: Chat = Chat(),
    @field:JsonProperty(value = "model_log") val modelLog: ModelLog = ModelLog(),
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
        /** 观察窗口容量 */
        @field:JsonProperty(value = "observation_window_size") val observationWindowSize: Int = 30,
        /** ACTIVE 首次/重置后的观察间隔（秒） */
        @field:JsonProperty(value = "active_interval_sec") val activeIntervalSec: Long = 30,
        /** ACTIVE 退避后的最大观察间隔（秒） */
        @field:JsonProperty(value = "active_max_interval_sec") val activeMaxIntervalSec: Long = 120,
        /** FOCUSED 首次/重置后的主动观察间隔（秒） */
        @field:JsonProperty(value = "focus_interval_sec") val focusIntervalSec: Long = 15,
        /** FOCUSED 退避后的最大主动观察间隔（秒） */
        @field:JsonProperty(value = "focus_max_interval_sec") val focusMaxIntervalSec: Long = 300,
        /** 每次 wait 后间隔的增长因子；1.0 表示固定间隔 */
        @field:JsonProperty(value = "interval_growth_factor") val intervalGrowthFactor: Double = 2.0,
        /** ObserverAI 配置 */
        @field:JsonProperty(value = "observer") val observer: Observer = Observer(),
    ) {
        data class Observer(
            /** 观察模型任务名 */
            @field:JsonProperty(value = "model_task") val modelTask: String = "observer",
            /** 单次观察等待模型的最长时间（秒） */
            @field:JsonProperty(value = "timeout_sec") val timeoutSec: Long = 30,
            /** 一次观察内最多几轮工具调用 */
            @field:JsonProperty(value = "max_tool_rounds") val maxToolRounds: Int = 4,
        )
    }

    /**
     * 私聊聊天配置
     *
     * M1 阶段只支持私聊纯文本对话。所有字段都有默认值，旧配置文件缺少该节点时
     * 会使用默认值（聊天默认关闭），不会导致启动失败。
     */
    data class Chat(
        /** 是否启用聊天 */
        @field:JsonProperty(value = "enabled") val enabled: Boolean = false,
        /** 是否启用回环调试模式：收到私聊消息后把渲染结果原样发回，用于验证出站通道 */
        @field:JsonProperty(value = "debug_echo") val debugEcho: Boolean = false,
        /** 组装上下文时引用的历史消息条数 */
        @field:JsonProperty(value = "history_limit") val historyLimit: Int = 20,
        /** 调用模型的最长等待时间（秒） */
        @field:JsonProperty(value = "reply_timeout_sec") val replyTimeoutSec: Long = 60,
        /** 发言使用的模型任务名（沿用 M1 的 `chat` 任务） */
        @field:JsonProperty(value = "model_task") val modelTask: String = "chat",
        /** 人设文件路径（相对于运行目录） */
        @field:JsonProperty(value = "persona_file") val personaFile: String = "config/persona.txt",
        /** 允许对话的用户平台 ID 列表；为空表示不限制 */
        @field:JsonProperty(value = "allowed_users") val allowedUsers: List<String> = listOf()
    )

    /**
     * 模型调用存档配置
     *
     * [persistContentTasks] 中的任务会把模型原始输出（thinking / content / toolCalls）
     * 写入 model_interaction；其余任务只写 model_api_request 统计。
     */
    data class ModelLog(
        /** 需要保存原始输出的任务名列表 */
        @field:JsonProperty(value = "persist_content_tasks") val persistContentTasks: List<String> =
            listOf("chat"),
        /** 单个 TEXT 字段的最大字符数，超出会被截断 */
        @field:JsonProperty(value = "content_max_chars") val contentMaxChars: Int = 20000,
        /** model_interaction 保留天数，0 表示不清理 */
        @field:JsonProperty(value = "content_retention_days") val contentRetentionDays: Int = 30,
        /** 过期内容清理的检查间隔（分钟），0 表示只在启动时清理一次 */
        @field:JsonProperty(value = "cleanup_interval_minutes") val cleanupIntervalMinutes: Int = 60,
    )
}
