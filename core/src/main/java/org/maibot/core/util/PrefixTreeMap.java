package org.maibot.core.util;

import java.util.HashMap;
import java.util.Map;

public class PrefixTreeMap<T, V> {
    static class PrefixTreeMapNode<T, V> {
        private Map<T, PrefixTreeMapNode<T, V>> children = null;
        private boolean isEnd = false;
        private V value = null;
    }

    private final PrefixTreeMapNode<T, V> root = new PrefixTreeMapNode<>();

    /**
     * 插入一个键值对
     *
     * @param key   可迭代的键
     * @param value 值
     */
    public void insert(Iterable<T> key, V value) {
        var node = root;
        for (var k : key) {
            if (node.children == null) {
                node.children = new HashMap<>();
            }
            node = node.children.computeIfAbsent(k, x -> new PrefixTreeMapNode<>());
        }
        node.isEnd = true;
        node.value = value;
    }

    /**
     * 搜索一个键
     * （返回最近一个最长前缀匹配的值）
     *
     * @param key 可迭代的键
     * @return 值，如果不存在则返回null
     */
    public V search(Iterable<T> key) {
        PrefixTreeMapNode<T, V> node = root;
        V lastValue = null;
        for (var k : key) {
            if (node.children == null || !node.children.containsKey(k)) {
                break;
            }
            node = node.children.get(k);
            if (node.isEnd) {
                lastValue = node.value;
            }
        }
        return lastValue;
    }
}
