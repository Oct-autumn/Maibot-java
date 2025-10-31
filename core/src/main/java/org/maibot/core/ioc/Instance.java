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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public final class Instance {
    /// 实现类管理器
    private static final ImplManager                implManager        = new ImplManager();
    /// 单例实例存储
    private static final Map<Class<?>, Object>      singletons         = new ConcurrentHashMap<>();
    /// 构造栈，检测循环依赖
    private static final ThreadLocal<Set<Class<?>>> constructionStack  = ThreadLocal.withInitial(HashSet::new);
    /// 单例实例记录队列，关闭时有序销毁
    private static final Stack<Object>              singletonInstances = new Stack<>();

    static {
        // 加载核心组件配置
        var configService = new ConfigServiceImpl();
        configService.postConstruct();

        singletons.put(ConfigServiceImpl.class, configService);
    }

    /**
     * 扫描指定包下的实现类并注册
     *
     * @param basePackage 要扫描的基础包名
     */
    public static void scanImplementations(String basePackage, ClassLoader classLoader) {
        Set<Class<?>> classes = new HashSet<>();
        var scanner = new ClassGraph().enableAllInfo().overrideClassLoaders(classLoader);
        if (basePackage != null && !basePackage.isBlank()) {
            scanner = scanner.acceptPackages(basePackage);
        }

        try (var scanResult = scanner.scan()) {
            // 扫描指定包下的所有类，找到带有 @Component 注解的实现类
            scanResult.getClassesWithAnnotation(Component.class.getName()).forEach(classInfo -> {
                try {
                    // 只注册非抽象类和非接口
                    if (!Modifier.isAbstract(classInfo.getModifiers()) && !classInfo.isInterface()) {
                        Class<?> clazz = Class.forName(classInfo.getName(), false, classLoader);
                        classes.add(clazz);
                    }
                } catch (ClassNotFoundException e) {
                    // 不应该发生，因为 ClassGraph 已经找到了这个类
                    throw new FatalError(
                      "Failed to load class %s during scanning. This shouldn't happen.",
                      classInfo.getName(),
                      e
                    );
                }
            });
        } catch (ClassGraphException e) {
            throw new FatalError("Failed to scan implementations in package '%s'", basePackage, e);
        }

        for (Class<?> clazz : classes) {
            String name = clazz.getAnnotation(Component.class).name();
            if (name.isBlank()) {
                name = clazz.getSimpleName();
            }

            // 注册类及其所有接口的实现
            implManager.putImpl(clazz, name, clazz, clazz.getAnnotation(Component.class).primaryImpl());

            for (Class<?> iface : clazz.getInterfaces()) {
                implManager.putImpl(iface, name, clazz, clazz.getAnnotation(Component.class).primaryImpl());
            }

            for (Class<?> superClass = clazz.getSuperclass(); superClass != null && superClass != Object.class; superClass = superClass.getSuperclass()) {
                implManager.putImpl(superClass, name, clazz, clazz.getAnnotation(Component.class).primaryImpl());
            }
        }
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
            T instance = null;
            if ((clazz.isAnnotationPresent(Component.class) && clazz.getAnnotation(Component.class).singleton())) {
                // 对于单例，使用线程安全的方式获取或创建实例
                // 放入占位符（Future模式），防止CHM的循环更改
                // 类似于数据库缓存击穿的加锁等待解决方案
                var future = new CompletableFuture<T>();
                var obj = singletons.putIfAbsent(clazz, future);
                if (obj == null) {
                    // 当前线程负责创建实例
                    try {
                        instance = createInstance(clazz);
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
        while (!singletonInstances.isEmpty()) {
            var instance = singletonInstances.pop();
            if (instance instanceof DestroyableComponent destroyable) {
                try {
                    destroyable.preDestroy();
                } catch (Exception e) {
                    // 忽略销毁时的异常
                }
            }
            singletons.remove(instance.getClass());
        }
    }

    private static final class ImplManager {
        private final Map<Class<?>, ImplMap> implementations = new ConcurrentHashMap<>();

        private void putImpl(Class<?> interfaceOrClass, String name, Class<?> implClass, boolean primary) {
            var implMap = implementations.computeIfAbsent(interfaceOrClass, clazz -> new ImplMap());
            synchronized (implMap) {    // 同步以防止并发修改
                if (implMap.impls.containsKey(name) && implMap.impls.get(name) != implClass) {
                    // 重复的实现名称
                    throw new FatalError(
                      "Duplicate implementation name '%s' found for %s: %s and %s",
                      name,
                      interfaceOrClass.getName(),
                      implMap.impls.get(name).getName(),
                      implClass.getName()
                    );
                }
                implMap.impls.put(name, implClass);
                if (primary) {
                    if (implMap.primary != null && implMap.primary != implClass) {
                        // 重复的主实现
                        throw new FatalError(
                          "Multiple primary implementations found for %s: %s and %s",
                          interfaceOrClass.getName(),
                          implMap.primary.getName(),
                          implClass.getName()
                        );
                    }
                    implMap.primary = implClass;
                }
            }
        }

        private Class<?> getImpl(Class<?> interfaceOrClass, String name) {
            var implMap = implementations.get(interfaceOrClass);
            if (implMap == null) {
                // 没有任何实现
                throw new ClassNoImplementation("No implementation found for %s", interfaceOrClass.getName());
            } else if (!implMap.impls.containsKey(name)) {
                // 没有指定名称的实现
                throw new ClassNoImplementation(
                  "No implementation named '%s' found for %s",
                  name,
                  interfaceOrClass.getName()
                );
            } else {
                // 返回指定名称的实现
                return implMap.impls.get(name);
            }
        }

        private Class<?> getImpl(Class<?> interfaceOrClass) {
            var implMap = implementations.get(interfaceOrClass);
            Class<?> implClass;
            if (implMap == null) {
                // 没有任何实现
                throw new ClassNoImplementation("No implementation found for %s", interfaceOrClass.getName());
            } else if (implMap.primary != null) {
                // 返回主实现
                implClass = implMap.primary;
            } else {
                // 没有主实现，检查实现数量
                if (implMap.impls.size() == 1) {
                    // 只有一个实现，返回它
                    implClass = implMap.impls.values().iterator().next();
                } else {
                    // 多个实现，无法确定使用哪个，抛出异常
                    throw new ClassNoImplementation(
                      "Multiple implementations found for %s, but no primary implementation is defined. Implementations: %s",
                      interfaceOrClass.getName(),
                      String.join(", ", implMap.impls.keySet())
                    );
                }
            }
            return implClass;
        }

        private static final class ImplMap {
            Map<String, Class<?>> impls   = new HashMap<>();
            Class<?>              primary = null;
        }
    }
}
