package org.maibot.core.ioc

import org.maibot.core.ioc.Instance.createInstance
import org.maibot.sdk.exceptions.InstanceConstructException
import org.maibot.sdk.ioc.DestroyableComponent
import org.maibot.sdk.ioc.InitializableComponent
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException

class SingletonManager : DestroyableComponent {
    /** 单例实例存储 */
    private val singletons = ConcurrentHashMap<Class<*>, Any>()

    /** 单例实例记录队列，关闭时有序销毁 */
    private val singletonInstances = Stack<Any>()

    /** 获取单例实例 */
    fun <T> getSingletonInstance(clazz: Class<T>): T {
        val instance: T
        // 使用线程安全的方式获取或创建实例
        // 放入占位符（Future模式），防止CHM的循环更改
        // 类似于数据库缓存击穿的加锁等待解决方案
        val future = CompletableFuture<T>()
        when (val obj = singletons.putIfAbsent(clazz, future)) {
            null -> {
                // 当前线程负责创建实例
                try {
                    instance = createInstance(clazz)
                    if (instance is InitializableComponent) {
                        // 如果实现接口，执行后初始化方法
                        instance.postConstruct()
                    }
                    future.complete(instance) // 完成Future
                    singletonInstances.push(instance) // 记录单例实例以便关闭时销毁
                    singletons[clazz] = instance as Any // 替换占位符为实际实例
                } catch (e: InstanceConstructException) {
                    singletons.remove(clazz) // 创建失败，移除占位符
                    future.completeExceptionally(e) // 完成Future异常
                    throw e
                }
            }

            is CompletableFuture<*> -> {
                // 其他线程正在创建实例，等待其完成
                try {
                    instance = clazz.cast(obj.get())
                } catch (e: InterruptedException) {
                    // 构建过程中被中断
                    Thread.currentThread().interrupt()
                    throw InstanceConstructException(
                        "Instance creation interrupted for Class %s",
                        clazz.getName(),
                        e
                    )
                } catch (_: ExecutionException) {
                    // 异步构建失败
                    throw InstanceConstructException(
                        "Concurrent instance creation failed for Class %s",
                        clazz.getName()
                    )
                }
            }

            else -> {
                // 实例已存在，直接返回
                instance = clazz.cast(obj)
            }
        }
        return instance
    }


    override fun preDestroy() {
        while (singletonInstances.isNotEmpty()) {
            val instance = singletonInstances.pop()
            if (instance is DestroyableComponent) {
                try {
                    instance.preDestroy()
                } catch (_: Throwable) {
                    // 忽略销毁时的异常
                }
            }
            singletons.remove(instance.javaClass)
        }
    }
}
