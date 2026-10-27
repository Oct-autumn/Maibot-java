package org.maibot.core.thinking

/**
 * 思维流观察状态
 *
 * INACTIVE：不活跃，观察线程阻塞等待外部消息；
 * ACTIVE：周期观察，新消息不立即触发；
 * FOCUSED：关注观察，新消息立即响应，并保留主动观察。
 */
enum class FlowState {
    INACTIVE,
    ACTIVE,
    FOCUSED,
}
