package org.maibot.core.modloader;

import com.moandjiezana.toml.Toml;
import org.maibot.core.cdi.Instance;
import org.maibot.core.cdi.annotation.AutoInject;
import org.maibot.core.cdi.annotation.Component;
import org.maibot.core.config.BuildInfo;
import org.maibot.sdk.Mod;
import org.maibot.sdk.exceptions.FatalError;
import org.maibot.sdk.exceptions.UnignorableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

@Component
public class ModManager {
    private static final Logger log            = LoggerFactory.getLogger(ModManager.class);
    private static final String MODS_DIRECTORY = "mods";
    private static final String MOD_META_PATH  = "META-INF/mod.toml";

    private final BuildInfo buildInfo;

    private final Map<String, Mod> loadedMods = new ConcurrentHashMap<>();

    @AutoInject
    private ModManager(BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    /**
     * 载入Mod
     */
    public void loadMods() {
        // 1. 扫描mods目录，找到所有Mod文件
        // 2. 读取每个Mod的元数据，建立依赖关系树
        // 3. 根据依赖关系排序，确保依赖先行加载
        // 4. 逐个加载Mod，处理加载时的异常

        log.info("正在加载Mod...");
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
        var dependencyTree = this.preLoadModMeta(files);

        log.debug("载入Mod实例...");
        this.getModInstances(files, dependencyTree);
        log.info("Mod加载完成，共成功加载 {} 个Mod", loadedMods.size());
    }

    /**
     * 预载Mod元数据，建立依赖关系树
     *
     * @param modFiles Mod文件列表
     * @return 依赖关系树
     */
    private DependencyTree preLoadModMeta(File[] modFiles) {
        DependencyTree tree = new DependencyTree(this.buildInfo.sdkVersion());

        for (var file : modFiles) {
            String modId = null;
            try (JarFile jar = new JarFile(file)) {
                var metaData = readModMeta(jar);

                modId = metaData.modId;

                tree.addMod(metaData.modId, metaData.version, metaData.mainClass);

                tree.addDependency(
                  metaData.modId,
                  "sdk",
                  metaData.sdkVersion,
                  true
                );

                if (metaData.dependencies != null) {
                    for (var dep : metaData.dependencies) {
                        tree.addDependency(
                          metaData.modId,
                          dep.modId,
                          dep.version,
                          dep.mandatory
                        );
                    }
                }
            } catch (IOException | SecurityException | UnignorableException e) {
                log.error("加载Mod文件 {} 时发生错误", file.getName(), e);
                if (modId != null) tree.removeMod(modId);   // 移除已添加的Mod节点
            }
        }

        return tree;
    }

    /**
     * 根据依赖关系树加载Mod实例
     *
     * @param modFiles Mod文件列表
     * @param tree     依赖关系树
     */
    private void getModInstances(File[] modFiles, DependencyTree tree) {
        Queue<String> loadOrder;

        loadOrder = tree.resolveLoadOrder();

        URL[] modUrls = new URL[modFiles.length];
        for (int i = 0; i < modFiles.length; i++) {
            try {
                modUrls[i] = modFiles[i].toURI().toURL();
            } catch (MalformedURLException e) {
                throw new FatalError("Malformed URL for mod file: %s", modFiles[i].getName(), e);
            }
        }

        // TODO: 拆分ClassLoader，避免Mod间类冲突
        try (URLClassLoader modClassLoader = new URLClassLoader(modUrls, this.getClass().getClassLoader())) {
            for (String instruct : loadOrder) {
                String[] parts = instruct.split(":");
                if (parts[0].equals("sdk")) continue; // 跳过SDK节点

                Class<?> modClazz = Class.forName(parts[1], true, modClassLoader);
                Object modInstance = Instance.get(modClazz);

                if (modInstance instanceof Mod mod) {
                    mod.onLoad();
                    loadedMods.put(parts[0], mod);
                    log.debug("成功加载Mod: {}", parts[0]);
                } else {
                    log.error("Mod主类 {} 未实现 Mod 接口，跳过加载", instruct);
                }
            }
        } catch (ClassNotFoundException e) {
            throw new FatalError("Mod main class not found during loading.", e);
        } catch (SecurityException | NullPointerException | IOException e) {
            throw new FatalError("Unexpected exception when getting mod instances.", e);
        }
    }

    /**
     * 从Toml读取Mod元数据
     *
     * @return Mod元数据对象
     * @throws UnignorableException 如果读取或解析失败
     */
    private static ModMeta readModMeta(JarFile modJar)
    throws UnignorableException {
        try (InputStream is = modJar.getInputStream(modJar.getJarEntry(MOD_META_PATH))) {
            if (is == null) {
                throw new UnignorableException("Mod JAR does not contain %s", MOD_META_PATH);
            }

            Toml metaToml = new Toml().read(is);

            // 考虑到Mod开发时构建脚本中提供了完善的校验，这里不再进行冗余的字段检查

            return metaToml.to(ModMeta.class);
        } catch (IOException e) {
            throw new UnignorableException("Failed to read mod metadata from %s", modJar.getName(), e);
        }
    }

}
