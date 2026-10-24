package org.maibot.core.thinking

import org.maibot.sdk.ioc.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 交互流到 [ThinkingFlow] 的运行时注册表
 *
 * 把「流实例的存放」从 [ThinkingFlowManager] 中拆出来，避免 SpeakTool 需要反向依赖
 * Manager（Manager → 策略 → 工具 → 流实例），保持依赖方向单向。
 */
@Component
class ThinkingFlowRegistry {
    private val flows = ConcurrentHashMap<String, ThinkingFlow>()

    fun get(streamId: String): ThinkingFlow? = flows[streamId]

    fun computeIfAbsent(streamId: String, factory: () -> ThinkingFlow): ThinkingFlow {
        return flows.computeIfAbsent(streamId) { factory() }
    }

    fun all(): Collection<ThinkingFlow> = flows.values

    fun remove(streamId: String): ThinkingFlow? = flows.remove(streamId)
}
