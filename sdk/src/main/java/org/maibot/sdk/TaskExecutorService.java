package org.maibot.sdk;

import org.maibot.sdk.ioc.DestroyableComponent;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public abstract class TaskExecutorService implements DestroyableComponent {
    /**
     * 提交任务到执行器
     *
     * @param task 任务Callable
     * @param virT 是否使用虚拟线程
     * @return 任务Future
     */
    abstract public <T> CompletableFuture<T> submit(Callable<T> task, boolean virT);

    /**
     * 提交任务到执行器
     *
     * @param task 任务Runnable
     * @param virT 是否使用虚拟线程
     * @return 任务Future
     */
    abstract public CompletableFuture<Object> submit(Runnable task, boolean virT);
}
