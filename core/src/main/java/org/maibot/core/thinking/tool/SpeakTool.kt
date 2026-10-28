package org.maibot.core.thinking.tool

import org.maibot.core.chat.speech.ConversationTarget
import org.maibot.core.chat.speech.SpeechGenerator
import org.maibot.core.chat.support.BotMessagePersister
import org.maibot.core.msg.OutgoingMessageSender
import org.maibot.core.thinking.FlowState
import org.maibot.core.thinking.ObservationEntry
import org.maibot.core.thinking.ObservationSnapshot
import org.maibot.core.thinking.ThinkingFlowRegistry
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.msg.OutgoingMessage
import org.maibot.sdk.storage.domain.StreamType
import org.maibot.sdk.tool.Tool
import org.maibot.sdk.tool.ToolCallContext
import org.maibot.sdk.tool.ToolResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode

/**
 * `speak` 工具
 *
 * 参数只有发言意图；上下文、目标会话和正文生成都由 Core 注入：
 * - 目标消息来自当前观察快照中尚未回复的新消息；
 * - 没有新消息时，只有 FOCUSED 的周期观察允许主动发言；
 * - 正文由 [SpeechGenerator] 生成，发送与落库在这里完成。
 */
@Component
class SpeakTool
@AutoInject private constructor(
    private val flowRegistry: ThinkingFlowRegistry,
    private val speechGenerator: SpeechGenerator,
    private val messagePersister: BotMessagePersister,
    private val outgoingMessageSender: OutgoingMessageSender,
) : Tool(TOOL_NAME, DESCRIPTION, SpeakParams::class.java) {
    override fun execute(context: ToolCallContext): ToolResult {
        val streamId = context.streamId
            ?: return ToolResult("缺少交互流 ID", isError = true)

        val flow = flowRegistry.get(streamId)
            ?: return ToolResult("思维流不存在: $streamId", isError = true)

        val snapshot = flow.snapshot()
        val intent = parseIntent(context.arguments)
        val targetSequences = snapshot.newExternalEntries
            .filter { !it.replied }
            .map { it.sequence }

        if (targetSequences.isEmpty() && snapshot.state != FlowState.FOCUSED) {
            return ToolResult("没有需要回复的新消息", isError = true)
        }

        val targetEntry = selectTargetEntry(snapshot, targetSequences)
            ?: return ToolResult("观察窗口里没有可用于定位会话的消息", isError = true)
        val target = resolveTarget(targetEntry)
            ?: return ToolResult("无法确定回复目标会话: $streamId", isError = true)

        val replyText = try {
            speechGenerator.generate(snapshot, targetSequences, intent)
        } catch (e: Exception) {
            log.error(
                "生成发言失败（stream: {}, targets: {}）",
                streamId, targetSequences.joinToString(","), e
            )
            return ToolResult("生成发言失败: ${e.message}", isError = true)
        } ?: return ToolResult("模型返回空回复", isError = true)

        val sent = try {
            outgoingMessageSender.send(
                OutgoingMessage.text(target.platform, target.platformTargetId, replyText)
            )
        } catch (e: Exception) {
            log.error("发送发言失败（stream: {}, targets: {}）", streamId, targetSequences.joinToString(","), e)
            return ToolResult("发送发言失败: ${e.message}", isError = true)
        }

        if (!sent) {
            return ToolResult("出站通道不可用", isError = true)
        }

        log.info(
            "观察发言: stream={}, targets={}, intent={}, content={}",
            streamId,
            if (targetSequences.isEmpty()) "主动" else targetSequences.joinToString(","),
            intent ?: "",
            replyText,
        )

        try {
            val botMessage = messagePersister.persist(streamId, replyText, targetSequences)
            flow.addBotMessage(botMessage, targetSequences)
        } catch (e: Exception) {
            // 已经发出去了，不能返回错误触发重试，否则会重复发言；
            // 标记为已回复，至少保证观察线程不会因为原消息仍未读而再次发言。
            flow.markReplied(targetSequences)
            log.error("Bot 回复落库/回写窗口失败（stream: {}, targets: {}）", streamId, targetSequences.joinToString(","), e)
        }

        return ToolResult(replyText)
    }

    /**
     * 选择用于解析目标会话的消息
     *
     * 优先使用本次目标消息；没有目标消息（主动发言）时退回窗口里最近一条外部消息。
     */
    private fun selectTargetEntry(snapshot: ObservationSnapshot, targetSequences: List<Long>): ObservationEntry? {
        if (targetSequences.isNotEmpty()) {
            val targetSequence = targetSequences.last()
            return snapshot.entries.firstOrNull { it.sequence == targetSequence }
        }
        return snapshot.entries.lastOrNull { it.isExternal }
    }

    private fun parseIntent(arguments: JsonNode?): String? {
        val node = arguments?.get("intent") ?: return null
        return node.asString()?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * 从触发消息的交互流解析平台与目标 ID
     */
    private fun resolveTarget(entry: ObservationEntry): ConversationTarget? {
        val stream = entry.message.stream ?: return null
        if (stream.type != StreamType.PRIVATE) {
            // 当前只处理私聊；群聊后续再扩展
            return null
        }
        val entity = stream.entity ?: return null
        val platform = entity.platform ?: return null
        val platformUserId = entity.platformUserId ?: return null
        return ConversationTarget(
            streamId = stream.id ?: return null,
            platform = platform,
            platformTargetId = platformUserId,
            isGroup = false,
        )
    }

    companion object {
        const val TOOL_NAME = "speak"

        private const val DESCRIPTION = "根据当前观察窗口和发言意图，生成并发送一条聊天回复。"

        private val log: Logger = LoggerFactory.getLogger(SpeakTool::class.java)
    }
}
