package org.maibot.core.modloader

import org.maibot.sdk.exceptions.CircularDependence
import org.maibot.sdk.exceptions.DependencyNotExist
import org.maibot.sdk.exceptions.DuplicateMod
import org.maibot.sdk.mod.Mod
import org.semver4j.Semver
import java.net.URI
import java.net.URL
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

internal class ModTree(sdkVersion: Semver) {
    private val nodes = HashMap<String, ModNode>()

    val size: Int
        get() = nodes.size

    init {
        val sdkNode = ModNode("sdk", sdkVersion, "", URI("file:/dev/null").toURL())
        sdkNode.loaded(object : Mod() {}, "MaiBot Team", "MaiBot SDK", Thread.currentThread().getContextClassLoader())
        nodes["sdk"] = sdkNode
    }

    @Throws(DuplicateMod::class)
    fun addMod(modId: String, version: String, mainClass: String, modFileUrl: URL) {
        if (nodes.containsKey(modId)) {
            throw DuplicateMod("Duplicate mod detected: %s", modId)
        }
        val modNode = ModNode(modId, Semver(version), mainClass, modFileUrl)
        nodes[modId] = modNode
    }

    fun removeMod(modId: String) {
        nodes.remove(modId)
    }

    /**
     * 添加Mod依赖关系
     * 
     * @param modId        Mod ID
     * @param depModId     依赖的Mod ID
     * @param versionRange 版本范围
     * @param isMandatory  是否为强制依赖
     * @throws DependencyNotExist 如果依赖不存在或不满足版本要求
     */
    fun addDependency(modId: String, depModId: String, versionRange: String, isMandatory: Boolean) {
        val modModNode = nodes[modId]!!
        val depModNode = nodes[depModId] ?: if (isMandatory) {
            throw DependencyNotExist(
                "The mandatory dependency '%s' for mod '%s' does not exist.", depModId, modId
            )
        } else {
            // 非强制依赖且依赖不存在，忽略
            return
        }

        // 检查版本范围
        // versionRange 有以下两种形式：
        // 1. 精确版本号，如 "1.2.3"
        // 2. 版本区间，如 "[1.0.0, 2.0.0)", "(,1.5.0]", "[1.2.0,)"
        val depVersion = depModNode.version
        if (!checkVersion(depVersion, versionRange)) {
            throw DependencyNotExist(
                "The dependency '%s' for mod '%s' does not meet the version requirement: %s. Found version: %s",
                depModId,
                modId,
                versionRange,
                depVersion.version
            )
        }

        modModNode.dependencies.add(depModNode)
    }

    private fun checkVersion(version: Semver, range: String): Boolean {
        if (range[0] in '0'..'9') {
            // 精确版本号
            return version.isEquivalentTo(Semver(range))
        } else {
            // 版本区间
            if (range == "*") return true

            val matcher = VERSION_CONSTRAINT_PATTERN.matcher(range)
            if (!matcher.matches()) return false

            val lowerInclusive = range[0] == '['
            val upperInclusive = range[range.length - 1] == ']'

            val lbStr = matcher.group("lb")
            val rbStr = matcher.group("rb")

            if (!lbStr.isEmpty()) {
                // 检查下界
                val lb = Semver(lbStr)
                val cmp = version.compareTo(lb)
                if (cmp < 0 || (cmp == 0 && !lowerInclusive)) return false
            }
            if (!rbStr.isEmpty()) {
                // 检查上界
                val rb = Semver(rbStr)
                val cmp = version.compareTo(rb)
                if (cmp > 0 || (cmp == 0 && !upperInclusive)) return false
            }

            return true
        }
    }

    val topologicalOrder: Queue<ModNode>
        get() = resolveTopologicalOrder()

    /**
     * 获取加载顺序
     * 
     * @return 加载顺序的Mod ID队列
     * @throws CircularDependence 如果存在循环依赖则抛出异常
     */
    @Throws(CircularDependence::class)
    private fun resolveTopologicalOrder(): Queue<ModNode> {
        // Kahn算法实现拓扑排序，检测循环依赖
        val inDegree = HashMap<String, Int>()

        nodes.values.forEach { node ->
            node.dependencies.forEach { dep ->
                val depId = dep.modId
                inDegree[depId] = (inDegree[depId] ?: 0) + 1
            }
        }

        val loadOrder = LinkedList<ModNode>()
        val zeroInDegreeQueue = LinkedList<String>()

        nodes.keys.filter { !inDegree.containsKey(it) }.forEach { zeroInDegreeQueue.add(it) }

        while (!zeroInDegreeQueue.isEmpty()) {
            val modId = zeroInDegreeQueue.poll()
            val modNode = nodes[modId]!!
            loadOrder.add(modNode)

            modNode.dependencies.forEach { dep ->
                val depId = dep.modId

                inDegree.compute(depId) { _, inD ->
                    val newInD = inD!! - 1
                    if (newInD == 0) {
                        zeroInDegreeQueue.add(depId)
                    }
                    newInD
                }
            }
        }

        if (loadOrder.size != nodes.size)   // 存在循环依赖，找出未被加载的节点
            throw CircularDependence(
                "Circular dependency detected among mods: %s",
                HashSet(nodes.keys).apply { removeAll(loadOrder.map { it.modId }.toSet()) }.joinToString(", ")
            )

        return loadOrder
    }

    data class LoadedData(
        val modInstance: Mod, val author: String, val description: String, val modClassLoader: ClassLoader
    )

    data class OnLoadData(
        val mainClass: String, val modFileUrl: URL, val classLoaderFuture: CompletableFuture<ClassLoader>
    )

    class ModNode internal constructor(
        val modId: String, val version: Semver, mainClass: String, modFileUrl: URL
    ) {
        val dependencies = ArrayList<ModNode>()

        var onLoadData: OnLoadData? = OnLoadData(mainClass, modFileUrl, CompletableFuture<ClassLoader>())
            private set

        var loadedData: LoadedData? = null
            private set

        fun loaded(
            modInstance: Mod, author: String, description: String, modClassLoader: ClassLoader
        ) {
            this.loadedData = LoadedData(modInstance, author, description, modClassLoader)
            this.onLoadData = null
        }
    }

    companion object {
        private val VERSION_CONSTRAINT_PATTERN =
            Pattern.compile("^[\\[(](?<lb>[0-9a-zA-Z-+.]*), ?(?<rb>[0-9a-zA-Z-+.]*)[)\\]]$")
    }
}
