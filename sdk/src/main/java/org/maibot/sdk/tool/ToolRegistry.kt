package org.maibot.sdk.tool

/**
 * 工具注册表
 *
 * Core 实现该注册表并注册内置工具；mod 可以注册自己提供的工具。
 */
abstract class ToolRegistry {
    /**
     * 注册（或覆盖）一个工具
     */
    abstract fun register(tool: Tool)

    /**
     * 按名称获取工具
     *
     * @return 未注册时返回 null
     */
    abstract fun get(name: String): Tool?

    /**
     * 按名称列表批量获取工具；未注册的名称会被忽略
     */
    abstract fun list(names: List<String>): List<Tool>

    /**
     * 获取当前注册的全部工具
     */
    abstract fun all(): List<Tool>
}
