package org.maibot.core.chat.speech

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.maibot.core.config.CoreConfig
import org.maibot.core.thinking.ObservationEntry
import org.maibot.core.thinking.ObservationSnapshot
import org.maibot.sdk.model.payload.MessageContextItem
import org.maibot.sdk.storage.db.dao.InteractionEntity
import org.maibot.sdk.storage.db.dao.Message

class SpeechPromptBuilderTest {
    private val config = CoreConfig(
        version = "test",
        botInfo = CoreConfig.BotInfo(name = "小七", aliases = listOf("小柒")),
        network = CoreConfig.Network(host = "127.0.0.1", port = 8080),
    )

    private val builder = SpeechPromptBuilder(config, object : PersonaProvider {
        override fun load(): String = "人设：测试用"
    })

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
    fun `历史保持稳定，只有末尾未回复消息带未读标记`() {
        val entries = listOf(
            ObservationEntry(message(1, "在吗"), ObservationEntry.Origin.EXTERNAL, replied = true),
            ObservationEntry(message(2, "在的", bot = true), ObservationEntry.Origin.SELF, replied = true),
            ObservationEntry(message(3, "看看这个"), ObservationEntry.Origin.EXTERNAL, replied = false),
        )
        val snapshot = ObservationSnapshot("P-1", version = 3, entries = entries, triggerSequence = 3)

        val items = builder.build(snapshot, 3L)

        assertEquals(MessageContextItem.Role.SYSTEM, items.first().role)
        val systemText = items.first().contentList.first().asText()!!
        assertTrue(systemText.contains("人设：测试用"))
        assertTrue(systemText.contains("小七"))
        assertTrue(systemText.contains("小柒"))

        val userTexts = items.filter { it.role == MessageContextItem.Role.USER }
            .map { it.contentList.first().asText()!! }
        assertTrue(userTexts.any { it.contains("在吗") && !it.contains("[未读]") })
        assertTrue(userTexts.any { it.contains("看看这个") && it.contains("[未读]") })

        val assistantTexts = items.filter { it.role == MessageContextItem.Role.ASSISTANT }
            .map { it.contentList.first().asText()!! }
        assertTrue(assistantTexts.contains("在的"))

        val tail = userTexts.last()
        assertTrue(tail.contains("当前时间"))
        assertTrue(tail.contains("sequence: 3"))
        assertTrue(tail.contains("[未读]"))
    }

    @Test
    fun `已回复的外部消息不再带未读标记`() {
        val snapshot = ObservationSnapshot(
            "P-1",
            version = 1,
            entries = listOf(ObservationEntry(message(1, "在吗"), ObservationEntry.Origin.EXTERNAL, replied = true)),
            triggerSequence = 1,
        )

        val items = builder.build(snapshot, 1L)

        val userText = items.first { it.role == MessageContextItem.Role.USER }
            .contentList.first().asText()!!
        assertFalse(userText.contains("[未读]"))
    }
}
