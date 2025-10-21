package org.maibot.core.util;

import lombok.Getter;
import lombok.NonNull;
import org.maibot.core.cdi.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务执行器服务
 * <p>
 * 提供线程池和虚拟线程池用于任务执行
 */
@Component
public class TaskExecutorService {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutorService.class);

    @Getter
    private final ExecutorService executor;
    @Getter
    private final ExecutorService virtualExecutor;

    public TaskExecutorService() {
        var processorCount = Runtime.getRuntime().availableProcessors();
        this.executor = new ThreadPoolExecutor(
                processorCount,
                processorCount * 2,
                60L,
                java.util.concurrent.TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactory() {
                    final AtomicInteger threadNumber = new AtomicInteger(1);

                    @Override
                    public Thread newThread(@NonNull Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setName("T-" + threadNumber.getAndIncrement());
                        return thread;
                    }
                }
        );
        this.virtualExecutor = Executors.newThreadPerTaskExecutor(
                new ThreadFactory() {
                    final AtomicInteger threadNumber = new AtomicInteger(1);

                    @Override
                    public Thread newThread(@NonNull Runnable r) {
                        Thread thread = Thread.ofVirtual().unstarted(r);
                        var threadId = threadNumber.getAndUpdate(idx -> {
                            if (idx >= 100) {
                                return 1;
                            } else {
                                return idx + 1;
                            }
                        });
                        thread.setName("VT-" + threadId);
                        return thread;
                    }
                }
        );
    }

    /**
     * 提交任务到执行器
     *
     * @param task 任务Func
     * @param virT 是否使用虚拟线程
     * @return 任务Future
     */
    @SuppressWarnings("UnusedReturnValue")
    // 不是所有任务的结果都会被使用，但有时需要通过Future来监控任务状态
    public Future<?> submit(Runnable task, boolean virT) {
        if (virT) {
            return this.virtualExecutor.submit(task);
        } else {
            return this.executor.submit(task);
        }
    }

    /**
     * 关闭所有执行器
     */
    public void shutdown() {
        try {
            this.executor.shutdown();
            this.virtualExecutor.shutdown();
        } catch (Exception e) {
            log.error("关闭任务执行器时发生错误", e);
        }
    }
}
