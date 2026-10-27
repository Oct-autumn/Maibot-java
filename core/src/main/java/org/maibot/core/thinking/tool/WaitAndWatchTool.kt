package org.maibot.core.thinking.tool

import org.maibot.sdk.ioc.Component
import org.maibot.sdk.tool.EmptyParams
import org.maibot.sdk.tool.Tool
import org.maibot.sdk.tool.ToolCallContext
import org.maibot.sdk.tool.ToolResult

/**
 * 继续观察工具
 *
 * 只表达「保持当前观察状态」的决策；状态迁移由 FlowObserver 执行。
 */
@Component
class WaitAndWatchTool : Tool(NAME, DESCRIPTION, EmptyParams::class.java) {
    override fun execute(context: ToolCallContext): ToolResult {
        return ToolResult("保持当前观察状态。")
    }

    companion object {
        const val NAME = "wait_and_watch"

        private const val DESCRIPTION = "结束本次观察，保持当前观察状态；没有需要发言或调整关注度时调用。"
    }
}
