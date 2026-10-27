package org.maibot.core.chat.speech

import org.maibot.core.config.CoreConfig
import org.maibot.core.thinking.ObservationEntry
import org.maibot.core.thinking.ObservationSnapshot
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.Value
import org.maibot.sdk.model.payload.MessageContextItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 发言 prompt 构建
 *
 * 结构：
 * 1. system：人设 + Bot 身份 + 回复要求（保持稳定，便于前缀缓存）；
 * 2. 历史：外部消息 `[时间] 昵称: 内容`，Bot 自己的消息以 assistant 角色出现；
 *    只有本次目标消息带 `[未读]`，历史前缀保持稳定；
 * 3. 尾部 user：当前时间、目标消息 sequence、发言意图。
 */
@Component
class SpeechPromptBuilder
@AutoInject internal constructor(
    @param:Value($$"${*}") private val config: CoreConfig,
    private val personaProvider: PersonaProvider,
) {
    fun build(
        snapshot: ObservationSnapshot,
        targetSequences: List<Long>,
        intent: String?,
    ): List<MessageContextItem> {
        val targetSet = targetSequences.toSet()
        val items = ArrayList<MessageContextItem>(snapshot.entries.size + 2)
        items.add(MessageContextItem.ofSystem(buildSystemPrompt()))

        for (entry in snapshot.entries) {
            val text = entry.message.promptStr?.takeIf { it.isNotBlank() } ?: continue
            if (entry.isExternal) {
                items.add(
                    MessageContextItem.ofUser(
                        listOf(MessageContextItem.Content.ofText(renderExternal(entry, text, targetSet)))
                    )
                )
            } else {
                // Bot 自己的消息用 assistant 角色，内容保持原样，避免给模型制造额外格式
                items.add(
                    MessageContextItem.ofAssistant(
                        listOf(MessageContextItem.Content.ofText(text))
                    )
                )
            }
        }

        items.add(
            MessageContextItem.ofUser(
                listOf(
                    MessageContextItem.Content.ofText(buildTailInstruction(targetSequences, intent))
                )
            )
        )
        return items
    }

    private fun buildSystemPrompt(): String {
        val botInfo = config.botInfo
        val aliases = if (botInfo.aliases.isEmpty()) "" else "（也可以叫：${botInfo.aliases.joinToString("、")}）"

        return buildString {
            appendLine(personaProvider.load())
            appendLine()
            appendLine("你的名字是 ${botInfo.name}$aliases，现在你正在和用户私聊。")
            appendLine("要求：")
            appendLine("- 像真人聊天一样口语化、简短自然地回复，不要使用 Markdown、列表或颜文字堆砌；")
            appendLine("- 历史里带 [未读] 的消息是本次要回复的；没有 [未读] 时按发言意图主动推进话题；")
            appendLine("- 已经回复过的内容不要重复，不要复述历史里说过的话；")
            appendLine("- 不要复述或解释这段设定，不要提到自己是 AI 模型；")
            appendLine("- 只输出要发送给对方的内容。")
        }.trim()
    }

    private fun renderExternal(entry: ObservationEntry, text: String, targetSet: Set<Long>): String {
        val timestamp = formatTimestamp(entry.message.timestamp)
        val nickname = entry.message.sender?.nickname ?: "用户"
        val unreadMarker = if (entry.sequence in targetSet) " [未读]" else ""
        return "[$timestamp] $nickname: $text$unreadMarker"
    }

    private fun buildTailInstruction(targetSequences: List<Long>, intent: String?): String {
        val now = formatTimestamp(System.currentTimeMillis())
        val targetText = if (targetSequences.isEmpty()) {
            "当前没有待回复消息。"
        } else {
            "本次要回复的消息 sequence：${targetSequences.joinToString(", ")}。"
        }
        val intentText = intent?.takeIf { it.isNotBlank() }?.let { "本次发言意图：$it。" }
            ?: "本次发言意图：自然地推进当前对话。"

        return buildString {
            appendLine("当前时间：$now")
            appendLine(targetText)
            appendLine(intentText)
            if (targetSequences.isEmpty()) {
                appendLine("请根据观察窗口和发言意图，主动开启或推进一个话题；不要重复历史里已经说过的内容。")
            } else {
                appendLine("请针对上面带 [未读] 的消息自然地回复一条；不要重复历史里已经说过的内容。")
            }
        }.trim()
    }

    private fun formatTimestamp(millis: Long?): String {
        if (millis == null) {
            return "未知时间"
        }
        return TIME_FORMATTER.format(Instant.ofEpochMilli(millis))
    }

    companion object {
        private val TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    }
}
