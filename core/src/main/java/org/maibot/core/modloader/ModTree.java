package org.maibot.core.modloader;

import org.maibot.sdk.exceptions.CircularDependence;
import org.maibot.sdk.exceptions.DependencyNotExist;
import org.maibot.sdk.exceptions.DuplicateMod;
import org.maibot.sdk.mod.Mod;
import org.semver4j.Semver;

import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

class ModTree {
    private final Map<String, ModNode> nodes = new HashMap<>();

    public ModTree(Semver sdkVersion) {
        var sdkNode = new ModNode("sdk", sdkVersion, null, null);
        sdkNode.loaded(null, "MaiBot Team", "MaiBot SDK", null);
        nodes.put("sdk", sdkNode);
    }

    public void addMod(String modId, String version, String mainClass, URL modFileUrl)
    throws DuplicateMod {
        if (nodes.containsKey(modId)) {
            throw new DuplicateMod("Duplicate mod detected: %s", modId);
        }
        ModNode modNode = new ModNode(modId, new Semver(version), mainClass, modFileUrl);
        nodes.put(modId, modNode);
    }

    public void removeMod(String modId) {
        nodes.remove(modId);
    }

    public int size() {
        return nodes.size();
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
    public void addDependency(String modId, String depModId, String versionRange, boolean isMandatory) {
        ModNode modModNode = nodes.get(modId);
        ModNode depModNode = nodes.get(depModId);

        if (depModNode == null) {
            if (isMandatory) {
                throw new DependencyNotExist(
                  "The mandatory dependency '%s' for mod '%s' does not exist.",
                  depModId,
                  modId
                );
            }
            // 非强制依赖且依赖不存在，忽略
            return;
        }

        // 检查版本范围
        // versionRange 有以下两种形式：
        // 1. 精确版本号，如 "1.2.3"
        // 2. 版本区间，如 "[1.0.0, 2.0.0)", "(,1.5.0]", "[1.2.0,)"
        Semver depVersion = depModNode.version;
        if (!checkVersion(depVersion, versionRange)) {
            throw new DependencyNotExist(
              "The dependency '%s' for mod '%s' does not meet the version requirement: %s. Found version: %s",
              depModId,
              modId,
              versionRange,
              depVersion.getVersion()
            );
        }

        modModNode.dependencies.add(depModNode);
    }

    private boolean checkVersion(Semver version, String range) {
        if (range.charAt(0) >= '0' && range.charAt(0) <= '9') {
            // 精确版本号
            return version.isEquivalentTo(new Semver(range));
        } else {
            // 版本区间
            if (range.equals("*")) {
                return true;
            }

            Pattern pattern = Pattern.compile("^[\\[(](?<lb>[0-9a-zA-Z-+.]*), ?(?<rb>[0-9a-zA-Z-+.]*)[)\\]]$");
            var matcher = pattern.matcher(range);
            if (!matcher.find()) {
                return false;
            }
            boolean lowerInclusive = range.charAt(0) == '[';
            boolean upperInclusive = range.charAt(range.length() - 1) == ']';

            String lbStr = matcher.group("lb");
            String rbStr = matcher.group("rb");

            if (!lbStr.isEmpty()) {
                Semver lb = new Semver(lbStr);
                int cmp = version.compareTo(lb);
                if (cmp < 0 || (cmp == 0 && !lowerInclusive)) {
                    return false;
                }
            }
            if (!rbStr.isEmpty()) {
                Semver rb = new Semver(rbStr);
                int cmp = version.compareTo(rb);
                return cmp <= 0 && (cmp != 0 || upperInclusive);
            }
        }
        return true;
    }

    /**
     * 获取加载顺序
     *
     * @return 加载顺序的Mod ID队列
     * @throws CircularDependence 如果存在循环依赖则抛出异常
     */
    public Queue<ModNode> resolveTopologicalOrder()
    throws CircularDependence {
        // Kahn算法实现拓扑排序，检测循环依赖
        Map<String, Integer> inDegree = new HashMap<>();
        for (var nodeEntry : nodes.entrySet()) {
            for (var dep : nodeEntry.getValue().dependencies) {
                inDegree.put(dep.modId, inDegree.getOrDefault(dep.modId, 0) + 1);
            }
        }

        Queue<ModNode> loadOrder = new LinkedList<>();
        Queue<String> zeroInDegreeQueue = new LinkedList<>();

        for (var nodeEntry : nodes.entrySet()) {
            if (!inDegree.containsKey(nodeEntry.getKey())) {
                zeroInDegreeQueue.add(nodeEntry.getKey());
            }
        }

        while (!zeroInDegreeQueue.isEmpty()) {
            String modId = zeroInDegreeQueue.poll();
            loadOrder.add(nodes.get(modId));

            for (var dep : nodes.get(modId).dependencies) {
                String depId = dep.modId;
                inDegree.put(depId, inDegree.get(depId) - 1);
                if (inDegree.get(depId) == 0) {
                    zeroInDegreeQueue.add(depId);
                }
            }
        }

        if (loadOrder.size() != nodes.size()) {
            Set<String> remainingNodes = new HashSet<>(nodes.keySet());
            loadOrder.forEach(item -> remainingNodes.remove(item.modId));
            throw new CircularDependence(
              "Circular dependency detected among mods: %s",
              String.join(", ", remainingNodes)
            );
        }

        return loadOrder;
    }

    public record LoadedData(
      Mod modInstance,
      String author,
      String description,
      ModClassLoader modClassLoader
    ) {
    }

    public record OnLoadData(
      String mainClass,
      URL modFileUrl,
      CompletableFuture<ClassLoader> classLoaderFuture
    ) {
    }

    public static class ModNode {
        private final String        modId;
        private final Semver        version;
        private final List<ModNode> dependencies = new ArrayList<>();

        private LoadedData loadedData;
        private OnLoadData onLoadData;

        public ModNode(String modId, Semver version, String mainClass, URL modFileUrl) {
            this.modId = modId;
            this.version = version;

            this.loadedData = null;
            this.onLoadData = new OnLoadData(mainClass, modFileUrl, new CompletableFuture<>());
        }

        public void loaded(Mod modInstance, String author, String description, ModClassLoader modClassLoader) {
            this.loadedData = new LoadedData(modInstance, author, description, modClassLoader);
            this.onLoadData = null;
        }

        public String modId() {
            return modId;
        }

        public Semver version() {
            return version;
        }

        public List<ModNode> dependencies() {
            return dependencies;
        }

        public LoadedData loadedData() {
            return loadedData;
        }

        public OnLoadData onLoadData() {
            return onLoadData;
        }
    }
}
