/* Maibot-JE-Launcher - The launcher for Maibot-JavaEdition
 * Copyright (C) 2025 Maibot-JE Project Developers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.maibot.launcher;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import net.sourceforge.argparse4j.ArgumentParsers;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.maibot.launcher.resolver.ResolverBooter;
import org.maibot.launcher.resolver.ResolverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

public class LauncherMain {
    // Launcher工作目录
    public static final String LAUNCHER_WORK_DIR = ".maibot-launcher";

    private static final Logger log                    = LoggerFactory.getLogger("Launcher");
    // Maibot Core JAR 文件前缀
    private static final String MAIBOT_CORE_JAR_PREFIX = "core";


    public static void main(String[] args)
    throws IOException {
        var parser = ArgumentParsers.newFor("Maibot-JE Launcher").build().defaultHelp(true).description(
          "Launcher for Maibot-JavaEdition");
        parser.addArgument("--log-level")
          // 设置日志级别
          .help("Set the log level for the launcher (TRACE, DEBUG, INFO, WARN, ERROR, OFF)").setDefault("INFO");
        parser.addArgument("--to-core")
          // 传递给 Maibot Core 的参数
          .help("Arguments to pass to Maibot Core").nargs("*").setDefault();

        var ns = parser.parseArgsOrFail(args);

        try {
            var buildInfo = new BuildInfo();
            System.out.printf("<=== MaiBot-JE Launcher - %s ===>\n", buildInfo.coreVersion().getVersion());
            System.out.printf("> Build Time: %s (UTC) <\n", buildInfo.getBuildTime());
        } catch (Exception e) {
            log.error("无法获取 Launcher 版本信息", e);
            System.exit(1);
        }

        // 配置日志系统
        configLogger(ns.getString("log_level").toUpperCase());

        log.info("当前Java环境：{} {}", System.getProperty("java.version"), System.getProperty("java.vendor"));

        var workDirPath = Path.of(LAUNCHER_WORK_DIR);
        log.info("Launcher工作目录：{}", workDirPath.toAbsolutePath());

        var coreJar = searchCoreJar(workDirPath);
        log.info("找到 Core JAR 文件: {}", coreJar.getAbsolutePath());

        // MOD管理：同步运行MOD
        var modList = ModManage.syncMods();

        // 外部依赖解析与加载
        URL[] dependencyUrls;
        try (var resolverService = new ResolverService(LAUNCHER_WORK_DIR)) {
            log.info("处理依赖中...");

            // 收集依赖
            var rootNode = collectDependencies(
              coreJar,
              modList,
              resolverService.repoSystem,
              resolverService.repoSession
            );

            // 加载依赖
            dependencyUrls = loadDependencies(
              rootNode,
              resolverService.repoSystem,
              resolverService.repoSession,
              coreJar
            );
        }

        // 创建URL类加载器并启动 Maibot Core
        try (var urlClassLoader = new URLClassLoader(dependencyUrls, ClassLoader.getSystemClassLoader())) {
            // 设置当前线程的上下文类加载器
            Thread.currentThread().setContextClassLoader(urlClassLoader);

            CoreBooter.launchMaibotCore(urlClassLoader, modList);
        } finally {
            // 恢复上下文类加载器
            Thread.currentThread().setContextClassLoader(LauncherMain.class.getClassLoader());
        }
    }

    private static void configLogger(String logLevel) {
        // 设置日志级别
        var context = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("ROOT");
        context.setLevel(Level.toLevel(logLevel));
    }

    private static URL[] loadDependencies(
      DependencyNode rootNode,
      RepositorySystem repoSystem,
      RepositorySystemSession.CloseableSession repoSession,
      File coreJar
    ) {
        var dependencyRequest = new DependencyRequest(rootNode, null);

        log.info("加载依赖中...");

        DependencyResult dependencyResult;
        try {
            dependencyResult = repoSystem.resolveDependencies(repoSession, dependencyRequest);
        } catch (DependencyResolutionException e) {
            log.error("加载依赖时发生错误，已终止启动", e);
            System.exit(1);
            throw new RuntimeException(e); // 永远不会执行到这里
        }

        var artifactResults = dependencyResult.getArtifactResults();

        var artifactUrlSet = new HashSet<URL>(artifactResults.size());

        try {
            var url = coreJar.toURI().toURL();
            artifactUrlSet.add(url);
        } catch (MalformedURLException e) {
            log.error("转换 Maibot Core JAR 文件路径为 URL 时发生错误，已终止启动", e);
            System.exit(1);
        }

        artifactResults.forEach(result -> {
            try {
                // 转换为 URL 并添加到集合中
                // Set 自动去重
                var url = result.getArtifact().getPath().toUri().toURL();
                artifactUrlSet.add(url);
            } catch (IOException e) {
                log.error("转换依赖 JAR 文件路径为 URL 时发生错误，已终止启动", e);
                System.exit(1);
                // 永远不会执行到这里
            }
        });
        // 去重并收集为列表
        var dependencyUrls = artifactUrlSet.toArray(new URL[0]);

        log.info("共成功加载了 {} 个依赖项", dependencyUrls.length);
        return dependencyUrls;
    }

    private static DependencyNode collectDependencies(
      File coreJar,
      List<String> modList,
      RepositorySystem repoSystem,
      RepositorySystemSession.CloseableSession repoSession
    )
    throws IOException {
        log.info("收集依赖中...");

        Set<DependencyNode> dependencies = new HashSet<>();

        if (collectDependenciesFromJar(coreJar, dependencies, repoSystem, repoSession)) {
            log.error("Maibot Core JAR 文件的依赖解析失败，请检查对应的日志信息以获取更多细节");
            System.exit(1);
        }

        // 读取JAR包，"META-INF/build-inf.properties"文件，获取依赖列表
        var modsDirPath = Path.of("mods");
        Utils.ensureDirExists(modsDirPath);

        var exceptionMods = new ArrayList<String>();
        for (String modJar : modList) {
            log.debug("正在收集 Mod JAR 文件 {} 的依赖...", modJar);
            if (collectDependenciesFromJar(new File(modJar), dependencies, repoSystem, repoSession)) {
                exceptionMods.add(modJar);
            }
        }
        if (!exceptionMods.isEmpty()) {
            log.error(
              "以下 Mod JAR 文件的依赖解析失败，请检查对应的日志信息以获取更多细节：\n -{}",
              String.join("\n -", exceptionMods)
            );
            System.exit(1);
        }

        var rootNode = new DefaultDependencyNode((Dependency) null);
        rootNode.setChildren(dependencies.stream().toList());
        rootNode.setData("A-ID", "[root]");

        // 输出依赖树
        if (log.isDebugEnabled()) {
            log.debug("依赖树：");
            ResolverBooter.Utils.recursivePrintDependencyTree(rootNode, "");
        }
        return rootNode;
    }

    private static boolean collectDependenciesFromJar(
      File jarFile,
      Set<DependencyNode> dependencySet,
      RepositorySystem system,
      RepositorySystemSession session
    )
    throws IOException {
        try (var jar = new JarFile(jarFile)) {
            var buildInfoEntry = jar.getJarEntry("META-INF/build-inf.properties");
            if (buildInfoEntry == null) {
                log.error("在 JAR '{}' 中未找到 META-INF/build-inf.properties", jarFile.getAbsolutePath());
                return true;
            }

            var buildInfoStream = jar.getInputStream(buildInfoEntry);
            var properties = new Properties();
            properties.load(buildInfoStream);

            var deps = properties.getProperty("implDeps");
            if (deps == null) {
                log.warn("JAR '{}' 中的 build-inf.properties 未定义 implDeps 属性", jarFile.getAbsolutePath());
                return true;
            }
            if (deps.isBlank()) {
                log.warn("JAR '{}' 中的 build-inf.properties 定义的 implDeps 属性为空", jarFile.getAbsolutePath());
                return false; // 没有依赖
            }

            var artifactId = properties.getProperty("artifactId");
            if (artifactId == null || artifactId.isBlank()) {
                log.error("JAR '{}' 中的 build-inf.properties 未定义 artifactId 属性", jarFile.getAbsolutePath());
                return true;
            }

            var childrenDepsCoords = new HashSet<String>();

            for (var dep : deps.split(",")) {
                if (dep.isBlank()) {
                    continue;
                } else if (!dep.matches("^[^:]+:[^:]+(:[^:]+)?$")) {
                    log.debug("跳过依赖项 '{}'", dep);
                    continue;
                }

                childrenDepsCoords.add(dep);
            }

            var collectRequest = new CollectRequest().setDependencies(childrenDepsCoords.stream()
                                                                        .map(coords -> new Dependency(
                                                                          new DefaultArtifact(coords),
                                                                          "compile"
                                                                        ))
                                                                        .toList()).setRepositories(
              ResolverBooter.newRemoteRepositories());

            CollectResult collectResult;
            try {
                collectResult = system.collectDependencies(session, collectRequest);
            } catch (DependencyCollectionException e) {
                log.error("收集 JAR '{}' 的依赖时发生异常", jarFile.getAbsolutePath(), e);

                if (e.getResult() == null) {
                    return true;
                }
                collectResult = e.getResult();
            }
            var rootNode = collectResult.getRoot();

            log.debug("成功收集 JAR '{}' 的依赖", jarFile.getAbsolutePath());

            rootNode.setData("A-ID", artifactId);

            dependencySet.add(rootNode);
            return false;
        }
    }

    private static File searchCoreJar(Path workDirPath) {
        var coreFindResult = Utils.findFiles(workDirPath, String.format("^%s-.+\\.jar$", MAIBOT_CORE_JAR_PREFIX));

        if (coreFindResult.isEmpty()) {
            log.error(
              "未找到 Maibot Core JAR 文件，请确保工作目录下存在符合 {}-*.jar 命名的文件",
              MAIBOT_CORE_JAR_PREFIX
            );
            System.exit(1);
        } else if (coreFindResult.size() > 1) {
            log.error(
              "找到多个 Maibot Core JAR 文件，请确保工作目录下仅存在一个符合 {}-*.jar 命名规范的文件，当前找到: {}",
              MAIBOT_CORE_JAR_PREFIX,
              coreFindResult
            );
            System.exit(1);
        }

        return coreFindResult.getFirst();
    }
}
