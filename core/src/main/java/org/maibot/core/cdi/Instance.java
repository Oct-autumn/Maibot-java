package org.maibot.core.cdi;


import org.maibot.core.cdi.annotation.AutoInject;
import org.maibot.core.cdi.annotation.Component;
import org.maibot.core.cdi.annotation.ObjectFactory;
import org.maibot.core.cdi.annotation.Value;
import org.maibot.core.config.ConfigService;
import org.maibot.core.exceptions.CircularDependence;
import org.maibot.core.exceptions.InstanceConstructException;
import org.maibot.core.exceptions.InvalidConfigPath;
import org.maibot.core.exceptions.InvalidValueInjection;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class Instance {

    private static final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();

    private static final ThreadLocal<Set<Class<?>>> constructionStack = ThreadLocal.withInitial(HashSet::new);

    /**
     * 获取类的实例，支持单例和自动注入
     *
     * @param clazz 要获取实例的类
     * @return 类的实例
     * @throws InstanceConstructException 如果实例创建失败或检测到循环依赖
     */
    public static <T> T get(Class<T> clazz)
    throws InstanceConstructException {
        var stack = constructionStack.get();
        if (stack.contains(clazz)) {
            throw new CircularDependence(
              "Circular dependency detected while creating instance of Class %s",
              clazz.getName()
            );
        }
        // 标记正在构造该类的实例
        stack.add(clazz);

        try {
            T instance = null;
            if ((clazz.isAnnotationPresent(Component.class) && clazz.getAnnotation(Component.class).singleton())
              || clazz.isAnnotationPresent(ObjectFactory.class)) {
                // 对于单例，使用线程安全的方式获取或创建实例
                // 放入占位符（Future模式），防止CHM的循环更改
                // 类似于数据库缓存击穿的加锁等待解决方案
                var future = new CompletableFuture<T>();
                var obj = singletons.putIfAbsent(clazz, future);
                if (obj == null) {
                    // 当前线程负责创建实例
                    try {
                        instance = createInstance(clazz);
                        future.complete(instance); // 完成Future
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
            } else {
                // 对于非单例，尝试自动注入
                try {
                    instance = createInstance(clazz);
                } catch (InstanceConstructException e) {
                    throw new InstanceConstructException(
                      "Failed to create instance of Class %s",
                      clazz.getName(),
                      e
                    );
                }
            }
            return instance;
        } finally {
            // 移除构造标记
            stack.remove(clazz);
        }
    }

    /**
     * 创建类的实例，支持 @AutoInject 注解的构造方法注入
     *
     * @param clazz 要创建实例的类
     * @return 类的实例
     * @throws InstanceConstructException 如果实例创建失败或类缺少合适的构造方法
     */
    private static <T> T createInstance(Class<T> clazz)
    throws InstanceConstructException {
        // 查找带有自动注入注解 / 零参构造方法
        Constructor<?> autoConstructor = null;
        Constructor<?> zeroConstructor = null;
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            if (c.isAnnotationPresent(AutoInject.class)) {
                autoConstructor = c;
            }
            if (c.getParameterCount() == 0) {
                zeroConstructor = c;
            }
        }

        if (autoConstructor == null && zeroConstructor == null) {
            throw new InstanceConstructException(
              "Failed to create instance of Class %s, it must have either a zero-arg constructor or a constructor annotated with @AutoInject",
              clazz.getName()
            );
        }

        var instance = constructInst(clazz, autoConstructor, zeroConstructor);

        return clazz.cast(instance);
    }

    /**
     * 使用指定的构造方法创建实例
     *
     * @param clazz           要创建实例的类
     * @param autoConstructor @AutoInject 注解的构造方法
     * @param zeroConstructor 零参构造方法
     * @return 类的实例
     * @throws InstanceConstructException 如果实例创建失败或类缺少合适的构造方法
     */
    private static <T> Object constructInst(
      Class<T> clazz,
      Constructor<?> autoConstructor,
      Constructor<?> zeroConstructor
    )
    throws InstanceConstructException {
        Object instance;
        if (autoConstructor != null) {
            // 对于 @AutoInject 构造方法
            try {
                var paramClarifications = autoConstructor.getParameters();
                var params = new Object[paramClarifications.length];
                for (int idx = 0; idx < paramClarifications.length; idx++) {
                    if (paramClarifications[idx].isAnnotationPresent(Value.class)) {
                        // 参数注入
                        var value = paramClarifications[idx].getAnnotation(Value.class).value();
                        params[idx] = getValue(value, paramClarifications[idx].getType());
                    } else {
                        params[idx] = get(paramClarifications[idx].getType());
                    }
                }
                autoConstructor.setAccessible(true);
                instance = autoConstructor.newInstance(params);
            } catch (Exception e) {
                throw new InstanceConstructException(
                  "Failed to create instance using @AutoInject constructor for class %s",
                  clazz.getName(),
                  e
                );
            }
        } else {
            // 对于 零参 构造方法
            try {
                zeroConstructor.setAccessible(true);
                instance = zeroConstructor.newInstance();
            } catch (Exception e) {
                throw new InstanceConstructException(
                  "Failed to create instance using zero-arg constructor for class %s",
                  clazz.getName(),
                  e
                );
            }
        }
        return instance;
    }

    /**
     * 获取字段值，支持从配置文件中读取
     *
     * @param value     字段名称（形如<code>${section.field}</code>），或直接的字符串值
     * @param valueType 字段类型
     * @return 字段值
     * @throws InvalidConfigPath 如果配置路径无效
     */
    private static <T> T getValue(String value, Class<T> valueType)
    throws InvalidConfigPath {
        var confMgr = Instance.get(ConfigService.class);

        if (value.startsWith("${") && value.endsWith("}")) {
            // 符合格式的配置项，从配置文件中读取
            var path = value.substring(2, value.length() - 1);
            return confMgr.getFromRaw(path, valueType);
        }

        try {
            // 其他情况，尝试直接转换
            return valueType.cast(value);
        } catch (ClassCastException e) {
            throw new InvalidValueInjection("Cannot inject value '%s' as type %s", value, valueType.getName());
        }
    }
}
