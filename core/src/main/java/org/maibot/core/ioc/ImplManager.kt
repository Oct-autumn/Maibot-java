package org.maibot.core.ioc

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassGraphException
import org.maibot.sdk.exceptions.ClassNoImplementation
import org.maibot.sdk.exceptions.FatalError
import org.maibot.sdk.ioc.Component
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

internal class ImplManager {
    private val implementations = ConcurrentHashMap<Class<*>, ImplMap>()

    /**
     * 扫描指定包下的实现类并注册
     *
     * @param basePackage 要扫描的基础包名
     */
    fun scanImplementations(classLoader: ClassLoader, basePackage: String) {
        val classes = HashSet<Class<*>>()
        var scanner = ClassGraph().enableAllInfo().overrideClassLoaders(classLoader)
        if (basePackage.isNotBlank()) {
            scanner = scanner.acceptPackages(basePackage)
        }

        try {
            // 扫描指定包下的所有类，找到带有 @Component 注解的实现类
            scanner.scan().getClassesWithAnnotation(Component::class.java).forEach { classInfo ->
                try {
                    // 只注册非抽象类和非接口
                    if (!Modifier.isAbstract(classInfo.modifiers) && !classInfo.isInterface) {
                        val clazz = Class.forName(classInfo.getName(), false, classLoader)
                        classes.add(clazz)
                    }
                } catch (e: Exception) {
                    throw FatalError("Failed to load class %s during implementation scanning", classInfo.getName(), e)
                }
            }
        } catch (e: ClassGraphException) {
            throw FatalError("Failed to scan implementations in package '%s'", basePackage, e)
        }

        for (clazz in classes) {
            var anno = clazz.getAnnotation(Component::class.java)
            if (anno == null) {
                // 说明@Component作为元注解使用，获取实际注解
                anno = clazz.annotations.map { it.annotationClass.java.getAnnotation(Component::class.java) }
                    .filterNotNull().firstOrNull() ?: throw FatalError( // 理论上不会发生，因为前面已经通过ClassGraph筛选过了
                    "Component annotation not found on class %s during registration. This shouldn't happen.",
                    clazz.getName()
                )

            }

            // 获取组件名称，如果注解中没有指定，则使用类名
            var name = anno.name
            if (name.isBlank()) {
                name = clazz.getSimpleName()
            }

            // 注册类为自身的实现
            this.putImpl(clazz, name, clazz, anno.primaryImpl)
            // 注册类为接口的实现（如果有）
            clazz.interfaces.forEach { this.putImpl(it, name, clazz, anno.primaryImpl) }
            // 注册类为父类的实现（如果有）
            clazz.superclass?.let {
                var superClass = it
                while (superClass != Any::class.java) {
                    this.putImpl(superClass, name, clazz, anno.primaryImpl)
                    superClass = superClass.getSuperclass()
                }
            }
        }
    }

    fun putImpl(interfaceOrClass: Class<*>, name: String, implClass: Class<*>, primary: Boolean) {
        implementations.compute(
            interfaceOrClass
        ) { _: Class<*>, implMap: ImplMap? ->
            val map = implMap ?: ImplMap()

            map.impls[name]?.let {
                if (it != implClass) {
                    // 重复的实现名称
                    throw FatalError(
                        "Duplicate implementation name '%s' found for %s: %s and %s",
                        name,
                        interfaceOrClass.getName(),
                        it.getName(),
                        implClass.getName()
                    )
                }
            }

            map.impls[name] = implClass
            if (primary) {
                map.primary?.let {
                    if (it != implClass) {
                        // 重复的主实现
                        throw FatalError(
                            "Multiple primary implementations found for %s: %s and %s",
                            interfaceOrClass.getName(),
                            it.getName(),
                            implClass.getName()
                        )
                    }
                }

                map.primary = implClass
            }

            map
        }
    }

    fun getImpl(interfaceOrClass: Class<*>, name: String = ""): Class<*> {
        val ret = AtomicReference<Any?>()

        implementations.compute(
            interfaceOrClass
        ) { _: Class<*>, implMap: ImplMap? ->
            if (implMap == null) {
                // 没有任何实现
                ret.set(ClassNoImplementation("No implementation found for %s", interfaceOrClass.getName()))
                return@compute null
            }

            when (name) {
                "" -> {
                    // 没有指定名称
                    // 优先返回主实现
                    // 没有主实现时，如果只有一个实现，返回它
                    // 否则抛出异常，无法确定使用哪个实现
                    if (implMap.primary != null) {
                        ret.set(implMap.primary)
                    } else if (implMap.impls.size == 1) {
                        ret.set(implMap.impls.values.iterator().next())
                    } else {
                        ret.set(
                            ClassNoImplementation(
                                "Multiple implementations found for %s, but no primary implementation is defined. Implementations: %s",
                                interfaceOrClass.getName(),
                                implMap.impls.keys.joinToString(", ")
                            )
                        )
                    }
                }

                else -> {
                    // 指定了名称，查找对应的实现
                    implMap.impls[name]?.let {
                        // 返回指定名称的实现
                        ret.set(it)
                    } ?: run {
                        // 没有指定名称的实现
                        ret.set(
                            ClassNoImplementation(
                                "No implementation named '%s' found for %s", name, interfaceOrClass.getName()
                            )
                        )
                    }
                }
            }

            implMap
        }

        when (val result = ret.get()) {
            is ClassNoImplementation -> throw result
            is Class<*> -> return result
            else -> throw FatalError(
                "Unexpected error occurred while retrieving implementation for %s with name '%s'",
                interfaceOrClass.getName(),
                name
            )
        }
    }

    private class ImplMap {
        val impls = HashMap<String, Class<*>>()
        var primary: Class<*>? = null
    }
}
