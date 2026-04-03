package org.maibot.core.modloader

import java.io.IOException
import java.net.URL
import java.net.URLClassLoader
import java.util.*

/**
 * 用于加载模块的类加载器
 * 
 * 
 * 因为模块间也存在依赖关系，因此我们需要重新实现类加载器，以打破双亲委托模型。<br></br>
 * 模块类加载器允许多亲委托，即在加载类时，可以向多个父类加载器请求加载类。
 * 
 * @author OctAutumn
 */
class ModClassLoader : URLClassLoader {
    /** 是否为Root模块加载器 */
    private val isRoot: Boolean

    /** 父加载器列表 */
    private var parentCls: Array<ClassLoader>

    constructor(modUrl: URL, initialParent: List<ClassLoader>) :
            super(arrayOf(modUrl), null) // 不使用默认的父类加载器
    {
        this.isRoot = false
        this.parentCls = initialParent.toTypedArray()
    }

    constructor(parents: List<ClassLoader>) :
            super(arrayOf(), null) // 不使用默认的父类加载器
    {
        require(!parents.isEmpty()) { "Root ModClassLoader requires at least one parent ClassLoader" }
        this.isRoot = true
        this.parentCls = parents.toTypedArray()
    }

    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        var cls = super.findLoadedClass(name)

        cls ?: let {
            // 依次向父加载器请求加载类
            for (parent in parentCls) {
                try {
                    cls = parent.loadClass(name)
                    break
                } catch (_: ClassNotFoundException) {
                    // 忽略异常，继续尝试下一个父加载器
                }
            }
            cls
        } ?: let {
            if (isRoot) {
                // Root加载器无法加载类，直接抛出异常
                throw ClassNotFoundException(name)
            }
            // 如果父加载器都无法加载，则尝试自己加载
            cls = super.findClass(name)
        }

        if (resolve) {
            super.resolveClass(cls)
        }

        return cls
    }

    override fun getResource(name: String): URL? {
        var resource: URL? = null

        // 依次向父加载器请求资源
        for (parent in parentCls) {
            resource = parent.getResource(name) ?: continue
            break
        }

        return resource ?: let {
            if (isRoot) {
                // Root加载器无法加载资源，直接返回null
                return@let null
            }
            // 如果父加载器都无法提供资源，则尝试自己加载
            super.findResource(name)
        }
    }

    @Throws(IOException::class)
    override fun getResources(name: String): Enumeration<URL> {
        // 这里不进行缓存，直接依次请求所有父加载器和自己
        val resources = ArrayList<URL>()

        for (parent in parentCls) {
            with(parent.getResources(name)) {
                while (hasMoreElements()) {
                    resources.add(nextElement())
                }
            }
        }

        if (!isRoot) {
            with(super.findResources(name)) {
                while (hasMoreElements()) {
                    resources.add(nextElement())
                }
            }
        }

        return Collections.enumeration(resources)
    }

    @Throws(IOException::class)
    override fun close() {
        super.close()
        this.parentCls = arrayOf()
    }
}
