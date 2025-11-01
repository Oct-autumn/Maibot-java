package org.maibot.core.modloader;

import io.github.classgraph.ClassGraph;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ModManager implements DestroyableComponent {
    /*-- 静态区 --*/
    private static final Logger log            = LoggerFactory.getLogger(ModManager.class);
    private static final String MODS_DIRECTORY = "mods";
    private static final String MOD_META_PATH  = "META-INF/mod.toml";
    /*-- END --*/

    /*-- 单例资源区 --*/
    private final BuildInfo         buildInfo;
    private final ConfigServiceImpl configService;
    /*-- END --*/

    /*-- 私有区 --*/
    private final AtomicReference<URLClassLoader> modClassLoaderRef = new AtomicReference<>(null);
    private final Map<String, Mod>                loadedMods        = new ConcurrentHashMap<>();
    /*-- END --*/

    @AutoInject
    private ModManager(BuildInfo buildInfo, ConfigServiceImpl configService) {
        this.buildInfo = buildInfo;
        this.configService = configService;
    }

    public ClassLoader getModClassLoader() {
        return modClassLoaderRef.get();
    }

    /**
     * 载入Mod
     */
    public void loadMods() {
        // 1. 扫描mods目录，找到所有Mod文件
        // 2. 读取每个Mod的元数据，建立依赖关系树
        // 3. 根据依赖关系排序，确保依赖先行加载
        // 4. 逐个加载Mod，处理加载时的异常

        // 搜索mods目录下所有.jar结尾的文件
        log.debug("读取Mod目录...");
        var modDir = new File(MODS_DIRECTORY);
        // 确保mods目录存在
        if (!modDir.exists() && !modDir.mkdirs()) {
            log.error("无法创建mods目录，请检查权限！");
            return;
        }

        // 遍历mods目录下的所有文件
        var files = modDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (files == null || files.length == 0) {
            log.info("未找到任何Mod");
            return;
        }

        log.debug("找到 {} 个Mod文件，正在预载元数据...", files.length);
        var dependencyTree = this.preLoadMod(files);

        // TODO: 搜索Mod中的Class，应用IoC托管

        log.debug("载入Mod实例...");
        this.getModInstances(dependencyTree);
        log.info("Mod加载完成，共成功加载 {} 个Mod", loadedMods.size());
    }

    /**
     * 预载Mod元数据，建立依赖关系树
     *
     * @param modFiles Mod文件列表
     * @return 依赖关系树
     */
    private ModDependencyTree preLoadMod(File[] modFiles) {
        ModDependencyTree tree = new ModDependencyTree(this.buildInfo.sdkVersion());

        boolean needReboot = false;

        for (var file : modFiles) {
            URL url;
            try {
                url = file.toURI().toURL();
            } catch (MalformedURLException e) {
                // 不应该发生的错误，因为文件路径一定是本地文件系统的合法路径
                // 但为了安全起见，还是捕获一下
                log.warn("Mod文件路径无效: {}", file.getAbsolutePath(), e);
                continue;
            }

            String modId = null;

            // 建立URLClassLoader以读取mod.toml以及配置文件模板
            // 这里不实例化ModClass，但是Mod的配置类可能需要Jackson，因此需要为URLClassLoader提供Jackson-annotation依赖

            try (var urlClassLoader = new URLClassLoader(new URL[]{url}, this.getClass().getClassLoader())) {
                var metaData = readModMeta(urlClassLoader.getResourceAsStream(MOD_META_PATH));
                modId = metaData.modId;

                try (var scanResult = new ClassGraph().overrideClassLoaders(urlClassLoader)
                                                      .acceptPackages(metaData.packageName)
                                                      .enableAllInfo()
                                                      .scan()) {
                    var mainClassList = scanResult.getClassesWithAnnotation(ModMainClass.class);
                    if (mainClassList.size() != 1) {
                        throw new UnignorableException(
                          "Mod '%s' must have exactly one class annotated with @ModMainClass.",
                          file.getName()
                        );
                    }
                    var mainClass = mainClassList.getFirst().getName();

                    var modConfigClassList = scanResult.getClassesWithAnnotation(Configuration.class);
                    if (modConfigClassList.size() > 1) {
                        throw new UnignorableException(
                          "Mod '%s' can have at most one class annotated with @Configuration.",
                          file.getName()
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

                    tree.addMod(metaData.modId, metaData.version, mainClass, url);
                } catch (ClassNotFoundException e) {
                    // 不应该发生的错误，因为类名是从扫描结果中获取的
                    throw new UnignorableException("Mod class not found during metadata pre-loading.", e);
                }


                tree.addDependency(metaData.modId, "sdk", metaData.sdkVersion, true);

                if (metaData.dependencies != null) {
                    for (var dep : metaData.dependencies) {
                        tree.addDependency(metaData.modId, dep.modId, dep.version, dep.mandatory);
                    }
                }
            } catch (IOException | UnignorableException e) {
                log.error("读取Mod文件 {} 时发生错误", file.getName(), e);
                if (modId != null) {
                    configService.removeConfigNameSpace(modId);
                    tree.removeMod(modId);
                }
            }
        }

        if (needReboot) {
            throw new FatalError("One or more mod configuration files were created. Please restart the application.");
        }

        return tree;
    }

    /**
     * 从Toml读取Mod元数据
     *
     * @return Mod元数据对象
     * @throws UnignorableException 如果读取或解析失败
     */
    private static ModMeta readModMeta(InputStream inputStream)
    throws UnignorableException {
        if (inputStream == null) {
            throw new UnignorableException("Mod JAR does not contain %s", MOD_META_PATH);
        }

        var tomlMapper = new TomlMapper();
        return tomlMapper.readValue(inputStream, ModMeta.class);
    }

    /**
     * 根据依赖关系树加载Mod实例
     *
     * @param tree 依赖关系树
     */
    private void getModInstances(ModDependencyTree tree) {
        Queue<ModDependencyTree.MetaNode> loadOrder;

        loadOrder = tree.resolveLoadOrder();

        // 收集所有Mod的URL
        URL[] modUrls = loadOrder.stream()
                                 .filter(item -> !item.modId().equals("sdk"))
                                 .map(ModDependencyTree.MetaNode::modFileUrl)
                                 .toArray(URL[]::new);

        var modClassLoader = new URLClassLoader(modUrls, this.getClass().getClassLoader());
        modClassLoaderRef.set(modClassLoader);

        Instance.scanImplementations("", modClassLoader);

        for (ModDependencyTree.MetaNode node : loadOrder) {
            if (node.modId().equals("sdk")) continue; // 跳过SDK节点

            try {
                Class<?> modClazz = Class.forName(node.mainClass(), true, modClassLoader);
                Object modInstance = Instance.get(modClazz);
                if (modInstance instanceof Mod mod) {
                    mod.onLoad();
                    loadedMods.put(node.modId(), mod);
                    log.debug("成功加载Mod: {}", node.modId());
                } else {
                    log.error("Mod主类 {} 未实现 Mod 接口，跳过加载", node.mainClass());
                    configService.removeConfigNameSpace(node.modId());
                }
            } catch (ClassNotFoundException e) {
                // 不应该发生的错误，因为类名是从扫描结果中获取的
                throw new FatalError("Mod main class not found: %s", node.mainClass(), e);
            } catch (InstanceConstructException e) {
                throw new FatalError("Failed to construct mod instance.", e);
            } catch (Throwable e) {
                throw new FatalError("Unexpected exception when getting mod instances." + node.modId(), e);
            }
        }
    }

    @Override
    public void preDestroy() {
        this.loadedMods.values().forEach(mod -> {
            try {
                mod.onUnload();
            } catch (Throwable e) {
                log.error("卸载Mod {} 时发生异常", mod.getClass().getName(), e);
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
