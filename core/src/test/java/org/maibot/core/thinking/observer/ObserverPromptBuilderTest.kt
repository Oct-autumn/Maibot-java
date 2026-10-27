package org.maibot.core.thinking.observer

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.maibot.core.config.CoreConfig
import org.maibot.core.thinking.FlowState
import org.maibot.core.thinking.ObservationEntry
import org.maibot.core.thinking.ObservationSnapshot
import org.maibot.core.thinking.ObservationTrigger
import org.maibot.sdk.model.payload.MessageContextItem
import org.maibot.sdk.storage.db.dao.InteractionEntity
import org.maibot.sdk.storage.db.dao.Message

class ObserverPromptBuilderTest {
    private val config = CoreConfig(
        version = "test",
        botInfo = CoreConfig.BotInfo(name = "小七", aliases = listOf("小柒")),
        network = CoreConfig.Network(host = "127.0.0.1", port = 8080),
    )

    private val builder = ObserverPromptBuilder(config)

    private fun message(sequence: Long, text: String, bot: Boolean = false): Message {
        val sender = InteractionEntity().apply {
            this.id = if (bot) 999L else sequence
            this.platform = "qq"
            this.platformUserId = if (bot) "10000" else "2000$sequence"
            this.nickname = if (bot) "小七" else "用户$sequence"
        }
        return Message().apply {
            this.sequence = sequence
            this.timestamp = 1_700_000_000_000 + sequence
            this.promptStr = text
            this.rawContentJson = """{"text":"$text"}"""
            this.objectType = if (bot) "core.bot_text" else "qq.text"
            this.sender = sender
        }
    }

    @Test
    fun `关注态新消息 prompt 包含状态、sequence 和上次决策`() {
        val snapshot = ObservationSnapshot(
            streamId = "P-1",
            version = 3,
            state = FlowState.FOCUSED,
            entries = listOf(
                ObservationEntry(message(1, "在吗"), ObservationEntry.Origin.EXTERNAL, replied = true),
                ObservationEntry(message(2, "在的", bot = true), ObservationEntry.Origin.SELF, replied = true),
                ObservationEntry(message(3, "看这个"), ObservationEntry.Origin.EXTERNAL, replied = false),
            ),
            newSequences = setOf(3L),
            lastObservedSequence = 2L,
            lastActivityAt = System.currentTimeMillis(),
            trigger = ObservationTrigger.NEW_MESSAGE,
            triggerSequence = 3L,
            lastDecision = "focus",
        )

        val items = builder.build(snapshot)

        val system = items.first { it.role == MessageContextItem.Role.SYSTEM }.contentList.first().asText()!!
        assertTrue(system.contains("观察者"))
        assertTrue(system.contains("speak"))
        assertTrue(system.contains("focus_watch"))

        val users = items.filter { it.role == MessageContextItem.Role.USER }
            .map { it.contentList.first().asText()!! }
        assertTrue(users.any { it.contains("(seq=3)") })
        assertTrue(users.any { it.contains("在的") }.not(), "Bot 消息应使用 assistant 角色")

        val assistants = items.filter { it.role == MessageContextItem.Role.ASSISTANT }
            .map { it.contentList.first().asText()!! }
        assertTrue(assistants.contains("在的"))

        val tail = users.last()
        assertTrue(tail.contains("当前状态：FOCUSED"))
        assertTrue(tail.contains("新消息 sequence：3"))
        assertTrue(tail.contains("上次决策：focus"))
        assertTrue(tail.contains("关注态新消息"))
    }

    @Test
    fun `ACTIVE 无新消息 prompt 提示不要主动刷屏`() {
        val snapshot = ObservationSnapshot(
            streamId = "P-1",
            version = 1,
            state = FlowState.ACTIVE,
            entries = listOf(ObservationEntry(message(1, "在吗"), ObservationEntry.Origin.EXTERNAL, replied = true)),
            trigger = ObservationTrigger.PERIODIC,
        )

        val users = builder.build(snapshot)
            .filter { it.role == MessageContextItem.Role.USER }
            .map { it.contentList.first().asText()!! }

        assertTrue(users.last().contains("当前状态：ACTIVE"))
        assertTrue(users.last().contains("没有新消息"))
        assertTrue(users.last().contains("不要主动发言"))
    }
}
