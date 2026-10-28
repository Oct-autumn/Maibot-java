package org.maibot.core.thinking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.maibot.core.thinking.observer.FlowTiming
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
        val flow = newFlow()

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
        val flow = newFlow()
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
        val flow = newFlow()
        flow.addExternalMessage(message(1, "在吗"))

        flow.markObserved(1L, "wait")

        val snapshot = flow.snapshot()
        assertTrue(snapshot.newSequences.isEmpty())
        assertFalse(flow.hasUnobservedMessages())
        assertEquals("wait", snapshot.lastDecision)
    }

    @Test
    fun `从历史恢复时按 reply_to 标记已回复且不重放旧消息`() {
        val flow = newFlow()
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
        val flow = newFlow()
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
        val flow = newFlow(2)

        flow.addExternalMessage(message(1, "a"))
        flow.addExternalMessage(message(2, "b"))
        flow.addExternalMessage(message(3, "c"))

        val sequences = flow.snapshot().entries.map { it.sequence }
        assertEquals(listOf(2L, 3L), sequences)
    }

    @Test
    fun `等待超时返回 TIMEOUT`() {
        val flow = newFlow()

        val result = flow.awaitChange(System.currentTimeMillis() + 20)

        assertEquals(FlowWakeup.TIMEOUT, result)
    }

    @Test
    fun `等待前已有未观察消息时立即返回 MESSAGE`() {
        val flow = newFlow()
        flow.addExternalMessage(message(1, "在吗"))

        val result = flow.awaitChange(System.currentTimeMillis() + 1_000)
        assertEquals(FlowWakeup.MESSAGE, result)

        // 同一条未观察消息只唤醒一次，下一次应正常等到超时，而不是空转
        val second = flow.awaitChange(System.currentTimeMillis() + 20)
        assertEquals(FlowWakeup.TIMEOUT, second)
    }

    @Test
    fun `等待新消息返回 MESSAGE`() {
        val flow = newFlow()
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
        val flow = newFlow()
        flow.stopObserving()

        assertEquals(FlowWakeup.STOPPED, flow.awaitChange(null))
        assertNull(flow.persistedState().lastDecision)
    }

    @Test
    fun `focus 迁移沿用并 clamp 间隔，同时清空 cap 计数`() {
        val flow = newFlow()
        flow.addExternalMessage(message(1, "在吗"))
        flow.transitionTo(FlowState.ACTIVE, "test")
        flow.resetBackoff()
        flow.growBackoff()
        flow.recordCapWait()

        assertTrue(flow.focus())

        assertEquals(FlowState.FOCUSED, flow.state)
        assertEquals(60_000, flow.currentIntervalMs)
        assertEquals(0, flow.capWaitCount)
        assertTrue(flow.lastActivityAt > 0)
    }

    @Test
    fun `unfocus 把 FOCUSED 的间隔 clamp 回 ACTIVE 上限`() {
        val flow = newFlow()
        flow.transitionTo(FlowState.FOCUSED, "test")
        repeat(5) { flow.growBackoff() }
        assertEquals(300_000, flow.currentIntervalMs)

        assertTrue(flow.unfocus())

        assertEquals(FlowState.ACTIVE, flow.state)
        assertEquals(120_000, flow.currentIntervalMs)
        assertEquals(0, flow.capWaitCount)
    }

    @Test
    fun `quit 遇到未观察消息时保持当前状态`() {
        val flow = newFlow()
        flow.transitionTo(FlowState.FOCUSED, "test")
        flow.addExternalMessage(message(1, "在吗"))

        assertFalse(flow.quit())

        assertEquals(FlowState.FOCUSED, flow.state)
    }

    private fun newFlow(capacity: Int = 10): ThinkingFlow {
        return ThinkingFlow(capacity, "P-1", TIMING)
    }

    companion object {
        private const val BOT_ENTITY_ID = 999L

        private val TIMING = FlowTiming(
            activeBaseIntervalMs = 30_000,
            activeMaxIntervalMs = 120_000,
            focusBaseIntervalMs = 15_000,
            focusMaxIntervalMs = 300_000,
            growthFactor = 2.0,
        )
    }
}
