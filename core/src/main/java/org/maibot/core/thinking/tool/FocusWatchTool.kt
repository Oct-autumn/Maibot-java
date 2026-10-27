package org.maibot.core.thinking.tool

import org.maibot.sdk.ioc.Component
import org.maibot.sdk.tool.EmptyParams
import org.maibot.sdk.tool.Tool
import org.maibot.sdk.tool.ToolCallContext
import org.maibot.sdk.tool.ToolResult

/**
 * 进入关注工具
 *
 * 只表达「提高关注度」的决策；实际状态迁移由 FlowObserver 执行。
 */
@Component
class FocusWatchTool : Tool(NAME, DESCRIPTION, EmptyParams::class.java) {
    override fun execute(context: ToolCallContext): ToolResult {
        return ToolResult("已登记进入关注状态。")
    }

    companion object {
        const val NAME = "focus_watch"

        private const val DESCRIPTION = "进入关注状态：之后每条新消息都会立即触发观察，并保留周期主动观察。"
    }
}
