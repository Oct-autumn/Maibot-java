package org.maibot.core.modloader;

import lombok.NonNull;
import org.maibot.sdk.exceptions.CircularDependence;
import org.maibot.sdk.exceptions.DependencyNotExist;
import org.maibot.sdk.exceptions.DuplicateMod;
import org.semver4j.Semver;

import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

public class ModDependencyTree {
    private final Map<String, MetaNode> nodes = new HashMap<>();

    public ModDependencyTree(Semver sdkVersion) {
        nodes.put(
          "sdk", new MetaNode(
            "sdk",
            sdkVersion,
            null,
            null
          )
        );
    }

    public void addMod(String modId, String version, String mainClass, URL modFileUrl)
    throws DuplicateMod {
        if (nodes.containsKey(modId)) {
            throw new DuplicateMod("Duplicate mod detected: %s", modId);
        }
        MetaNode metaNode = new MetaNode(modId, new Semver(version), mainClass, modFileUrl);
        nodes.put(modId, metaNode);
    }

    public void removeMod(String modId) {
        nodes.remove(modId);
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
        MetaNode modMetaNode = nodes.get(modId);
        MetaNode depMetaNode = nodes.get(depModId);

        if (depMetaNode == null) {
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
        Semver depVersion = depMetaNode.version();
        if (!checkVersion(depVersion, versionRange)) {
            throw new DependencyNotExist(
              "The dependency '%s' for mod '%s' does not meet the version requirement: %s. Found version: %s",
              depModId,
              modId,
              versionRange,
              depVersion.getVersion()
            );
        }

        modMetaNode.addDependency(depMetaNode);
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
                if (cmp > 0 || (cmp == 0 && !upperInclusive)) {
                    return false;
                }
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
    public Queue<MetaNode> resolveLoadOrder()
    throws CircularDependence {
        // Kahn算法实现拓扑排序，检测循环依赖
        Map<String, Integer> inDegree = new HashMap<>();
        for (var nodeEntry : nodes.entrySet()) {
            for (var dep : nodeEntry.getValue().dependencies()) {
                inDegree.put(dep.modId(), inDegree.getOrDefault(dep.modId(), 0) + 1);
            }
        }

        Queue<MetaNode> loadOrder = new LinkedList<>();
        Queue<String> zeroInDegreeQueue = new LinkedList<>();

        for (var nodeEntry : nodes.entrySet()) {
            if (!inDegree.containsKey(nodeEntry.getKey())) {
                zeroInDegreeQueue.add(nodeEntry.getKey());
            }
        }

        while (!zeroInDegreeQueue.isEmpty()) {
            String modId = zeroInDegreeQueue.poll();
            loadOrder.add(nodes.get(modId));

            for (var dep : nodes.get(modId).dependencies()) {
                String depId = dep.modId();
                inDegree.put(depId, inDegree.get(depId) - 1);
                if (inDegree.get(depId) == 0) {
                    zeroInDegreeQueue.add(depId);
                }
            }
        }

        if (loadOrder.size() != nodes.size()) {
            Set<String> remainingNodes = new HashSet<>(nodes.keySet());
            loadOrder.forEach(item -> remainingNodes.remove(item.modId()));
            throw new CircularDependence(
              "Circular dependency detected among mods: %s",
              String.join(", ", remainingNodes)
            );
        }

        return loadOrder;
    }

    public static class MetaNode {
        private final String modId;
        private final Semver version;
        private final String mainClass;
        private final URL    modFileUrl;

        private final List<MetaNode> dependencies = new ArrayList<>();

        public MetaNode(
          @NonNull String modId,
          @NonNull Semver version,
          String mainClass,
          URL modFileUrl
        ) {
            this.modId = modId;
            this.version = version;
            this.mainClass = mainClass;
            this.modFileUrl = modFileUrl;
            if (!modId.equals("sdk")) {
                if (mainClass == null) {
                    throw new NullPointerException("mainClass");
                }
                if (modFileUrl == null) {
                    throw new NullPointerException("modFileUrl");
                }
            }

        }

        public String modId() {
            return modId;
        }

        public Semver version() {
            return version;
        }

        public String mainClass() {
            return mainClass;
        }

        public URL modFileUrl() {
            return modFileUrl;
        }

        public List<MetaNode> dependencies() {
            return dependencies;
        }

        public void addDependency(@NonNull ModDependencyTree.MetaNode metaNode) {
            this.dependencies.add(metaNode);
        }
    }
}
