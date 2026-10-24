package org.maibot.sdk.tool

/**
 * 可供模型调用的工具
 *
 * Core 内置 `speak` / `wait_and_watch` / `quit_watch` 等工具；
 * mod 提供的能力（ComputerUse、WebUI 操作等）也通过继承本类注册进来。
 *
 * W/O 阶段只注册并执行 `speak`；其他工具在接入 ObserverAI 时再补。
 *
 * @param name        工具名（模型看到的 function name）
 * @param description 工具说明（模型看到的 description）
 * @param paramsClass 参数定义类，字段上的 Jackson 注解会被转换成 JSON Schema；可为空
 */
open class Tool(
    val name: String,
    val description: String = "",
    val paramsClass: Class<*>? = null,
) {
    /**
     * 执行工具调用
     *
     * @throws UnsupportedOperationException 子类未实现时抛出
     */
    open fun execute(context: ToolCallContext): ToolResult {
        throw UnsupportedOperationException("工具执行尚未实现: $name")
    }
}
