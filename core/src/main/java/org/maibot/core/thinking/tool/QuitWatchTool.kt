package org.maibot.core.thinking.tool

import org.maibot.sdk.ioc.Component
import org.maibot.sdk.tool.EmptyParams
import org.maibot.sdk.tool.Tool
import org.maibot.sdk.tool.ToolCallContext
import org.maibot.sdk.tool.ToolResult

/**
 * 退出观察工具
 *
 * 只表达「回到不活跃」的决策；实际状态迁移由 FlowObserver 执行。
 */
@Component
class QuitWatchTool : Tool(NAME, DESCRIPTION, EmptyParams::class.java) {
    override fun execute(context: ToolCallContext): ToolResult {
        return ToolResult("已登记退出观察。")
    }

    companion object {
        const val NAME = "quit_watch"

        private const val DESCRIPTION = "退出观察，回到不活跃状态；下一条新消息会重新激活。对话已经结束或不需要继续关注时调用。"
    }
}
