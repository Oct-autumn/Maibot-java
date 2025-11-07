package org.maibot.core.modloader;

import io.github.classgraph.ClassGraph;
import lombok.NonNull;
import org.maibot.core.config.BuildInfo;
import org.maibot.core.config.ConfigServiceImpl;
import org.maibot.core.ioc.Instance;
import org.maibot.sdk.config.Configuration;
import org.maibot.sdk.exceptions.FatalError;
import org.maibot.sdk.exceptions.InstanceConstructException;
import org.maibot.sdk.exceptions.UnignorableException;
import org.maibot.sdk.ioc.AutoInject;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.ioc.DestroyableComponent;
import org.maibot.sdk.mod.Mod;
import org.maibot.sdk.mod.ModMainClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.dataformat.toml.TomlMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ModManager implements DestroyableComponent {
    /*-- 静态区 --*/
    private static final Logger log           = LoggerFactory.getLogger(ModManager.class);
    private static final String MOD_META_PATH = "META-INF/mod.toml";
    /*-- END --*/

    /*-- 单例资源区 --*/
    private final ConfigServiceImpl configService;
    /*-- END --*/

    /*-- 私有区 --*/
    private final ModTree                         modTree;
    private final AtomicReference<ModClassLoader> modClassLoaderRef = new AtomicReference<>(null);
    /*-- END --*/

    @AutoInject
    private ModManager(BuildInfo buildInfo, ConfigServiceImpl configService) {
        this.configService = configService;
        this.modTree = new ModTree(buildInfo.sdkVersion());
    }

    public ClassLoader getModClassLoader() {
        return modClassLoaderRef.get();
    }

    /**
     * 载入Mod
     */
    public void loadMods(String[] modList) {
        // 1. 读取每个Mod的元数据，建立依赖关系树
        // 2. 加载Mod，处理加载时的异常
        log.info("开始加载Mod...");

        URL[] modUrls = new URL[modList.length];
        for (int i = 0; i < modList.length; i++) {
            try {
                modUrls[i] = Path.of(modList[i]).toUri().toURL();
            } catch (InvalidPathException | MalformedURLException e) {
                log.error("Mod文件路径无效: {}", modList[i], e);
            }
        }

        // 预载Mod元数据，建立依赖关系树
        this.preLoadMod(modUrls);

        // 根据依赖关系树加载Mod实例
        this.getModInstances();

        log.info("Mod加载完成，共成功加载 {} 个Mod", modTree.size() - 1);
    }

    /**
     * 预载Mod元数据，建立依赖关系树
     *
     * @param modUrls Mod文件URL数组
     * @return 依赖关系树
     */
    private void preLoadMod(URL[] modUrls) {
        log.debug("正在预载Mod元数据...");

        boolean needReboot = false;

        var tomlMapper = new TomlMapper();

        for (var url : modUrls) {
            String modId = null;

            // 建立URLClassLoader以读取mod.toml以及配置文件模板
            // 这里不实例化ModClass，但是Mod的配置类可能需要Jackson，因此需要为URLClassLoader提供Jackson-annotation依赖
            try (var urlClassLoader = new URLClassLoader(
              new URL[]{url},
              Thread.currentThread().getContextClassLoader()
            )) {
                InputStream inputStream = urlClassLoader.getResourceAsStream(MOD_META_PATH);
                if (inputStream == null) {
                    throw new UnignorableException("Mod JAR does not contain %s", MOD_META_PATH);
                }
                var metaData = tomlMapper.readValue(inputStream, ModMeta.class);
                modId = metaData.modId;

                try (var scanResult = new ClassGraph().overrideClassLoaders(urlClassLoader)
                                                      .acceptPackages(metaData.packageName)
                                                      .enableAllInfo()
                                                      .scan()) {
                    var mainClassList = scanResult.getClassesWithAnnotation(ModMainClass.class);
                    if (mainClassList.size() != 1) {
                        throw new UnignorableException(
                          "Mod '%s' must have exactly one class annotated with @ModMainClass.",
                          url.getFile()
                        );
                    }
                    var mainClass = mainClassList.getFirst().getName();

                    var modConfigClassList = scanResult.getClassesWithAnnotation(Configuration.class);
                    if (modConfigClassList.size() > 1) {
                        throw new UnignorableException(
                          "Mod '%s' can have at most one class annotated with @Configuration.",
                          url.getFile()
                        );
                    }
                    if (modConfigClassList.size() == 1) {
                        var configClass = Class.forName(modConfigClassList.getFirst().getName(), false, urlClassLoader);
                        try (var templateStream = urlClassLoader.getResourceAsStream(ConfigServiceImpl.MOD_CONFIG_TEMPLATE_FILE)) {
                            var loadSuccess = this.configService.loadExtraConfig(
                              metaData.modId,
                              configClass,
                              metaData.modId + ".config.toml",
                              templateStream
                            );
                            if (loadSuccess) {
                                log.debug("Mod {} 的配置文件加载成功", metaData.modId);
                            } else {
                                log.warn(
                                  "Mod {} 的配置文件不存在，已创建默认配置文件 {}，请根据需要修改后重新启动程序。",
                                  metaData.modId,
                                  Path.of(ConfigServiceImpl.CONFIG_DIR, metaData.modId + ".config.toml")
                                );
                                needReboot = true;
                            }
                        }
                    }

                    this.modTree.addMod(metaData.modId, metaData.version, mainClass, url);
                } catch (ClassNotFoundException e) {
                    // 不应该发生的错误，因为类名是从扫描结果中获取的
                    throw new UnignorableException("Mod class not found during metadata pre-loading.", e);
                }

                // 添加对SDK的依赖
                this.modTree.addDependency(metaData.modId, "sdk", metaData.sdkVersion, true);

                if (metaData.dependencies != null) {
                    for (var dep : metaData.dependencies) {
                        this.modTree.addDependency(metaData.modId, dep.modId, dep.version, dep.mandatory);
                    }
                }
            } catch (IOException | UnignorableException e) {
                log.error("读取Mod文件 {} 时发生错误", url.getFile(), e);
                if (modId != null) {
                    configService.removeConfigNameSpace(modId);
                    this.modTree.removeMod(modId);
                }
            }
        }

        if (needReboot) {
            throw new FatalError("One or more mod configuration files were created. Please restart the application.");
        }
    }

    /**
     * 根据依赖关系树加载Mod实例
     */
    private void getModInstances() {
        log.debug("载入Mod实例...");

        // 并行加载Mod
        try (var executor = new ThreadPoolExecutor(
          Math.min(4, Runtime.getRuntime().availableProcessors()),
          Math.min(8, Runtime.getRuntime().availableProcessors() * 2),
          60L,
          TimeUnit.SECONDS,
          new LinkedBlockingQueue<>(),
          new ThreadFactory() {
              private final AtomicInteger threadNumber = new AtomicInteger(1);

              @Override
              public Thread newThread(@NonNull Runnable r) {
                  Thread thread = new Thread(r);
                  thread.setName("MLT-" + threadNumber.getAndIncrement());
                  thread.setContextClassLoader(Thread.currentThread().getContextClassLoader());
                  return thread;
              }
          }
          // 创建线程池
        )) {
            for (var node : this.modTree.resolveTopologicalOrder()) {
                if (node.modId().equals("sdk")) continue; // 跳过SDK节点

                executor.submit(() -> {
                    var parentClassLoaders = node.dependencies().stream().map(depNode -> {
                        if (depNode.modId().equals("sdk")) {
                            return Thread.currentThread().getContextClassLoader();
                        }
                        if (depNode.onLoadData() != null) {
                            try { // 等待依赖Mod的类加载器准备好
                                return depNode.onLoadData().classLoaderFuture().get();
                            } catch (InterruptedException | ExecutionException e) {
                                throw new FatalError(
                                  "Failed to get class loader for dependency mod: %s",
                                  depNode.modId(),
                                  e
                                );
                            }
                        } else {
                            // 依赖Mod已加载，直接获取其类加载器
                            return depNode.loadedData().modClassLoader();
                        }
                    }).toList();
                    var modClassLoader = new ModClassLoader(
                      node.onLoadData().modFileUrl(),
                      parentClassLoaders
                    );

                    Instance.scanImplementations("", modClassLoader);

                    // 完成类加载器的Future，供依赖它的Mod使用
                    node.onLoadData().classLoaderFuture().complete(modClassLoader);

                    try {
                        Class<?> modClazz = Class.forName(node.onLoadData().mainClass(), true, modClassLoader);
                        var modAuthor = modClazz.getAnnotation(ModMainClass.class).author();
                        var modDescription = modClazz.getAnnotation(ModMainClass.class).description();
                        Object modInstance = Instance.get(modClazz);
                        if (modInstance instanceof Mod mod) {
                            mod.onLoad();
                            node.loaded(mod, modAuthor, modDescription, modClassLoader);
                            log.debug("成功加载Mod: {}", node.modId());
                        } else {
                            log.error("Mod主类 {} 未实现 Mod 接口，跳过加载", node.onLoadData().mainClass());
                            configService.removeConfigNameSpace(node.modId());
                        }
                    } catch (ClassNotFoundException ignored) {
                        // 不应该发生的错误，因为类名是从扫描结果中获取的
                    } catch (InstanceConstructException e) {
                        throw new FatalError("Failed to construct mod instance.", e);
                    } catch (Throwable e) {
                        throw new FatalError("Unexpected exception when getting mod instances." + node.modId(), e);
                    }
                });
            }

            // 收集各mod的类加载器
            List<ClassLoader> modClassLoaders = this.modTree.resolveTopologicalOrder()
                                                            .stream()
                                                            .filter(node -> !node.modId().equals("sdk"))
                                                            .map(node -> {
                                                                try {
                                                                    return node.onLoadData().classLoaderFuture().get();
                                                                } catch (InterruptedException | ExecutionException e) {
                                                                    throw new FatalError(
                                                                      "获取Mod %s 的类加载器时发生异常",
                                                                      node.modId(),
                                                                      e
                                                                    );
                                                                }
                                                            })
                                                            .toList();
            this.modClassLoaderRef.set(new ModClassLoader(modClassLoaders));
        }
    }

    @Override
    public void preDestroy() {
        this.modTree.resolveTopologicalOrder().forEach(node -> {
            if (node.modId().equals("sdk")) return; // 跳过SDK节点
            var loadedData = node.loadedData();
            if (loadedData != null) {
                try {
                    loadedData.modInstance().onUnload();
                    log.debug("成功卸载Mod: {}", node.modId());
                } catch (Throwable e) {
                    log.error("卸载Mod {} 时发生异常", node.modId(), e);
                }
                this.configService.removeConfigNameSpace(node.modId());
            }
        });
        try {
            this.modClassLoaderRef.get().close();
        } catch (IOException e) {
            log.error("关闭Mod类加载器时发生异常", e);
        } finally {
            this.modClassLoaderRef.set(null);
        }
    }
}
