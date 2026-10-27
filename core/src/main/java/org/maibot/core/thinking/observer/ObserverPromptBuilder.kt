package org.maibot.core.thinking.observer

import org.maibot.core.config.CoreConfig
import org.maibot.core.thinking.ObservationEntry
import org.maibot.core.thinking.ObservationSnapshot
import org.maibot.core.thinking.ObservationTrigger
import org.maibot.core.thinking.FlowState
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.Value
import org.maibot.sdk.model.payload.MessageContextItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ObserverAI 的观察 prompt
 *
 * 结构：
 * 1. system：观察者职责、工具规则（稳定）；
 * 2. 历史：外部消息带 sequence，Bot 消息用 assistant 角色（稳定前缀）；
 * 3. 尾部：当前状态、触发原因、新消息、上次决策、空闲时间（动态段）。
 */
@Component
class ObserverPromptBuilder
@AutoInject internal constructor(
    @param:Value($$"${*}") private val config: CoreConfig,
) {
    fun build(snapshot: ObservationSnapshot): List<MessageContextItem> {
        val items = ArrayList<MessageContextItem>(snapshot.entries.size + 2)
        items.add(MessageContextItem.ofSystem(buildSystemPrompt()))

        for (entry in snapshot.entries) {
            val text = entry.message.promptStr?.takeIf { it.isNotBlank() } ?: continue
            if (entry.isExternal) {
                items.add(
                    MessageContextItem.ofUser(
                        listOf(MessageContextItem.Content.ofText(renderExternal(entry, text)))
                    )
                )
            } else {
                items.add(
                    MessageContextItem.ofAssistant(
                        listOf(MessageContextItem.Content.ofText(text))
                    )
                )
            }
        }

        items.add(
            MessageContextItem.ofUser(
                listOf(MessageContextItem.Content.ofText(buildTail(snapshot)))
            )
        )
        return items
    }

    private fun buildSystemPrompt(): String {
        val botName = config.botInfo.name
        return buildString {
            appendLine("你是 $botName 的交互流观察者，也是这个聊天流里的参与者。")
            appendLine("你的职责不是回复每条消息，而是判断这次观察该做什么：发言、继续观察、进入关注、退出关注或退出观察。")
            appendLine()
            appendLine("工具规则：")
            appendLine("- `speak`：让聊天模型根据当前观察窗口和发言意图生成并发送一条回复；一次观察最多调用一次。")
            appendLine("- `wait_and_watch`：保持当前状态，结束本次观察。")
            appendLine("- `focus_watch`：进入关注状态，之后新消息立即响应，并保留周期主动观察。")
            appendLine("- `unfocus_watch`：退出关注，回到周期性观察。")
            appendLine("- `quit_watch`：退出观察，回到不活跃；下一条新消息会重新激活。")
            appendLine()
            appendLine("判断要求：")
            appendLine("- 有新消息时，判断是否值得回复；没有新消息时不要复读历史里已经说过的内容。")
            appendLine("- 只有认为对话值得持续跟进时才进入关注；话题冷下来时退出关注。")
            appendLine("- 关注态的周期观察可以考虑主动开启话题，但不要为了说话而说话。")
            appendLine("- 不要提到自己是 AI，不要解释这段设定。")
            appendLine("- 先调用 `speak`，最后必须调用一个终止工具（wait/focus/unfocus/quit）。")
        }.trim()
    }

    private fun renderExternal(entry: ObservationEntry, text: String): String {
        val timestamp = formatTimestamp(entry.message.timestamp)
        val nickname = entry.message.sender?.nickname ?: "用户"
        return "[$timestamp] $nickname (seq=${entry.sequence}): $text"
    }

    private fun buildTail(snapshot: ObservationSnapshot): String {
        val newText = if (snapshot.newSequences.isEmpty()) {
            "无"
        } else {
            snapshot.newSequences.sorted().joinToString(", ")
        }
        val idleSeconds = if (snapshot.lastActivityAt > 0) {
            ((System.currentTimeMillis() - snapshot.lastActivityAt) / 1_000).coerceAtLeast(0)
        } else {
            0
        }
        val triggerText = when (snapshot.trigger) {
            ObservationTrigger.ACTIVATION -> "不活跃流被新消息激活"
            ObservationTrigger.NEW_MESSAGE -> "关注态新消息"
            ObservationTrigger.PERIODIC -> "周期观察"
        }

        return buildString {
            appendLine("[本次观察]")
            appendLine("当前状态：${snapshot.state}")
            appendLine("触发原因：$triggerText")
            appendLine("当前时间：${formatTimestamp(System.currentTimeMillis())}")
            appendLine("自上次观察以来的新消息 sequence：$newText")
            appendLine("上次决策：${snapshot.lastDecision ?: "无"}")
            appendLine("距离上次活动：${idleSeconds}s")
            appendLine()

            if (snapshot.newSequences.isEmpty()) {
                when (snapshot.state) {
                    FlowState.ACTIVE ->
                        appendLine("本次是周期观察，没有新消息。默认保持观察；除非有明确理由，否则不要主动发言。")

                    FlowState.FOCUSED ->
                        appendLine("本次是关注态主动观察，没有新消息。可以判断是否主动开启/推进话题；没有明确理由时保持观察。")

                    FlowState.INACTIVE ->
                        appendLine("当前不应处于观察状态。")
                }
            } else {
                appendLine("有新消息进入观察窗口。请判断是否需要回复、是否提高关注度，或继续观察。")
            }

            appendLine("最后必须调用一个终止工具；如果需要发言，先调用 speak。")
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
