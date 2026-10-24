package org.maibot.core.model

/**
 * 模型调用记录器接口
 *
 * 粒度是「一次 LLM 任务」：开始时插入 IN_PROGRESS，结束时更新为 SUCCESS/FAILED。
 * 记录失败不得影响调用结果。
 */
interface ModelCallRecorder {
    /**
     * 任务开始，插入一条 IN_PROGRESS 记录
     *
     * @return 记录 ID；写入失败时返回 null（结束时仍会尝试补写最终记录）
     */
    fun start(event: ModelCallEvent): Long?

    /**
     * 任务结束，把同一行更新为最终状态
     *
     * @param recordId [start] 返回的 ID；为 null 时补插一条最终记录
     */
    fun finish(recordId: Long?, event: ModelCallEvent)
}
