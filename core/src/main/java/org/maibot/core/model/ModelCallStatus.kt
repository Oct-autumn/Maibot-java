package org.maibot.core.model

/**
 * 一次 LLM 任务的状态
 *
 * 粒度是「一次任务调用」，不是单次 HTTP 请求/重试：
 * - IN_PROGRESS：任务已开始，正在尝试模型（含重试与候选模型切换）；
 * - SUCCESS：任务最终成功；
 * - FAILED：所有候选模型/重试都失败。
 */
enum class ModelCallStatus {
    IN_PROGRESS,
    SUCCESS,
    FAILED,
}
