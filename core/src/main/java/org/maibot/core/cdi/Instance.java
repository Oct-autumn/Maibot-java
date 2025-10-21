package org.maibot.core.cdi;


import org.maibot.core.cdi.annotation.ObjectFactory;
import org.maibot.core.config.ConfigService;
import org.maibot.core.cdi.annotation.AutoInject;
import org.maibot.core.cdi.annotation.Component;
import org.maibot.core.cdi.annotation.Value;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class Instance {

    private static final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();

    private static final ThreadLocal<Set<Class<?>>> constructionStack = ThreadLocal.withInitial(HashSet::new);

    /**
     * 获取类的实例，支持单例和自动注入
     *
     * @param clazz 要获取实例的类
     * @return 类的实例
     * @throws RuntimeException 如果实例创建失败或检测到循环依赖
     */
    public static <T> T get(Class<T> clazz) {
        var stack = constructionStack.get();
        try {
            if (stack.contains(clazz)) {
                throw new RuntimeException("Circular dependency detected while creating instance of Class " + clazz.getName());
            }

            stack.add(clazz);   // 标记正在构造该类的实例

            T instance;
            if ((clazz.isAnnotationPresent(Component.class) && clazz.getAnnotation(Component.class).singleton()) || clazz.isAnnotationPresent(ObjectFactory.class)) {
                // 对于单例，使用线程安全的方式获取或创建实例
                // 放入占位符（Future模式），防止CHM的循环更改
                // 类似于数据库缓存击穿的加锁等待解决方案
                var future = new CompletableFuture<T>();
                var prev = singletons.putIfAbsent(clazz, future);
                if (prev == null) {
                    // 当前线程负责创建实例
                    instance = createInstance(clazz);
                    future.complete(instance); // 完成Future
                    singletons.put(clazz, instance); // 替换占位符为实际实例
                } else if (prev instanceof CompletableFuture) {
                    // 其他线程正在创建实例，等待其完成
                    instance = ((CompletableFuture<T>) prev).get();
                } else {
                    // 实例已存在，直接返回
                    instance = clazz.cast(prev);
                }
            } else {
                // 对于非单例，尝试自动注入
                instance = createInstance(clazz);
            }

            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance for class: " + clazz.getName(), e);
        } finally {
            stack.remove(clazz); // 确保在异常情况下也能移除标记
        }
    }

    /**
     * 创建类的实例，支持 @AutoInject 注解的构造方法和字段注入
     *
     * @param clazz 要创建实例的类
     * @return 类的实例
     * @throws RuntimeException 如果实例创建失败或类缺少合适的构造方法
     */
    private static <T> T createInstance(Class<T> clazz) {
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

        var instance = constructInst(clazz, autoConstructor, zeroConstructor);

        // 进行字段注入
        try {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(AutoInject.class)) {
                    field.setAccessible(true);
                    Object dependency = get(field.getType());
                    field.set(instance, dependency);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to run field injection on Class " + clazz.getName(), e);
        }

        return clazz.cast(instance);
    }

    /**
     * 使用指定的构造方法创建实例
     *
     * @param clazz           要创建实例的类
     * @param autoConstructor @AutoInject 注解的构造方法
     * @param zeroConstructor 零参构造方法
     * @return 类的实例
     * @throws RuntimeException 如果实例创建失败或类缺少合适的构造方法
     */
    private static <T> Object constructInst(Class<T> clazz, Constructor<?> autoConstructor, Constructor<?> zeroConstructor) {
        Object instance;
        if (autoConstructor != null) {
            // 对于 @AutoInject 构造方法
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
            try {
                autoConstructor.setAccessible(true);
                instance = autoConstructor.newInstance(params);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create instance using @AutoInject constructor for class: " + clazz.getName(), e);
            }
        } else if (zeroConstructor != null) {
            // 对于 零参 构造方法
            try {
                zeroConstructor.setAccessible(true);
                instance = zeroConstructor.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create instance using zero-arg constructor for class: " + clazz.getName(), e);
            }
        } else {
            throw new RuntimeException("Failed to create instance of Class " + clazz.getName() + ", it must have either a zero-arg constructor or a constructor annotated with @AutoInject");
        }
        return instance;
    }

    /**
     * 获取字段值，支持从配置文件中读取
     *
     * @param value     字段名称（形如<code>${section.field}</code>），或直接的字符串值
     * @param valueType 字段类型
     * @return 字段值
     */
    private static <T> T getValue(String value, Class<T> valueType) {
        try {
            var confMgr = Instance.get(ConfigService.class);
            if (value.startsWith("${") && value.endsWith("}")) {
                var path = value.substring(2, value.length() - 1);
                return confMgr.getFromRaw(path, valueType);
            } else if (valueType == String.class) {
                return valueType.cast(value);
            } else {
                throw new RuntimeException("Invalid value format: " + value);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get value for: " + value, e);
        }
    }
}
