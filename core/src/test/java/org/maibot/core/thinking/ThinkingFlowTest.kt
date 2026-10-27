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
    fun `外部消息进入窗口后成为新消息，但不直接改状态`() {
        val flow = ThinkingFlow(10, "P-1")

        flow.addExternalMessage(message(1, "在吗"))

        val snapshot = flow.snapshot()
        assertEquals(FlowState.INACTIVE, snapshot.state)
        assertEquals(setOf(1L), snapshot.newSequences)
        assertEquals(1L, snapshot.triggerSequence)
        assertTrue(flow.hasUnobservedMessages())
        assertEquals(1L, flow.version)
    }

    @Test
    fun `Bot 消息不增加版本且标记回复`() {
        val flow = ThinkingFlow(10, "P-1")
        flow.addExternalMessage(message(1, "在吗"))

        flow.addBotMessage(message(2, "在的", bot = true), listOf(1))

        val snapshot = flow.snapshot()
        assertEquals(1L, flow.version, "Bot 消息不应增加窗口版本")
        assertTrue(snapshot.entries.first { it.sequence == 1L }.replied)
        assertTrue(flow.hasUnobservedMessages(), "Bot 回复不代表示已被观察")

        flow.markObserved(1L, "speak")
        assertFalse(flow.hasUnobservedMessages())
    }

    @Test
    fun `标记已观察后新消息集合清空`() {
        val flow = ThinkingFlow(10, "P-1")
        flow.addExternalMessage(message(1, "在吗"))

        flow.markObserved(1L, "wait")

        val snapshot = flow.snapshot()
        assertTrue(snapshot.newSequences.isEmpty())
        assertFalse(flow.hasUnobservedMessages())
        assertEquals("wait", snapshot.lastDecision)
    }

    @Test
    fun `从历史恢复时按 reply_to 标记已回复且不重放旧消息`() {
        val flow = ThinkingFlow(10, "P-1")
        val botMessage = message(2, "在的", bot = true).apply {
            rawContentJson = """{"text":"在的","reply_to_sequence":[1]}"""
        }

        flow.restoreHistory(listOf(message(1, "在吗"), botMessage, message(3, "再看这个")))

        val snapshot = flow.snapshot()
        assertTrue(snapshot.entries.first { it.sequence == 1L }.replied)
        assertFalse(snapshot.entries.first { it.sequence == 3L }.replied)
        assertEquals(3L, flow.lastObservedSequence)
        assertTrue(snapshot.newSequences.isEmpty(), "恢复的历史不应全部算作新消息")
    }

    @Test
    fun `恢复状态与观察位置`() {
        val flow = ThinkingFlow(10, "P-1")
        flow.restoreHistory(listOf(message(1, "在吗"), message(2, "在的", bot = true)))

        flow.restoreState(
            PersistedFlowState(
                state = FlowState.FOCUSED,
                lastActivityAt = 2_000_000_000_000L,
                lastObservedSequence = 2L,
                lastDecision = "focus",
            )
        )

        val snapshot = flow.snapshot()
        assertEquals(FlowState.FOCUSED, snapshot.state)
        assertEquals(2L, snapshot.lastObservedSequence)
        assertEquals("focus", snapshot.lastDecision)
        assertEquals(2_000_000_000_000L, flow.lastActivityAt)
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
    fun `等待超时返回 TIMEOUT`() {
        val flow = ThinkingFlow(10, "P-1")

        val result = flow.awaitChange(System.currentTimeMillis() + 20)

        assertEquals(FlowWakeup.TIMEOUT, result)
    }

    @Test
    fun `等待新消息返回 MESSAGE`() {
        val flow = ThinkingFlow(10, "P-1")
        val worker = Thread {
            Thread.sleep(20)
            flow.addExternalMessage(message(1, "在吗"))
        }

        worker.start()
        val result = flow.awaitChange(System.currentTimeMillis() + 1_000)
        worker.join()

        assertEquals(FlowWakeup.MESSAGE, result)
    }

    @Test
    fun `停止后等待立即返回 STOPPED`() {
        val flow = ThinkingFlow(10, "P-1")
        flow.stopObserving()

        assertEquals(FlowWakeup.STOPPED, flow.awaitChange(null))
        assertNull(flow.persistedState().lastDecision)
    }

    companion object {
        private const val BOT_ENTITY_ID = 999L
    }
}
