package org.maibot.core.thinking

/**
 * 思维流状态持久化接口
 *
 * 抽出来是为了让 FlowObserver 可测试，不直接依赖数据库实现。
 */
fun interface FlowStatePersister {
    fun saveThinkingFlow(flow: ThinkingFlow)
}
