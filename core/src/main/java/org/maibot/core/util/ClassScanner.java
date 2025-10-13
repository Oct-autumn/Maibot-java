package org.maibot.core.util;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarFile;

public class ClassScanner {
    public static Set<Class<?>> fileScan(String packageName, Function<Class<?>, Boolean> filter) {
        // 通过Reflect自动扫描指定包下被filter过滤器筛选的类
        Set<Class<?>> classes = new HashSet<>();
        Deque<URL> dirs = new ArrayDeque<>();
        try {
            Thread.currentThread()
                    .getContextClassLoader()
                    .getResources(packageName.replace(".", "/"))
                    .asIterator()
                    .forEachRemaining(dirs::add);

            while (!dirs.isEmpty()) {
                var item = new File(dirs.pop().getFile());
                if (item.isDirectory()) {
                    for (var file : Objects.requireNonNull(item.listFiles())) {
                        if (file.isDirectory()) {
                            classes.addAll(ClassScanner.fileScan(packageName + "." + file.getName(), filter));
                        } else if (file.getName().endsWith(".class")) {
                            var className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                            var clazz = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
                            if (filter.apply(clazz)) {
                                classes.add(clazz);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan entity classes in package: " + packageName, e);
        }
        return classes;
    }

    public static Set<Class<?>> jarScan(ClassLoader classLoader, String packageName, Function<Class<?>, Boolean> filter) {
        // 通过Reflect自动扫描指定包下被filter过滤器筛选的类
        Set<Class<?>> classes = new HashSet<>();
        Deque<URL> dirs = new ArrayDeque<>();
        try {
            classLoader.getResources(packageName.replace(".", "/"))
                    .asIterator()
                    .forEachRemaining(dirs::add);

            while (!dirs.isEmpty()) {
                var url = dirs.pop();
                if (url.getProtocol().equals("jar")) {
                    var jarPath = url.getPath().substring(5, url.getPath().indexOf("!"));
                    try (JarFile jarFile = new JarFile(jarPath)) {
                        var entries = jarFile.entries();
                        while (entries.hasMoreElements()) {
                            var entry = entries.nextElement();
                            var entryName = entry.getName();
                            if (entryName.startsWith(packageName.replace(".", "/")) && entryName.endsWith(".class")) {
                                var className = entryName.replace("/", ".").substring(0, entryName.length() - 6);
                                var clazz = Class.forName(className, false, classLoader);
                                if (filter.apply(clazz)) {
                                    classes.add(clazz);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan entity classes in package: " + packageName, e);
        }
        return classes;
    }
}
