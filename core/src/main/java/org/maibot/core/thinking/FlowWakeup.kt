package org.maibot.core.thinking

/**
 * 观察线程一次等待的结果
 */
enum class FlowWakeup {
    /** 等待期间有新外部消息进入窗口 */
    MESSAGE,

    /** 等待超时，需要检查 tick / idle deadline */
    TIMEOUT,

    /** 流已请求停止观察 */
    STOPPED,
}
