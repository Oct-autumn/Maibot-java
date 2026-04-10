package org.maibot.sdk.task

import java.util.concurrent.CompletableFuture

/**
 * ManagedTask 是一个抽象类，代表一个可管理的任务。
 *
 * 它提供了子任务管理的功能，允许在执行过程中动态添加子任务，并在父任务取消时递归取消所有子任务。
 */
abstract class ManagedTask<T> : Runnable {
    var executor: TaskExecuteService? = null

    val childTasks = mutableSetOf<ManagedTask<*>>()
    val retVal = CompletableFuture<T>()

    fun <U> subTask(virT: Boolean, subTask: ManagedTask<U>): CompletableFuture<U> {
        childTasks.add(subTask)
        return executor!!.submit(virT, subTask).handle { ret, t ->
            // 无论子任务成功还是失败，都从子任务列表中移除，避免内存泄漏
            childTasks.remove(subTask)

            // 反馈执行结果
            if (t != null) {
                throw t
            } else {
                return@handle ret
            }
        }
    }

    fun cancel(mayInterruptIfRunning: Boolean, recursiveCancel: Boolean = true) {
        retVal.cancel(mayInterruptIfRunning)
        if (recursiveCancel) {
            for (child in childTasks) {
                child.cancel(mayInterruptIfRunning)
            }
        }
    }
    
    abstract fun invoke(): T

    override fun run() {
        try {
            val result = invoke()
            retVal.complete(result)
        } catch (e: Throwable) {
            retVal.completeExceptionally(e)
        }
    }
}