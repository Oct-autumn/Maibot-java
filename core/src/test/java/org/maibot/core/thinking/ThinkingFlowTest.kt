package org.maibot.core.thinking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.maibot.sdk.storage.db.dao.InteractionEntity
import org.maibot.sdk.storage.db.dao.Message

class ThinkingFlowTest {
    private fun message(sequence: Long, text: String, bot: Boolean = false): Message {
        val sender = InteractionEntity().apply {
            this.id = if (bot) BOT_ENTITY_ID else sequence
            this.platform = "qq"
            this.platformUserId = if (bot) "10000" else "2000$sequence"
            this.nickname = if (bot) "小七" else "用户$sequence"
        }
        return Message().apply {
            this.sequence = sequence
            this.timestamp = 1_700_000_000_000 + sequence
            this.promptStr = text
            this.rawContentJson = """{"text":"$text"}"""
            this.objectType = if (bot) ThinkingFlow.BOT_MESSAGE_OBJECT_TYPE else "qq.text"
            this.sender = sender
        }
    }

    @Test
    fun `外部消息进入窗口后成为待回复触发`() {
        val flow = ThinkingFlow(10, "P-1")

        flow.addExternalMessage(message(1, "在吗"))

        val pending = flow.pendingSnapshot()
        assertEquals(1L, pending?.triggerSequence)
        assertEquals(1L, pending?.firstUnrepliedExternal?.sequence)
        assertFalse(requireNotNull(pending).firstUnrepliedExternal!!.replied)
        assertEquals(1L, flow.version)
    }

    @Test
    fun `Bot 消息不产生待回复触发`() {
        val flow = ThinkingFlow(10, "P-1")
        flow.addExternalMessage(message(1, "在吗"))

        flow.addBotMessage(message(2, "在的", bot = true), listOf(1))

        assertNull(flow.pendingSnapshot())
        assertEquals(1L, flow.version, "Bot 消息不应增加窗口版本")
        assertTrue(flow.snapshot().entries.first { it.sequence == 1L }.replied)
    }

    @Test
    fun `从历史恢复时按 reply_to 标记已回复`() {
        val flow = ThinkingFlow(10, "P-1")
        val botMessage = message(2, "在的", bot = true).apply {
            rawContentJson = """{"text":"在的","reply_to_sequence":[1]}"""
        }

        flow.restoreHistory(listOf(message(1, "在吗"), botMessage, message(3, "再看这个")))

        val snapshot = flow.snapshot()
        assertTrue(snapshot.entries.first { it.sequence == 1L }.replied)
        assertFalse(snapshot.entries.first { it.sequence == 3L }.replied)
        assertEquals(3L, flow.pendingSnapshot()?.triggerSequence)
    }

    @Test
    fun `窗口超过容量时淘汰最旧消息`() {
        val flow = ThinkingFlow(2, "P-1")

        flow.addExternalMessage(message(1, "a"))
        flow.addExternalMessage(message(2, "b"))
        flow.addExternalMessage(message(3, "c"))

        val sequences = flow.snapshot().entries.map { it.sequence }
        assertEquals(listOf(2L, 3L), sequences)
    }

    @Test
    fun `停止后 awaitPending 立即返回 null`() {
        val flow = ThinkingFlow(10, "P-1")
        flow.stopObserving()

        assertNull(flow.awaitPending())
    }

    companion object {
        private const val BOT_ENTITY_ID = 999L
    }
}
