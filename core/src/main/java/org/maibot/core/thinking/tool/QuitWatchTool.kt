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
 * 退出观察工具
 *
 * 只负责触发交互流的状态切换；未观察消息的 pending guard、状态切换和计时重置
 * 都在 `ThinkingFlow.quit()` 中完成。
 */
@Component
class QuitWatchTool
@AutoInject private constructor(
    private val flowRegistry: ThinkingFlowRegistry,
) : Tool(NAME, DESCRIPTION, EmptyParams::class.java), StateActionTool {
    override fun execute(context: ToolCallContext): ToolResult {
        val streamId = context.streamId
            ?: return ToolResult("缺少交互流 ID", isError = true)
        val flow = flowRegistry.get(streamId)
            ?: return ToolResult("思维流不存在: $streamId", isError = true)

        if (flow.state == FlowState.INACTIVE) {
            return ToolResult("当前已不活跃。")
        }
        if (flow.hasUnobservedMessages()) {
            return ToolResult("仍有未观察消息，暂不退出观察。")
        }
        if (!flow.quit()) {
            return ToolResult("状态切换失败：思维流已停止。", isError = true)
        }
        return ToolResult("已退出观察。")
    }

    companion object {
        const val NAME = "quit_watch"

        private const val DESCRIPTION = "退出观察，回到不活跃状态；下一条新消息会重新激活。对话已经结束或不需要继续关注时调用。"
    }
}
