package org.maibot.core.tool

import org.maibot.core.thinking.tool.SpeakTool
import org.maibot.core.thinking.tool.FocusWatchTool
import org.maibot.core.thinking.tool.QuitWatchTool
import org.maibot.core.thinking.tool.UnfocusWatchTool
import org.maibot.core.thinking.tool.WaitAndWatchTool
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.tool.Tool
import org.maibot.sdk.tool.ToolRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Core 工具注册表
 *
 * 注册 ObserverAI 可用的内置工具；mod 提供的工具通过 [register] 注册进来。
 */
@Component
class ToolRegistryImpl
@AutoInject private constructor(
    speakTool: SpeakTool,
    waitAndWatchTool: WaitAndWatchTool,
    focusWatchTool: FocusWatchTool,
    unfocusWatchTool: UnfocusWatchTool,
    quitWatchTool: QuitWatchTool,
) : ToolRegistry() {
    private val tools = ConcurrentHashMap<String, Tool>()

    init {
        register(speakTool)
        register(waitAndWatchTool)
        register(focusWatchTool)
        register(unfocusWatchTool)
        register(quitWatchTool)
    }

    override fun register(tool: Tool) {
        val previous = tools.put(tool.name, tool)
        if (previous != null) {
            log.warn("工具 {} 被重新注册，已覆盖旧实现", tool.name)
        }
        log.debug("注册工具: {}", tool.name)
    }

    override fun get(name: String): Tool? = tools[name]

    override fun list(names: List<String>): List<Tool> {
        return names.mapNotNull { tools[it] }
    }

    override fun all(): List<Tool> = tools.values.toList()

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ToolRegistryImpl::class.java)
    }
}
