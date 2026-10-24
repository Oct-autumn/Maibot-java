package org.maibot.core.thinking.observer

import org.maibot.core.thinking.ObservationSnapshot
import org.maibot.core.thinking.ThinkingFlow

/**
 * 观察策略
 *
 * 决定「这次观察做什么」：W/O 阶段是固定发言，后续 ObserverAI 阶段是
 * 调用轻量决策模型并执行它返回的工具调用。
 *
 * 策略只依赖工具/模型等能力，不负责观察循环的时序。
 */
interface ObservationStrategy {
    /**
     * 处理一次观察
     *
     * @param flow     当前思维流
     * @param snapshot 本次观察对应的窗口快照
     */
    fun observe(flow: ThinkingFlow, snapshot: ObservationSnapshot): ObservationResult
}
