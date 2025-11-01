package org.maibot.core.ioc;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassGraphException;
import org.maibot.sdk.exceptions.ClassNoImplementation;
import org.maibot.sdk.exceptions.FatalError;
import org.maibot.sdk.ioc.Component;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class ImplManager {
    private final Map<Class<?>, ImplMap> implementations = new ConcurrentHashMap<>();

    /**
     * 扫描指定包下的实现类并注册
     *
     * @param basePackage 要扫描的基础包名
     */
    void scanImplementations(String basePackage, ClassLoader classLoader) {
        Set<Class<?>> classes = new HashSet<>();
        var scanner = new ClassGraph().enableAllInfo().overrideClassLoaders(classLoader);
        if (basePackage != null && !basePackage.isBlank()) {
            scanner = scanner.acceptPackages(basePackage);
        }

        try (var scanResult = scanner.scan()) {
            // 扫描指定包下的所有类，找到带有 @Component 注解的实现类
            scanResult.getClassesWithAnnotation(Component.class).forEach(classInfo -> {
                try {
                    // 只注册非抽象类和非接口
                    if (!Modifier.isAbstract(classInfo.getModifiers()) && !classInfo.isInterface()) {
                        Class<?> clazz = Class.forName(classInfo.getName(), false, classLoader);
                        classes.add(clazz);
                    }
                } catch (ClassNotFoundException ignored) {
                    // 不应该发生，因为 ClassGraph 已经找到了这个类
                }
            });
        } catch (ClassGraphException e) {
            throw new FatalError("Failed to scan implementations in package '%s'", basePackage, e);
        }

        for (Class<?> clazz : classes) {
            // 获取组件名称
            var anno = clazz.getAnnotation(Component.class);
            if (anno == null) {
                // 说明@Component作为元注解使用，获取实际注解
                anno = Arrays.stream(clazz.getAnnotations())
                             .map(a -> a.annotationType().getAnnotation(Component.class))
                             .filter(Objects::nonNull)
                             .findFirst()
                             .orElseThrow(
                               // 理论上不会发生，因为前面已经通过ClassGraph筛选过了
                               () -> new FatalError(
                                 "Component annotation not found on class %s during registration. This shouldn't happen.",
                                 clazz.getName()
                               )
                             );
            }

            String name = anno.name();
            if (name.isBlank()) {
                name = clazz.getSimpleName();
            }

            // 注册类及其所有接口的实现
            this.putImpl(clazz, name, clazz, anno.primaryImpl());

            for (Class<?> iface : clazz.getInterfaces()) {
                this.putImpl(iface, name, clazz, anno.primaryImpl());
            }

            for (Class<?> superClass = clazz.getSuperclass(); superClass != null && superClass != Object.class; superClass = superClass.getSuperclass()) {
                this.putImpl(superClass, name, clazz, anno.primaryImpl());
            }
        }
    }

    void putImpl(Class<?> interfaceOrClass, String name, Class<?> implClass, boolean primary) {
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

    Class<?> getImpl(Class<?> interfaceOrClass, String name) {
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

    Class<?> getImpl(Class<?> interfaceOrClass) {
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
