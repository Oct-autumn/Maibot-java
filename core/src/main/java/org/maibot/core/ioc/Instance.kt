package org.maibot.core.ioc

import org.maibot.sdk.config.ConfigService
import org.maibot.sdk.exceptions.CircularDependence
import org.maibot.sdk.exceptions.InstanceConstructException
import org.maibot.sdk.exceptions.InvalidConfigPath
import org.maibot.sdk.exceptions.InvalidValueInjection
import org.maibot.sdk.ioc.*
import java.lang.reflect.Constructor
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.function.Supplier


object Instance {
    /** 实现类管理器 */
    private val implManager = ImplManager()

    /** 单例实例管理器 */
    private val singletonManager = SingletonManager()

    /** 构造栈，检测循环依赖 */
    private val constructionStack = ThreadLocal.withInitial(Supplier { HashSet<Class<*>>() })

    fun scanImplementations(classLoader: ClassLoader, basePackage: String = "") {
        implManager.scanImplementations(classLoader, basePackage)
    }

    /**
     * 获取类的实例，支持单例和自动注入
     * 
     * @param clazz 要获取实例的类
     * @return 类的实例
     * @throws InstanceConstructException 如果实例创建失败或检测到循环依赖
     */
    private fun <T> getInst(clazz: Class<T>): T {
        val stack = constructionStack.get()
        if (stack.contains(clazz)) {
            throw CircularDependence(
                "Circular dependency detected while creating instance of Class %s", clazz.getName()
            )
        }
        // 标记正在构造该类的实例
        stack.add(clazz)

        try {
            if ((clazz.isAnnotationPresent(Component::class.java) && clazz.getAnnotation(Component::class.java).singleton)) {
                // 对于单例，通过单例管理器获取
                return singletonManager.getSingletonInstance(clazz)
            } else {
                // 对于非单例，尝试自动注入
                try {
                    return createInstance(clazz).also {
                        if (it is InitializableComponent) {
                            // 如果实现接口，执行后初始化方法
                            it.postConstruct()
                        }
                    }
                } catch (e: InstanceConstructException) {
                    throw InstanceConstructException("Failed to create instance of Class %s", clazz.getName(), e)
                }
            }
        } finally {
            // 移除构造标记
            stack.remove(clazz)
        }
    }

    /**
     * 获取接口或类的指定实现的实例
     * 
     * 
     * 不推荐使用此方法，建议使用构造函数注入
     * 
     * @param interfaceOrClass 接口或类
     * @param name             实现名称
     * @param <T>              接口或类的类型
     * @return 接口或类的指定实现的实例
    </T> */
    fun <T> get(interfaceOrClass: Class<T>, name: String = ""): T {
        val implClass = implManager.getImpl(interfaceOrClass, name)

        return interfaceOrClass.cast(getInst(implClass ?: interfaceOrClass))
    }

    /**
     * 创建类的实例，支持 @AutoInject 注解的构造方法注入
     * 
     * @param clazz 要创建实例的类
     * @return 类的实例
     * @throws InstanceConstructException 如果实例创建失败或类缺少合适的构造方法
     */
    fun <T> createInstance(clazz: Class<T>): T {
        // 查找带有自动注入注解 / 零参构造方法
        var autoConstructor: Constructor<*>? = null
        var zeroConstructor: Constructor<*>? = null
        for (c in clazz.getDeclaredConstructors()) {
            if (c.isAnnotationPresent(AutoInject::class.java)) {
                autoConstructor = c
            }
            if (c.parameterCount == 0) {
                zeroConstructor = c
            }
        }

        val instance: Any?

        if (autoConstructor != null) {
            try {
                instance = getFromAutoConstructor(autoConstructor)
            } catch (e: Exception) {
                throw InstanceConstructException(
                    "Failed to create instance using @AutoInject constructor for class %s", clazz.getName(), e
                )
            }
        } else if (zeroConstructor != null) {
            // 对于 零参 构造方法
            try {
                zeroConstructor.setAccessible(true)
                instance = zeroConstructor.newInstance()
            } catch (e: Exception) {
                throw InstanceConstructException(
                    "Failed to create instance using zero-arg constructor for class %s", clazz.getName(), e
                )
            }
        } else {
            throw InstanceConstructException(
                "Failed to create instance of Class %s, it must have either a zero-arg constructor or a constructor annotated with @AutoInject",
                clazz.getName()
            )
        }

        return clazz.cast(instance)
    }

    /**
     * 使用 @AutoInject 注解的构造方法创建实例
     *
     * @param autoConstructor @AutoInject 注解的构造方法
     * @param <T>             类的类型
     * @return 类的实例
     * @throws InstanceConstructException 如果实例创建失败
    </T> */
    private fun <T> getFromAutoConstructor(autoConstructor: Constructor<T>): T {
        val paramClarifications = autoConstructor.parameters
        val params = arrayOfNulls<Any>(paramClarifications.size)

        paramClarifications.forEachIndexed { idx, param ->
            if (param.isAnnotationPresent(Value::class.java)) {
                // 配置文件注入或直接值注入
                val value = param.getAnnotation(Value::class.java).value
                params[idx] = getValue(value, param.parameterizedType)
            } else {
                val interfaceOrClass = param.getType()
                val implClassName = param.getAnnotation(Specify::class.java)?.name ?: ""
                val implClass = implManager.getImpl(interfaceOrClass, implClassName)

                // 注入实现
                params[idx] = getInst(implClass ?: interfaceOrClass)
            }
        }

        autoConstructor.setAccessible(true)
        return autoConstructor.newInstance(*params)
    }

    /**
     * 获取字段值，支持从配置文件中读取
     * 
     * @param value     字段名称（形如`${section.field}`），或直接的字符串值
     * @param valueType 字段类型
     * @return 字段值
     * @throws InvalidConfigPath 如果配置路径无效
     */
    private fun <T> getValue(value: String, valueType: Type): T {
        if (value.startsWith($$"${") && value.endsWith("}")) {
            // 符合格式的配置项，从配置文件中读取
            val confMgr = get(ConfigService::class.java)
            val path = value.substring(2, value.length - 1)

            return confMgr.getConfig(path, valueType)
        }

        try {
            // 其他情况，尝试直接转换
            @Suppress("UNCHECKED_CAST")
            return valueType.javaClass.cast(value) as T
        } catch (e: ClassCastException) {
            throw InvalidValueInjection("Cannot inject value '%s' as type %s", value, valueType.typeName, e)
        }
    }

    /**
     * 关闭 IOC 容器，销毁所有实例
     */
    fun close() {
        singletonManager.preDestroy()
    }
}
