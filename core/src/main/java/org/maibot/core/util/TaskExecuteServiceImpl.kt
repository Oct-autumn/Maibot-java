package org.maibot.core.util

import org.maibot.core.modloader.ModManager
import org.maibot.sdk.TaskExecuteService
import org.maibot.sdk.exceptions.FatalError
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.IntUnaryOperator
import kotlin.concurrent.Volatile
import kotlin.system.exitProcess

/**
 * 任务执行器服务
 * 
 * 
 * 提供线程池和虚拟线程池用于任务执行
 */
@Component
class TaskExecuteServiceImpl
@AutoInject constructor(
    modManager: ModManager
) : TaskExecuteService() {
    @Volatile
    private var isShuttingDown = false

    @Volatile
    private var isClosed = false

    private val executor: ThreadPoolExecutor

    private val virtualExecutor: ExecutorService

    init {
        val processorCount = Runtime.getRuntime().availableProcessors()

        this.executor = ThreadPoolExecutor(
            processorCount, processorCount * 2, 60L, TimeUnit.SECONDS, LinkedBlockingQueue(), object : ThreadFactory {
                val threadNumber: AtomicInteger = AtomicInteger(1)

                override fun newThread(r: Runnable): Thread {
                    val thread = Thread(r)
                    thread.setName("T-" + threadNumber.getAndIncrement())
                    thread.setContextClassLoader(modManager.modClassLoader)

                    return thread
                }
            })

        this.virtualExecutor = Executors.newThreadPerTaskExecutor(object : ThreadFactory {
            val threadNumber: AtomicInteger = AtomicInteger(1)

            override fun newThread(r: Runnable): Thread {
                val thread = Thread.ofVirtual().unstarted(r)
                val threadId = threadNumber.getAndUpdate(IntUnaryOperator { idx: Int ->
                    return@IntUnaryOperator if (idx >= 100) 1 else idx + 1
                })
                thread.setName("VT-$threadId")
                thread.setContextClassLoader(modManager.modClassLoader)

                return thread
            }
        })
    }

    override fun executor(): ExecutorService {
        return executor
    }

    override fun virtualExecutor(): ExecutorService {
        return virtualExecutor
    }

    /**
     * 提交任务到执行器
     * 
     * @param task 任务Callable
     * @param virT 是否使用虚拟线程
     * @return 任务Future
     */
    override fun <T> submit(virT: Boolean, task: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()

        if (isShuttingDown) {
            future.completeExceptionally(
                RejectedExecutionException("任务执行器正在关闭，无法接受新任务")
            )
            return future
        } else if (isClosed) {
            future.completeExceptionally(
                RejectedExecutionException("任务执行器未启动，无法接受任务")
            )
            return future
        }

        val wrappedTask = Runnable {
            try {
                future.complete(run(task))
            } catch (e: FatalError) {
                log.error("线程发生致命错误，终止运行", e)
                exitProcess(1)
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
        }

        if (virT) {
            this.virtualExecutor.execute(wrappedTask)
        } else {
            this.executor.execute(wrappedTask)
        }

        return future
    }

    /**
     * 关闭所有执行器
     */
    override fun preDestroy() {
        try {
            this.isShuttingDown = true
            this.executor.shutdown()
            this.virtualExecutor.shutdown()
            this.isClosed = true
        } catch (e: Exception) {
            log.error("关闭任务执行器时发生错误", e)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(TaskExecuteServiceImpl::class.java)
    }
}
