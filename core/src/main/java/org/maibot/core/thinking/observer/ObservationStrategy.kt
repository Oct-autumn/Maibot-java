package org.maibot.core.thinking.observer

import org.maibot.core.thinking.ObservationSnapshot
import org.maibot.core.thinking.ThinkingFlow

/**
 * 观察策略
 *
 * 决定「这次观察做什么」：调用 ObserverAI、执行工具循环，并返回状态机需要的决策。
 * 策略只依赖模型/工具等能力，不负责观察循环的时序与状态迁移。
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
