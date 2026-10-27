package org.maibot.core.thinking.tool

import org.maibot.sdk.ioc.Component
import org.maibot.sdk.tool.EmptyParams
import org.maibot.sdk.tool.Tool
import org.maibot.sdk.tool.ToolCallContext
import org.maibot.sdk.tool.ToolResult

/**
 * 退出关注工具
 *
 * 只表达「降低关注度」的决策；实际状态迁移由 FlowObserver 执行。
 */
@Component
class UnfocusWatchTool : Tool(NAME, DESCRIPTION, EmptyParams::class.java) {
    override fun execute(context: ToolCallContext): ToolResult {
        return ToolResult("已登记退出关注状态。")
    }

    companion object {
        const val NAME = "unfocus_watch"

        private const val DESCRIPTION = "退出关注状态，回到周期性观察。当前话题不再需要紧密跟随时调用。"
    }
}
