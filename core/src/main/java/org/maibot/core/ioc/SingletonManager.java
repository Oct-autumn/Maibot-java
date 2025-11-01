package org.maibot.core.ioc;

import org.maibot.sdk.exceptions.InstanceConstructException;
import org.maibot.sdk.ioc.DestroyableComponent;
import org.maibot.sdk.ioc.InitializableComponent;

import java.util.Map;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class SingletonManager implements DestroyableComponent {
    /// 单例实例存储
    private final Map<Class<?>, Object> singletons         = new ConcurrentHashMap<>();
    /// 单例实例记录队列，关闭时有序销毁
    private final Stack<Object>         singletonInstances = new Stack<>();

    /// 获取单例实例
    <T> T getSingletonInstance(Class<T> clazz) {
        T instance;
        // 使用线程安全的方式获取或创建实例
        // 放入占位符（Future模式），防止CHM的循环更改
        // 类似于数据库缓存击穿的加锁等待解决方案
        var future = new CompletableFuture<T>();
        var obj = singletons.putIfAbsent(clazz, future);
        if (obj == null) {
            // 当前线程负责创建实例
            try {
                instance = Instance.createInstance(clazz);
                if (instance instanceof InitializableComponent inst) {
                    // 如果实现接口，执行后初始化方法
                    inst.postConstruct();
                }
                future.complete(instance); // 完成Future
                singletonInstances.push(instance); // 记录单例实例以便关闭时销毁
                singletons.put(clazz, instance); // 替换占位符为实际实例
            } catch (InstanceConstructException e) {
                singletons.remove(clazz); // 创建失败，移除占位符
                future.completeExceptionally(e); // 完成Future异常
                throw e;
            }
        } else if (obj instanceof CompletableFuture<?> instFuture) {
            // 其他线程正在创建实例，等待其完成
            try {
                instance = clazz.cast(instFuture.get());
            } catch (InterruptedException e) {
                // 构建过程中被中断
                Thread.currentThread().interrupt();
                throw new InstanceConstructException(
                  "Instance creation interrupted for Class %s",
                  clazz.getName(),
                  e
                );
            } catch (ExecutionException e) {
                // 异步构建失败
                throw new InstanceConstructException(
                  "Concurrent instance creation failed for Class %s",
                  clazz.getName()
                );
            }
        } else {
            // 实例已存在，直接返回
            instance = clazz.cast(obj);
        }
        return instance;
    }


    @Override
    public void preDestroy() {
        while (!singletonInstances.isEmpty()) {
            var instance = singletonInstances.pop();
            if (instance instanceof DestroyableComponent destroyable) {
                try {
                    destroyable.preDestroy();
                } catch (Throwable ignored) {
                    // 忽略销毁时的异常
                }
            }
            singletons.remove(instance.getClass());
        }
    }
}
