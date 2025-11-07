package org.maibot.core.util;

import lombok.Getter;
import lombok.NonNull;
import org.maibot.core.modloader.ModManager;
import org.maibot.sdk.TaskExecutorService;
import org.maibot.sdk.exceptions.FatalError;
import org.maibot.sdk.ioc.AutoInject;
import org.maibot.sdk.ioc.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务执行器服务
 * <p>
 * 提供线程池和虚拟线程池用于任务执行
 */
@Component
public final class TaskExecutorServiceImpl extends TaskExecutorService {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutorServiceImpl.class);

    private volatile boolean            isStarted      = false;
    private volatile boolean            isShuttingDown = false;
    private volatile boolean            isClosed       = false;
    @Getter
    private final    ThreadPoolExecutor executor;
    @Getter
    private final    ExecutorService    virtualExecutor;

    @AutoInject
    public TaskExecutorServiceImpl(ModManager modManager) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            log.error("An uncaught exception occurred in thread {}", t.getName(), e);
            System.exit(1);
        });
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
                  thread.setContextClassLoader(modManager.getModClassLoader());

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
                  thread.setContextClassLoader(modManager.getModClassLoader());

                  return thread;
              }
          }
        );
    }

    public void start() {
        this.isStarted = true;
    }

    /**
     * 提交任务到执行器
     *
     * @param task 任务Callable
     * @param virT 是否使用虚拟线程
     * @return 任务Future
     */
    @Override
    public <T> CompletableFuture<T> submit(Callable<T> task, boolean virT) {
        var future = new CompletableFuture<T>();

        if (isShuttingDown) {
            future.completeExceptionally(
              new RejectedExecutionException("任务执行器正在关闭，无法接受新任务")
            );
            return future;
        } else if (!isStarted || isClosed) {
            future.completeExceptionally(
              new RejectedExecutionException("任务执行器未启动，无法接受任务")
            );
            return future;
        }

        var wrappedTask = new Runnable() {
            @Override
            public void run() {
                try {
                    var ret = task.call();
                    future.complete(ret);
                } catch (FatalError e) {
                    log.error("线程发生致命错误，终止运行", e);
                    System.exit(1);
                    throw e; // 这一行实际上不会被执行，但编译器需要
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            }
        };

        if (virT) {
            this.virtualExecutor.execute(wrappedTask);
        } else {
            this.executor.execute(wrappedTask);
        }

        return future;
    }

    /**
     * 提交任务到执行器
     *
     * @param task 任务Runnable
     * @param virT 是否使用虚拟线程
     * @return 任务Future
     */
    @Override
    public CompletableFuture<Object> submit(Runnable task, boolean virT) {
        var future = new CompletableFuture<>();

        if (isShuttingDown) {
            future.completeExceptionally(
              new RejectedExecutionException("任务执行器正在关闭，无法接受新任务")
            );
            return future;
        } else if (!isStarted || isClosed) {
            future.completeExceptionally(
              new RejectedExecutionException("任务执行器未启动，无法接受任务")
            );
            return future;
        }

        var wrappedTask = new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                    future.complete(null);
                } catch (FatalError e) {
                    log.error("线程发生致命错误，终止运行", e);
                    System.exit(1);
                    throw e; // 这一行实际上不会被执行，但编译器需要
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            }
        };

        if (virT) {
            this.virtualExecutor.execute(wrappedTask);
        } else {
            this.executor.execute(wrappedTask);
        }

        return future;
    }


    /**
     * 关闭所有执行器
     */
    @Override
    public void preDestroy() {
        try {
            this.isShuttingDown = true;
            this.executor.shutdown();
            this.virtualExecutor.shutdown();
            this.isClosed = true;
        } catch (Exception e) {
            log.error("关闭任务执行器时发生错误", e);
        }
    }
}
