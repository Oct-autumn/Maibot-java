package org.maibot.core.modloader;

import lombok.Getter;
import lombok.NonNull;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用于加载模块的类加载器
 * <p>
 * 因为模块间也存在依赖关系，因此我们需要重新实现类加载器，以打破双亲委托模型。<br>
 * 模块类加载器允许多亲委托，即在加载类时，可以向多个父类加载器请求加载类。
 *
 * @author OctAutumn
 */
public class ModClassLoader extends URLClassLoader {
    /// 是否为Root模块加载器
    private final boolean isRoot;

    /// 父加载器列表
    private ClassLoader[] parentCls;

    public ModClassLoader(@NonNull URL modUrl, @NonNull List<ClassLoader> initialParent) {
        super(new URL[]{modUrl}, null); // 不使用默认的父类加载器
        this.isRoot = false;
        this.parentCls = initialParent.toArray(new ClassLoader[0]);
    }

    public ModClassLoader(@NonNull List<ClassLoader> parents) {
        super(new URL[]{}, null); // 不使用默认的父类加载器
        if (parents.isEmpty()) {
            throw new IllegalArgumentException("Root ModClassLoader requires at least one parent ClassLoader");
        }
        this.isRoot = true;
        this.parentCls = parents.toArray(new ClassLoader[0]);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve)
    throws ClassNotFoundException {
        Class<?> cls = super.findLoadedClass(name);

        if (cls == null) {
            // 依次向父加载器请求加载类
            for (ClassLoader parent : parentCls) {
                try {
                    cls = parent.loadClass(name);
                    if (cls != null) {
                        break;
                    }
                } catch (ClassNotFoundException ignored) {
                    // 忽略异常，继续尝试下一个父加载器
                }
            }
        }

        if (cls == null) {
            if (isRoot) {
                // Root加载器无法加载类，直接抛出异常
                throw new ClassNotFoundException(name);
            }
            // 如果父加载器都无法加载，则尝试自己加载
            cls = super.findClass(name);
        }

        if (resolve) {
            super.resolveClass(cls);
        }

        return cls;
    }

    @Override
    public URL getResource(String name) {
        URL resource = null;

        // 依次向父加载器请求资源
        for (ClassLoader parent : parentCls) {
            resource = parent.getResource(name);
            if (resource != null) {

            }
        }

        // 如果父加载器都无法提供资源，则尝试自己加载
        if (resource == null) {
            if (isRoot) {
                // Root加载器无法加载资源，直接返回null
                return null;
            }
            resource = super.findResource(name);
        }

        return resource;
    }

    @Override
    public Enumeration<URL> getResources(String name)
    throws IOException {
        // 这里不进行缓存，直接依次请求所有父加载器和自己
        var resources = new ArrayList<URL>();

        for (ClassLoader parent : parentCls) {
            var parentResources = parent.getResources(name);
            while (parentResources.hasMoreElements()) {
                resources.add(parentResources.nextElement());
            }
        }

        if (!isRoot) {
            var ownResources = super.findResources(name);
            while (ownResources.hasMoreElements()) {
                resources.add(ownResources.nextElement());
            }
        }

        return Collections.enumeration(resources);
    }

    @Override
    public void close()
    throws IOException {
        super.close();
        this.parentCls = new ClassLoader[0];
    }
}
