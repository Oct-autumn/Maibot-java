package org.maibot.core.thinking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.maibot.core.thinking.observer.FlowObserver
import org.maibot.core.thinking.observer.FlowTiming
import org.maibot.core.thinking.observer.ObservationResult
import org.maibot.core.thinking.observer.ObservationStrategy
import org.maibot.sdk.storage.db.dao.InteractionEntity
import org.maibot.sdk.storage.db.dao.Message
import java.util.concurrent.CopyOnWriteArrayList

class FlowObserverTest {
    private fun message(sequence: Long): Message {
        val sender = InteractionEntity().apply {
            this.id = sequence
            this.platform = "qq"
            this.platformUserId = "2000$sequence"
            this.nickname = "用户$sequence"
        }
        return Message().apply {
            this.sequence = sequence
            this.timestamp = 1_700_000_000_000 + sequence
            this.promptStr = "消息$sequence"
            this.rawContentJson = """{"text":"消息$sequence"}"""
            this.objectType = "qq.text"
            this.sender = sender
        }
    }

    private fun flow(timing: FlowTiming): ThinkingFlow {
        return ThinkingFlow(10, "P-1", timing)
    }

    private fun start(flow: ThinkingFlow, strategy: ObservationStrategy): Pair<FlowObserver, Thread> {
        val observer = FlowObserver(flow, strategy, FlowStatePersister { })
        val thread = Thread(observer, "flow-observer-test")
        thread.start()
        return observer to thread
    }

    private fun stop(observer: FlowObserver, thread: Thread) {
        observer.stop()
        thread.join(2_000)
        assertTrue(!thread.isAlive, "观察线程未停止")
    }

    @Test
    fun `INACTIVE 激活后立即观察，ACTIVE 新消息等待 tick`() {
        val timing = FlowTiming(
            activeBaseIntervalMs = 200,
            activeMaxIntervalMs = 200,
            focusBaseIntervalMs = 50,
            focusMaxIntervalMs = 50,
            growthFactor = 2.0,
        )
        val flow = flow(timing)
        val strategy = RecordingStrategy()
        flow.addExternalMessage(message(1))
        val (observer, thread) = start(flow, strategy)

        waitUntil(2_000) { strategy.snapshots.size >= 1 }

        assertEquals(FlowState.ACTIVE, flow.state)
        assertEquals(ObservationTrigger.ACTIVATION, strategy.snapshots[0].trigger)

        flow.addExternalMessage(message(2))
        Thread.sleep(80)
        assertEquals(1, strategy.snapshots.size, "ACTIVE 下新消息不应立即触发观察")

        waitUntil(2_000) { strategy.snapshots.size >= 2 }
        // 消息恰好等到 tick 才被观察时，触发原因应保留 NEW_MESSAGE，而不是退化成周期观察
        assertEquals(ObservationTrigger.NEW_MESSAGE, strategy.snapshots[1].trigger)
        assertTrue(strategy.snapshots[1].newSequences.contains(2L))

        stop(observer, thread)
    }

    @Test
    fun `FOCUSED 新消息立即观察`() {
        val timing = FlowTiming(
            activeBaseIntervalMs = 2_000,
            activeMaxIntervalMs = 2_000,
            focusBaseIntervalMs = 1_000,
            focusMaxIntervalMs = 1_000,
            growthFactor = 2.0,
        )
        val flow = flow(timing)
        val strategy = RecordingStrategy()
        flow.addExternalMessage(message(1))
        flow.transitionTo(FlowState.FOCUSED, "test")
        val (observer, thread) = start(flow, strategy)

        waitUntil(2_000) { strategy.snapshots.size >= 1 }
        assertEquals(FlowState.FOCUSED, flow.state)

        flow.addExternalMessage(message(2))
        waitUntil(1_000) { strategy.snapshots.size >= 2 }
        assertEquals(ObservationTrigger.NEW_MESSAGE, strategy.snapshots[1].trigger)
        assertTrue(strategy.snapshots[1].newSequences.contains(2L))

        stop(observer, thread)
    }

    @Test
    fun `ACTIVE 到达上限两轮 wait 后退出`() {
        val timing = FlowTiming(
            activeBaseIntervalMs = 20,
            activeMaxIntervalMs = 40,
            focusBaseIntervalMs = 20,
            focusMaxIntervalMs = 40,
            growthFactor = 2.0,
        )
        val flow = flow(timing)
        val strategy = RecordingStrategy()
        flow.addExternalMessage(message(1))
        val (observer, thread) = start(flow, strategy)

        waitUntil(2_000) { flow.state == FlowState.ACTIVE }
        waitUntil(3_000) { flow.state == FlowState.INACTIVE }
        assertTrue(strategy.snapshots.size >= 4, "应经历 base/grow/cap 两轮")

        stop(observer, thread)
    }

    @Test
    fun `FOCUSED 到达上限两轮 wait 后回 ACTIVE，再退出`() {
        val timing = FlowTiming(
            activeBaseIntervalMs = 30,
            activeMaxIntervalMs = 60,
            focusBaseIntervalMs = 30,
            focusMaxIntervalMs = 60,
            growthFactor = 2.0,
        )
        val flow = flow(timing)
        val strategy = RecordingStrategy()
        flow.transitionTo(FlowState.FOCUSED, "test")
        val (observer, thread) = start(flow, strategy)

        waitUntil(2_000) { flow.state == FlowState.ACTIVE }
        waitUntil(3_000) { flow.state == FlowState.INACTIVE }

        stop(observer, thread)
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(10)
        }
        assertTrue(condition(), "等待条件超时")
    }

    private class RecordingStrategy(vararg results: ObservationResult) : ObservationStrategy {
        private val remaining = ArrayDeque(results.toList())
        val snapshots = CopyOnWriteArrayList<ObservationSnapshot>()

        override fun observe(flow: ThinkingFlow, snapshot: ObservationSnapshot): ObservationResult {
            snapshots.add(snapshot)
            return remaining.removeFirstOrNull() ?: ObservationResult.Wait
        }
    }
}
