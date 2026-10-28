package org.maibot.core.thinking.tool

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.maibot.core.ioc.Instance
import org.maibot.core.thinking.FlowState
import org.maibot.core.thinking.ThinkingFlow
import org.maibot.core.thinking.ThinkingFlowRegistry
import org.maibot.core.thinking.observer.FlowTiming
import org.maibot.sdk.storage.db.dao.InteractionEntity
import org.maibot.sdk.storage.db.dao.Message
import org.maibot.sdk.tool.ToolCallContext

class StateTransitionToolTest {
    private fun flow(streamId: String): ThinkingFlow {
        val flow = ThinkingFlow(10, streamId, TIMING)
        registry().computeIfAbsent(streamId) { flow }
        return flow
    }

    private fun registry(): ThinkingFlowRegistry {
        return Instance.get(ThinkingFlowRegistry::class.java)
    }

    private fun context(streamId: String): ToolCallContext {
        return ToolCallContext(streamId, null, null, null, null)
    }

    @Test
    fun `focus_watch 触发 ThinkingFlow 的状态迁移`() {
        val streamId = "P-focus-tool"
        val flow = flow(streamId)
        flow.transitionTo(FlowState.ACTIVE, "test")
        val tool = Instance.createInstance(FocusWatchTool::class.java)

        val result = tool.execute(context(streamId))

        assertFalse(result.isError)
        assertEquals(FlowState.FOCUSED, flow.state)
        registry().remove(streamId)
    }

    @Test
    fun `unfocus_watch 从关注退回 ACTIVE`() {
        val streamId = "P-unfocus-tool"
        val flow = flow(streamId)
        flow.transitionTo(FlowState.FOCUSED, "test")
        val tool = Instance.createInstance(UnfocusWatchTool::class.java)

        val result = tool.execute(context(streamId))

        assertFalse(result.isError)
        assertEquals(FlowState.ACTIVE, flow.state)
        registry().remove(streamId)
    }

    @Test
    fun `quit_watch 遇到未观察消息时不迁移`() {
        val streamId = "P-quit-tool"
        val flow = flow(streamId)
        flow.transitionTo(FlowState.ACTIVE, "test")
        flow.addExternalMessage(message(1))
        val tool = Instance.createInstance(QuitWatchTool::class.java)

        val result = tool.execute(context(streamId))

        assertFalse(result.isError)
        assertTrue(result.content.contains("未观察"))
        assertEquals(FlowState.ACTIVE, flow.state)
        registry().remove(streamId)
    }

    private fun message(sequence: Long): Message {
        val sender = InteractionEntity().apply {
            id = sequence
            platform = "qq"
            platformUserId = "2000$sequence"
            nickname = "用户$sequence"
        }
        return Message().apply {
            this.sequence = sequence
            timestamp = 1_700_000_000_000 + sequence
            promptStr = "消息$sequence"
            rawContentJson = """{"text":"消息$sequence"}"""
            objectType = "qq.text"
            this.sender = sender
        }
    }

    companion object {
        private val TIMING = FlowTiming(
            activeBaseIntervalMs = 30_000,
            activeMaxIntervalMs = 120_000,
            focusBaseIntervalMs = 15_000,
            focusMaxIntervalMs = 300_000,
            growthFactor = 2.0,
        )
    }
}
