package org.maibot.core.thinking.observer

import org.maibot.core.thinking.ObservationSnapshot
import org.maibot.core.thinking.ThinkingFlow
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.tool.ToolCallContext
import org.maibot.sdk.tool.ToolRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * W/O 阶段的观察策略：只要窗口里有未回复的外部消息，就固定调用一次 `speak`
 *
 * 这个策略是消融实验用的，不接 ObserverAI；后续接入 ObserverAI 时整体替换。
 */
@Component
class AlwaysSpeakStrategy
@AutoInject private constructor(
    private val toolRegistry: ToolRegistry,
) : ObservationStrategy {
    /**
     * 连续失败次数（按触发消息 sequence）。
     *
     * 模型或出站通道暂时不可用时不能每秒重试刷日志，这里做指数退避；
     * 成功后清理。W/O 阶段只求闭环，不需要更复杂的重试策略。
     */
    private val failures = ConcurrentHashMap<Long, Int>()

    override fun observe(flow: ThinkingFlow, snapshot: ObservationSnapshot): ObservationResult {
        val trigger = snapshot.triggerSequence
            ?: snapshot.firstUnrepliedExternal?.sequence
            ?: return ObservationResult.Continue

        val speakTool = toolRegistry.get(TOOL_NAME) ?: run {
            log.error("未注册 {} 工具，W/O 无法发言", TOOL_NAME)
            return ObservationResult.Retry(5_000)
        }

        val result = speakTool.execute(
            ToolCallContext(
                streamId = flow.id,
                triggerSequence = trigger,
                windowVersion = snapshot.version,
                toolCallId = null,
                arguments = null,
            )
        )

        return if (result.isError) {
            val attempt = failures.merge(trigger, 1, Int::plus) ?: 1
            val delay = minOf(1_000L shl (attempt - 1).coerceAtMost(5), MAX_RETRY_DELAY_MS)
            log.warn(
                "发言失败（stream: {}, trigger: {}, 第 {} 次），{}ms 后重试: {}",
                flow.id, trigger, attempt, delay, result.content
            )
            ObservationResult.Retry(delay)
        } else {
            failures.remove(trigger)
            ObservationResult.Continue
        }
    }

    companion object {
        const val TOOL_NAME = "speak"

        private const val MAX_RETRY_DELAY_MS = 30_000L

        private val log: Logger = LoggerFactory.getLogger(AlwaysSpeakStrategy::class.java)
    }
}
