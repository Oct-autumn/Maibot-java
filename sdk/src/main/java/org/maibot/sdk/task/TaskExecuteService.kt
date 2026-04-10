package org.maibot.sdk.task

import org.maibot.sdk.ioc.DestroyableComponent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

/**
 * 任务执行服务接口
 */
@Suppress("unused")
abstract class TaskExecuteService : DestroyableComponent {
    abstract fun executor(): ExecutorService

    abstract fun virtualExecutor(): ExecutorService

    /**
     * 提交带有执行上下文的任务，并返回一个Future对象用于获取结果
     *
     * @param virT 是否使用虚拟线程
     * @param task 任务ManagedTask
     * @return 任务Future
     */
    abstract fun <T> submit(virT: Boolean, task: ManagedTask<T>): CompletableFuture<T>

    /**
     * 提交普通任务，并返回一个Future对象用于获取结果
     *
     * @param virT 是否使用虚拟线程
     * @param task 任务Callable
     */
    abstract fun <T> submit(virT: Boolean, task: () -> T): CompletableFuture<T>

    /**
     * 提交普通任务，并返回一个Future对象用于获取任务执行状态
     */
    fun <T> submit(virT: Boolean, task: Runnable): CompletableFuture<Unit> {
        return submit(virT) {
            task.run()
        }
    }
}