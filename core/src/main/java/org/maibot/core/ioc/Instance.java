package org.maibot.core.ioc;


import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassGraphException;
import org.maibot.core.config.ConfigServiceImpl;
import org.maibot.sdk.config.ConfigService;
import org.maibot.sdk.exceptions.*;
import org.maibot.sdk.ioc.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class Instance {
    /// 实现类管理器
    private static final ImplManager                implManager       = new ImplManager();
    /// 单例实例管理器
    private static final SingletonManager           singletonManager  = new SingletonManager();
    /// 构造栈，检测循环依赖
    private static final ThreadLocal<Set<Class<?>>> constructionStack = ThreadLocal.withInitial(HashSet::new);

    public static void scanImplementations(String basePackage, ClassLoader classLoader) {
        implManager.scanImplementations(basePackage, classLoader);
    }

    /**
     * 获取类的实例，支持单例和自动注入
     *
     * @param clazz 要获取实例的类
     * @return 类的实例
     * @throws InstanceConstructException 如果实例创建失败或检测到循环依赖
     */
    private static <T> T getInst(Class<T> clazz)
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
            T instance;
            if ((clazz.isAnnotationPresent(Component.class) && clazz.getAnnotation(Component.class).singleton())) {
                instance = singletonManager.getSingletonInstance(clazz);
            } else {
                // 对于非单例，尝试自动注入
                try {
                    instance = createInstance(clazz);
                    if (instance instanceof InitializableComponent inst) {
                        // 如果实现接口，执行后初始化方法
                        inst.postConstruct();
                    }
                } catch (InstanceConstructException e) {
                    throw new InstanceConstructException("Failed to create instance of Class %s", clazz.getName(), e);
                }
            }
            return instance;
        } finally {
            // 移除构造标记
            stack.remove(clazz);
        }
    }

    /**
     * 获取接口或类的指定实现的实例
     * <p>
     * 不推荐使用此方法，建议使用构造函数注入
     *
     * @param interfaceOrClass 接口或类
     * @param name             实现名称
     * @param <T>              接口或类的类型
     * @return 接口或类的指定实现的实例
     */
    public static <T> T get(Class<T> interfaceOrClass, String name) {
        Class<?> implClass = implManager.getImpl(interfaceOrClass, name);
        return interfaceOrClass.cast(getInst(implClass));
    }

    /**
     * 获取接口或类的默认实现的实例
     * <p>
     * 不推荐使用此方法，建议使用构造函数注入
     *
     * @param interfaceOrClass 接口或类
     * @param <T>              接口或类的类型
     * @return 接口或类的默认实现的实例
     */
    public static <T> T get(Class<T> interfaceOrClass) {
        try {
            Class<?> implClass = implManager.getImpl(interfaceOrClass);
            return interfaceOrClass.cast(getInst(implClass));
        } catch (ClassNoImplementation e) {
            // 未托管的类，尝试直接创建实例
            return getInst(interfaceOrClass);
        }
    }

    /**
     * 创建类的实例，支持 @AutoInject 注解的构造方法注入
     *
     * @param clazz 要创建实例的类
     * @return 类的实例
     * @throws InstanceConstructException 如果实例创建失败或类缺少合适的构造方法
     */
    static <T> T createInstance(Class<T> clazz)
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

        Object instance;

        if (autoConstructor != null) {
            instance = getFromAutoConstructor(clazz, autoConstructor);
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

        return clazz.cast(instance);
    }

    /**
     * 使用 @AutoInject 注解的构造方法创建实例
     *
     * @param clazz           要创建实例的类
     * @param autoConstructor @AutoInject 注解的构造方法
     * @param <T>             类的类型
     * @return 类的实例
     * @throws InstanceConstructException 如果实例创建失败
     */
    private static <T> Object getFromAutoConstructor(Class<T> clazz, Constructor<?> autoConstructor) {
        Object instance;
        try {
            var paramClarifications = autoConstructor.getParameters();
            var params = new Object[paramClarifications.length];
            for (int idx = 0; idx < paramClarifications.length; idx++) {
                var param = paramClarifications[idx];
                if (param.isAnnotationPresent(Value.class)) {
                    // 配置文件注入或直接值注入
                    var value = param.getAnnotation(Value.class).value();
                    params[idx] = getValue(value, param.getType());
                } else if (param.isAnnotationPresent(Specify.class)) {
                    // 指定实现
                    String implClassName = param.getAnnotation(Specify.class).name();
                    params[idx] = getInst(implManager.getImpl(param.getType(), implClassName));
                } else {
                    params[idx] = getInst(implManager.getImpl(param.getType()));
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
        if (value.startsWith("${") && value.endsWith("}")) {
            // 符合格式的配置项，从配置文件中读取
            var confMgr = Instance.get(ConfigService.class);
            var path = value.substring(2, value.length() - 1);
            return confMgr.getConfig(path, valueType);
        }

        try {
            // 其他情况，尝试直接转换
            return valueType.cast(value);
        } catch (ClassCastException e) {
            throw new InvalidValueInjection("Cannot inject value '%s' as type %s", value, valueType.getName());
        }
    }

    /**
     * 关闭 IOC 容器，销毁所有实例
     */
    public static void close() {
        singletonManager.preDestroy();
    }
}
