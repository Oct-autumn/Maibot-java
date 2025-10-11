package org.maibot.core.util;

import lombok.Getter;
import org.maibot.core.cdi.annotation.Component;

import java.util.concurrent.*;

@Component
public class TaskExecutorService {
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
                new LinkedBlockingQueue<>()
        );
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public Future<?> submit(Runnable task, boolean virT) {
        if (virT) {
            return this.virtualExecutor.submit(task);
        } else {
            return this.executor.submit(task);
        }
    }

    public void shutdown() {
        this.executor.shutdown();
        this.virtualExecutor.shutdown();
    }
}
