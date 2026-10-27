package org.maibot.core.thinking

/**
 * 一次观察的触发原因
 */
enum class ObservationTrigger {
    /** INACTIVE 被外部消息激活后的首次观察 */
    ACTIVATION,

    /** FOCUSED 下新消息触发的立即观察 */
    NEW_MESSAGE,

    /** ACTIVE / FOCUSED 的周期观察 */
    PERIODIC,
}
