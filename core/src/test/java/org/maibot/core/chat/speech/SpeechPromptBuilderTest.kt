package org.maibot.core.chat.speech

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `历史保持稳定，只有目标消息带未读标记`() {
        val entries = listOf(
            ObservationEntry(message(1, "在吗"), ObservationEntry.Origin.EXTERNAL, replied = true),
            ObservationEntry(message(2, "在的", bot = true), ObservationEntry.Origin.SELF, replied = true),
            ObservationEntry(message(3, "看看这个"), ObservationEntry.Origin.EXTERNAL, replied = false),
        )
        val snapshot = ObservationSnapshot(
            streamId = "P-1",
            version = 3,
            state = FlowState.ACTIVE,
            entries = entries,
            newSequences = setOf(3L),
            trigger = ObservationTrigger.NEW_MESSAGE,
            triggerSequence = 3L,
        )

        val items = builder.build(snapshot, listOf(3L), "回应对方刚才发的图片")

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
        assertTrue(tail.contains("sequence：3"))
        assertTrue(tail.contains("回应对方刚才发的图片"))
        assertTrue(tail.contains("[未读]"))
    }

    @Test
    fun `没有目标消息时走主动话题分支`() {
        val snapshot = ObservationSnapshot(
            streamId = "P-1",
            version = 1,
            state = FlowState.FOCUSED,
            entries = listOf(ObservationEntry(message(1, "在吗"), ObservationEntry.Origin.EXTERNAL, replied = true)),
            trigger = ObservationTrigger.PERIODIC,
        )

        val items = builder.build(snapshot, emptyList(), "主动开启一个轻松的话题")

        val userTexts = items.filter { it.role == MessageContextItem.Role.USER }
            .map { it.contentList.first().asText()!! }
        assertTrue(userTexts.none { it.contains("[未读]") })
        val tail = userTexts.last()
        assertTrue(tail.contains("当前没有待回复消息"))
        assertTrue(tail.contains("主动开启一个轻松的话题"))
        assertTrue(tail.contains("主动开启或推进"))
        assertFalse(tail.contains("本次要回复的消息 sequence"))
    }
}
