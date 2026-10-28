package org.maibot.core.thinking.tool

import org.maibot.core.thinking.FlowState
import org.maibot.core.thinking.ThinkingFlowRegistry
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.tool.EmptyParams
import org.maibot.sdk.tool.Tool
import org.maibot.sdk.tool.ToolCallContext
import org.maibot.sdk.tool.ToolResult

/**
 * 进入关注工具
 *
 * 只负责触发交互流的状态切换；间隔 clamp、cap 计数重置和活动时间刷新
 * 都在 `ThinkingFlow.focus()` 中完成。
 */
@Component
class FocusWatchTool
@AutoInject private constructor(
    private val flowRegistry: ThinkingFlowRegistry,
) : Tool(NAME, DESCRIPTION, EmptyParams::class.java), StateActionTool {
    override fun execute(context: ToolCallContext): ToolResult {
        val streamId = context.streamId
            ?: return ToolResult("缺少交互流 ID", isError = true)
        val flow = flowRegistry.get(streamId)
            ?: return ToolResult("思维流不存在: $streamId", isError = true)

        if (flow.state == FlowState.FOCUSED) {
            return ToolResult("当前已处于关注状态。")
        }
        if (!flow.focus()) {
            return ToolResult("状态切换失败：思维流已停止。", isError = true)
        }
        return ToolResult("已进入关注状态。")
    }

    companion object {
        const val NAME = "focus_watch"

        private const val DESCRIPTION = "进入关注状态：之后每条新消息都会立即触发观察，并保留周期主动观察。"
    }
}
