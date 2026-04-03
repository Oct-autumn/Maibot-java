package org.maibot.sdk

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
     * 提交任务到执行器
     * 
     * @param task 任务Callable
     * @param virT 是否使用虚拟线程
     * @return 任务Future
     */
    abstract fun <T> submit(virT: Boolean, task: () -> T): CompletableFuture<T>

    fun submit(virT: Boolean, task: Runnable): CompletableFuture<Unit> {
        return submit(virT) {
            task.run()
        }
    }
}
