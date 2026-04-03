package org.maibot.core.modloader

import io.github.classgraph.ClassGraph
import org.maibot.core.config.BuildInfo
import org.maibot.core.config.ConfigServiceImpl
import org.maibot.core.ioc.Instance
import org.maibot.core.ioc.Instance.scanImplementations
import org.maibot.sdk.config.Configuration
import org.maibot.sdk.exceptions.FatalError
import org.maibot.sdk.exceptions.InstanceConstructException
import org.maibot.sdk.exceptions.UnignorableException
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.DestroyableComponent
import org.maibot.sdk.mod.Mod
import org.maibot.sdk.mod.ModMainClass
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tools.jackson.dataformat.toml.TomlMapper
import tools.jackson.module.kotlin.KotlinModule
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

@Component
class ModManager @AutoInject private constructor(
    buildInfo: BuildInfo, private val configService: ConfigServiceImpl
) : DestroyableComponent {
    private val modTree: ModTree = ModTree(buildInfo.sdkVersion)
    private val modClassLoaderRef = AtomicReference<ModClassLoader?>(null)

    val modClassLoader: ClassLoader?
        get() = modClassLoaderRef.get()

    /**
     * 载入Mod
     */
    fun loadMods(modList: Array<String>) {
        // 1. 读取每个Mod的元数据，建立依赖关系树
        // 2. 加载Mod，处理加载时的异常
        log.info("开始加载Mod...")

        val modUrls = modList.map {
            try {
                return@map Path.of(it).toUri().toURL()
            } catch (e: InvalidPathException) {
                log.error("Mod文件路径无效: {}", it, e)
                null
            } catch (e: MalformedURLException) {
                log.error("Mod文件路径无效: {}", it, e)
                null
            }
        }.filterNotNull().toTypedArray()

        // 预载Mod元数据，建立依赖关系树
        preLoadMod(modUrls)

        // 根据依赖关系树加载Mod实例
        createModInstances()

        log.info("Mod加载完成，共成功加载 {} 个Mod", modTree.size - 1)
    }

    /**
     * 预载Mod元数据，建立依赖关系树
     * 
     * @param modUrls Mod文件URL数组
     * @return 依赖关系树
     */
    private fun preLoadMod(modUrls: Array<URL>) {
        log.debug("正在预载Mod元数据...")

        var needReboot = false

        val tomlMapper = TomlMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .build()

        modUrls.forEach { url ->
            var modId: String? = null

            // 建立URLClassLoader以读取mod.toml以及配置文件模板
            // 这里不实例化ModClass，但是Mod的配置类可能需要Jackson，因此需要为URLClassLoader提供Jackson-annotation依赖
            try {
                URLClassLoader(
                    arrayOf(url), Thread.currentThread().getContextClassLoader()
                ).use { urlClassLoader ->
                    val inputStream = urlClassLoader.getResourceAsStream(MOD_META_PATH)
                        ?: throw UnignorableException("Mod JAR does not contain %s", MOD_META_PATH)
                    val metaData = tomlMapper.readValue(inputStream, ModMeta::class.java)
                    modId = metaData.modId

                    ClassGraph().overrideClassLoaders(urlClassLoader).acceptPackages(metaData.packageName)
                        .enableAllInfo().scan().use { scanResult ->
                            val mainClassList = scanResult.getClassesWithAnnotation(ModMainClass::class.java)
                            if (mainClassList.size != 1) {
                                throw UnignorableException(
                                    "Mod '%s' must have exactly one class annotated with @ModMainClass.", url.file
                                )
                            }
                            val mainClassName = mainClassList[0].getName()

                            val modConfigClassList = scanResult.getClassesWithAnnotation(Configuration::class.java)
                            if (modConfigClassList.size > 1) {
                                throw UnignorableException(
                                    "Mod '%s' can have at most one class annotated with @Configuration.", url.file
                                )
                            } else if (modConfigClassList.size == 1) {
                                val configClass =
                                    Class.forName(modConfigClassList[0].getName(), false, urlClassLoader)
                                urlClassLoader.getResourceAsStream(ConfigServiceImpl.MOD_CONFIG_TEMPLATE_FILE)
                                    .use { templateStream ->
                                        templateStream ?: throw UnignorableException(
                                            "无法加载Mod配置文件模板 %s，请确保该文件存在于Mod JAR的根目录下.",
                                            ConfigServiceImpl.MOD_CONFIG_TEMPLATE_FILE,
                                            metaData.modId
                                        )

                                        val loadSuccess = this.configService.loadExtraConfig(
                                            metaData.modId,
                                            configClass,
                                            metaData.modId + ".config.toml",
                                            templateStream
                                        )
                                        if (loadSuccess) {
                                            log.debug("Mod {} 的配置文件加载成功", metaData.modId)
                                        } else {
                                            log.warn(
                                                "Mod {} 的配置文件不存在，已创建默认配置文件 {}，请根据需要修改后重新启动程序。",
                                                metaData.modId,
                                                Path.of(
                                                    ConfigServiceImpl.CONFIG_DIR, metaData.modId + ".config.toml"
                                                )
                                            )
                                            needReboot = true
                                        }
                                    }
                            }
                            modTree.addMod(metaData.modId, metaData.version, mainClassName, url)
                        }

                    // 添加对SDK的依赖
                    modTree.addDependency(metaData.modId, "sdk", metaData.sdkVersion, true)
                    // 添加Mod之间的依赖
                    metaData.dependencies.forEach { dep ->
                        modTree.addDependency(metaData.modId, dep.modId, dep.version, dep.mandatory)
                    }
                }
            } catch (e: Throwable) {
                when (e) {
                    is IOException, is UnignorableException -> {
                        log.error("读取Mod文件 {} 时发生错误", url.file, e)
                        modId?.let {
                            configService.removeConfigNameSpace(it)
                            modTree.removeMod(it)
                        }
                    }

                    else -> throw e
                }
            }
        }

        if (needReboot) {
            throw FatalError("One or more mod configuration files were created. Please restart the application.")
        }
    }

    /**
     * 根据依赖关系树加载Mod实例
     */
    private fun createModInstances() {
        log.debug("载入Mod实例...")

        ThreadPoolExecutor(
            min(4, Runtime.getRuntime().availableProcessors()),
            min(8, Runtime.getRuntime().availableProcessors() * 2),
            60L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(),
            object : ThreadFactory {
                private val threadNumber = AtomicInteger(1)

                override fun newThread(r: Runnable): Thread {
                    val thread = Thread(r)
                    thread.setName("MLT-" + threadNumber.getAndIncrement())
                    thread.setContextClassLoader(Thread.currentThread().getContextClassLoader())
                    return thread
                }
            } // 创建线程池，用于并行加载Mod实例
        ).use { executor ->
            modTree.topologicalOrder.forEach { node ->
                if (node.modId == "sdk") return@forEach  // 跳过SDK节点
                executor.submit {
                    val parentClassLoaders = node.dependencies.map { depNode ->
                        depNode.onLoadData?.let { onLoadData ->
                            try {
                                // 等待依赖Mod的类加载器准备好
                                onLoadData.classLoaderFuture.get()
                            } catch (e: Exception) {
                                when (e) {
                                    is InterruptedException, is ExecutionException -> throw FatalError(
                                        "Failed to get class loader for dependency mod: %s", depNode.modId, e
                                    )

                                    else -> throw e
                                }
                            }
                        } ?: depNode.loadedData!!.modClassLoader
                    }.toList()

                    val nodeOnLoadData = node.onLoadData!!
                    val modClassLoader = ModClassLoader(
                        nodeOnLoadData.modFileUrl, parentClassLoaders
                    )

                    scanImplementations(modClassLoader)

                    // 完成类加载器的Future，供依赖它的Mod使用
                    nodeOnLoadData.classLoaderFuture.complete(modClassLoader)

                    try {
                        val mainClazz = Class.forName(nodeOnLoadData.mainClass, false, modClassLoader)
                        val modAnno = mainClazz.getAnnotation(ModMainClass::class.java)

                        // 验证是否实现了Mod接口
                        val modInstance = Instance.get(mainClazz) as? Mod ?: throw UnignorableException(
                            "Mod main class '%s' must implement the Mod interface.", nodeOnLoadData.mainClass
                        )

                        node.loaded(
                            modInstance,
                            modAnno.author,
                            modAnno.description,
                            modClassLoader
                        )

                        log.debug("成功加载Mod: {}", node.modId)
                    } catch (e: Throwable) {
                        when (e) {
                            is InstanceConstructException -> throw FatalError("Failed to construct mod instance.", e)
                            else -> throw FatalError("Unexpected exception when getting mod instances." + node.modId, e)
                        }
                    }
                }
            }

            // 收集Mod类加载器
            modClassLoaderRef.set(ModClassLoader(collectModClassLoaders()))
        }
    }

    /**
     * 收集Mod类加载器，设置Top-Level ModClassLoader的引用
     */
    private fun collectModClassLoaders(): List<ClassLoader> {
        return this.modTree.topologicalOrder.map {
            if (it.modId == "sdk") return@map null // 跳过SDK节点

            it.onLoadData?.let { onLoadData ->
                try {
                    return@map onLoadData.classLoaderFuture.get()
                } catch (e: Throwable) {
                    when (e) {
                        is InterruptedException, is ExecutionException -> throw FatalError(
                            "获取Mod %s 的类加载器时发生异常", it.modId, e
                        )

                        else -> throw e
                    }
                }
            } ?: it.loadedData!!.modClassLoader
        }.filterNotNull().toList()
    }

    fun enableMods() {
        this.modTree.topologicalOrder.forEach {
            if (it.modId == "sdk") return@forEach  // 跳过SDK节点

            it.loadedData?.let { data ->
                try {
                    data.modInstance.onEnable()
                    log.debug("成功启用Mod: {}", it.modId)
                } catch (e: Throwable) {
                    log.error("启用Mod {} 时发生异常", it.modId, e)
                }
            }
        }
    }

    override fun preDestroy() {
        this.modTree.topologicalOrder.reversed().forEach { node ->
            if (node.modId == "sdk") return@forEach  // 跳过SDK节点

            node.loadedData?.let { loadedData ->
                try {
                    loadedData.modInstance.onUnload()
                    log.debug("成功卸载Mod: {}", node.modId)
                } catch (e: Throwable) {
                    log.error("卸载Mod {} 时发生异常", node.modId, e)
                }
                this.configService.removeConfigNameSpace(node.modId)
            }
        }
        try {
            this.modClassLoaderRef.get()!!.close()
        } catch (e: IOException) {
            log.error("关闭Mod类加载器时发生异常", e)
        } finally {
            this.modClassLoaderRef.set(null)
        }
    }

    companion object {
        /*-- 静态区 --*/
        private val log: Logger = LoggerFactory.getLogger(ModManager::class.java)
        private const val MOD_META_PATH = "META-INF/mod.toml"
    }
}
