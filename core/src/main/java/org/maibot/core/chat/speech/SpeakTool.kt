package org.maibot.core.chat.speech

import org.maibot.core.chat.support.BotMessagePersister
import org.maibot.core.msg.OutgoingMessageSender
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

/**
 * `speak` 工具
 *
 * 从观察窗口生成发言、发送、落库，并把 Bot 消息追加回窗口。
 * W/O 阶段由 AlwaysSpeakStrategy 固定调用；M3b 会由 ObserverAI 决定何时调用。
 */
@Component
class SpeakTool
@AutoInject private constructor(
    private val flowRegistry: ThinkingFlowRegistry,
    private val speechGenerator: SpeechGenerator,
    private val messagePersister: BotMessagePersister,
    private val outgoingMessageSender: OutgoingMessageSender,
) : Tool(TOOL_NAME, DESCRIPTION) {
    override fun execute(context: ToolCallContext): ToolResult {
        val streamId = context.streamId
            ?: return ToolResult("缺少交互流 ID", isError = true)

        val flow = flowRegistry.get(streamId)
            ?: return ToolResult("思维流不存在: $streamId", isError = true)

        val snapshot = flow.snapshot()
        val triggerSequence = context.triggerSequence
            ?: snapshot.firstUnrepliedExternal?.sequence
            ?: return ToolResult("没有需要回复的消息", isError = true)

        val triggerEntry = snapshot.entries.firstOrNull {
            it.sequence == triggerSequence && it.origin == ObservationEntry.Origin.EXTERNAL
        } ?: return ToolResult("触发消息不在观察窗口中: $triggerSequence", isError = true)

        val target = resolveTarget(triggerEntry)
            ?: return ToolResult("无法确定回复目标会话: $streamId", isError = true)

        val replyText = try {
            speechGenerator.generate(snapshot, triggerSequence)
        } catch (e: Exception) {
            log.error("生成发言失败（stream: {}, trigger: {}）", streamId, triggerSequence, e)
            return ToolResult("生成发言失败: ${e.message}", isError = true)
        } ?: return ToolResult("模型返回空回复", isError = true)

        val sent = try {
            outgoingMessageSender.send(
                OutgoingMessage.text(target.platform, target.platformTargetId, replyText)
            )
        } catch (e: Exception) {
            log.error("发送发言失败（stream: {}, trigger: {}）", streamId, triggerSequence, e)
            return ToolResult("发送发言失败: ${e.message}", isError = true)
        }

        if (!sent) {
            return ToolResult("出站通道不可用", isError = true)
        }

        try {
            val botMessage = messagePersister.persist(streamId, replyText, listOf(triggerSequence))
            flow.addBotMessage(botMessage, listOf(triggerSequence))
        } catch (e: Exception) {
            // 已经发出去了，不能返回错误触发重试，否则会重复发言；
            // 标记为已回复，至少保证观察线程不会因为原消息仍未读而再次发言。
            flow.markReplied(listOf(triggerSequence))
            log.error("Bot 回复落库/回写窗口失败（stream: {}, trigger: {}）", streamId, triggerSequence, e)
        }

        return ToolResult(replyText)
    }

    /**
     * 从触发消息的交互流解析平台与目标 ID
     */
    private fun resolveTarget(entry: ObservationEntry): ConversationTarget? {
        val stream = entry.message.stream ?: return null
        if (stream.type != StreamType.PRIVATE) {
            // W/O 只处理私聊；群聊在 M3b 再接
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

        private const val DESCRIPTION = "用当前观察窗口生成并发送一条聊天回复。"

        private val log: Logger = LoggerFactory.getLogger(SpeakTool::class.java)
    }
}
