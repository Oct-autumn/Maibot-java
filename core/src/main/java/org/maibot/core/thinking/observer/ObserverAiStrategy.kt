package org.maibot.core.thinking.observer

import org.maibot.core.config.CoreConfig
import org.maibot.core.model.ModelCaller
import org.maibot.core.thinking.ObservationSnapshot
import org.maibot.core.thinking.ThinkingFlow
import org.maibot.core.thinking.tool.FocusWatchTool
import org.maibot.core.thinking.tool.QuitWatchTool
import org.maibot.core.thinking.tool.SpeakTool
import org.maibot.core.thinking.tool.StateActionTool
import org.maibot.core.thinking.tool.TerminalObservationTool
import org.maibot.core.thinking.tool.UnfocusWatchTool
import org.maibot.core.thinking.tool.WaitAndWatchTool
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.Value
import org.maibot.sdk.model.ModelCallContext
import org.maibot.sdk.model.ModelCallRequest
import org.maibot.sdk.model.payload.AvailableFunctionItem
import org.maibot.sdk.model.payload.MessageContextItem
import org.maibot.sdk.model.payload.ToolCall
import org.maibot.sdk.model.payload.ToolChoice
import org.maibot.sdk.tool.Tool
import org.maibot.sdk.tool.ToolCallContext
import org.maibot.sdk.tool.ToolRegistry
import org.maibot.sdk.tool.ToolResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * ObserverAI 策略
 *
 * 调观察模型，执行它返回的工具调用，把工具结果回灌模型，直到它调用终止工具。
 * 状态迁移由工具触发、由 ThinkingFlow 完成；这里只负责工具循环和结果归类。
 */
@Component
class ObserverAiStrategy
@AutoInject private constructor(
    @param:Value($$"${*}") private val config: CoreConfig,
    private val modelCaller: ModelCaller,
    private val toolRegistry: ToolRegistry,
    private val promptBuilder: ObserverPromptBuilder,
) : ObservationStrategy {
    private val toolDefinitions: List<AvailableFunctionItem> by lazy {
        TOOL_ORDER.mapNotNull { name ->
            toolRegistry.get(name)?.let {
                AvailableFunctionItem.of(it.name, it.description, it.paramsClass)
            }
        }
    }

    override fun observe(flow: ThinkingFlow, snapshot: ObservationSnapshot): ObservationResult {
        val messages = promptBuilder.build(snapshot).toMutableList()
        var spoke = false
        var round = 0

        while (round < config.thinking.observer.maxToolRounds) {
            round++

            val response = try {
                modelCaller.call(
                    taskName = config.thinking.observer.modelTask,
                    request = ModelCallRequest(
                        messageContext = messages,
                        availableFunc = toolDefinitions,
                        callContext = ModelCallContext(
                            streamId = snapshot.streamId,
                            triggerSequence = snapshot.triggerSequence,
                        ),
                        // 观察模型必须用工具表达决策，避免返回普通文本后无法驱动状态机
                        toolChoice = ToolChoice.REQUIRED,
                    ),
                    timeoutSec = config.thinking.observer.timeoutSec,
                )
            } catch (e: Exception) {
                log.warn("观察模型调用失败（stream: {}, round: {}）", flow.id, round, e)
                // 已经发过言就不要再重试整轮，否则可能重复发送
                return if (spoke) ObservationResult.Wait else ObservationResult.Retry()
            }

            val toolCalls = response.toolCalls.orEmpty()
            if (toolCalls.isEmpty()) {
                log.debug("观察模型没有返回工具调用，按 wait 处理（stream: {}, round: {}）", flow.id, round)
                return ObservationResult.Wait
            }

            val calls = toolCalls.map {
                ToolCall(
                    id = it.id ?: "call_${UUID.randomUUID()}",
                    name = it.toolName,
                    arguments = it.toolArgs,
                )
            }

            // assistant 的 tool_calls 必须原样带回，否则下一轮 TOOL 消息无法关联
            messages.add(MessageContextItem.ofAssistantWithToolCalls(emptyList(), calls))

            for (call in calls) {
                val tool = toolRegistry.get(call.name)
                val result = if (tool == null) {
                    ToolResult("未知工具: ${call.name}", isError = true)
                } else {
                    executeTool(flow, snapshot, call, tool, spoke)
                }
                messages.add(
                    MessageContextItem.ofTool(
                        listOf(MessageContextItem.Content.ofText(result.content)),
                        call.id,
                    )
                )

                if (call.name == SpeakTool.TOOL_NAME && !result.isError) {
                    spoke = true
                }

                // 终止工具由自身实现触发状态切换，这里只按类型结束本轮
                if (tool is StateActionTool) {
                    return ObservationResult.StateAction
                }
                if (tool is TerminalObservationTool) {
                    return ObservationResult.Wait
                }
            }
        }

        log.warn("观察工具轮数超过上限 {}（stream: {}），按 wait 收尾", config.thinking.observer.maxToolRounds, flow.id)
        return ObservationResult.Wait
    }

    private fun executeTool(
        flow: ThinkingFlow,
        snapshot: ObservationSnapshot,
        call: ToolCall,
        tool: Tool,
        spoke: Boolean,
    ): ToolResult {
        if (call.name == SpeakTool.TOOL_NAME && spoke) {
            return ToolResult("本次观察已经发言过一次，不能重复发言", isError = true)
        }

        return try {
            tool.execute(
                ToolCallContext(
                    streamId = snapshot.streamId,
                    triggerSequence = snapshot.triggerSequence,
                    windowVersion = snapshot.version,
                    toolCallId = call.id,
                    arguments = call.arguments,
                )
            )
        } catch (e: Exception) {
            log.error("工具执行失败（stream: {}, tool: {}）", flow.id, call.name, e)
            ToolResult("工具执行失败: ${e.message}", isError = true)
        }
    }

    companion object {
        private val TOOL_ORDER = listOf(
            SpeakTool.TOOL_NAME,
            WaitAndWatchTool.NAME,
            FocusWatchTool.NAME,
            UnfocusWatchTool.NAME,
            QuitWatchTool.NAME,
        )

        private val log: Logger = LoggerFactory.getLogger(ObserverAiStrategy::class.java)
    }
}
