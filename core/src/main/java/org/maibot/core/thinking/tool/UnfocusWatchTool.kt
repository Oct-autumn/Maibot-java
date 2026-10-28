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
 * 退出关注工具
 *
 * 只负责触发交互流的状态切换；间隔 clamp、cap 计数重置和活动时间刷新
 * 都在 `ThinkingFlow.unfocus()` 中完成。
 */
@Component
class UnfocusWatchTool
@AutoInject private constructor(
    private val flowRegistry: ThinkingFlowRegistry,
) : Tool(NAME, DESCRIPTION, EmptyParams::class.java), StateActionTool {
    override fun execute(context: ToolCallContext): ToolResult {
        val streamId = context.streamId
            ?: return ToolResult("缺少交互流 ID", isError = true)
        val flow = flowRegistry.get(streamId)
            ?: return ToolResult("思维流不存在: $streamId", isError = true)

        if (flow.state != FlowState.FOCUSED) {
            return ToolResult("当前不在关注状态。")
        }
        if (!flow.unfocus()) {
            return ToolResult("状态切换失败：思维流已停止。", isError = true)
        }
        return ToolResult("已退出关注状态。")
    }

    companion object {
        const val NAME = "unfocus_watch"

        private const val DESCRIPTION = "退出关注状态，回到周期性观察。当前话题不再需要紧密跟随时调用。"
    }
}
