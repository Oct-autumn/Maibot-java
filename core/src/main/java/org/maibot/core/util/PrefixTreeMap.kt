package org.maibot.core.util

class PrefixTreeMap<T, V> {
    private val root = PrefixTreeMapNode<T, V>()

    /**
     * 插入一个键值对
     * 
     * @param key   可迭代的键
     * @param value 值
     */
    fun insert(key: Iterable<T>, value: V) {
        var node = root

        // 遍历键的每个元素，构建前缀树
        for (k in key) {
            node.children = node.children ?: HashMap()
            node = node.children!!.computeIfAbsent(k) { PrefixTreeMapNode() }
        }

        node.isEnd = true
        node.value = value
    }

    /**
     * 搜索一个键
     * （返回最近一个最长前缀匹配的值）
     * 
     * @param key 可迭代的键
     * @return 值，如果不存在则返回null
     */
    fun search(key: Iterable<T>): V? {
        var node = root
        var lastValue: V? = null
        for (k in key) {
            node = node.children?.get(k) ?: break
            if (node.isEnd) lastValue = node.value
        }

        return lastValue
    }

    internal class PrefixTreeMapNode<T, V> {
        internal var children: MutableMap<T, PrefixTreeMapNode<T, V>>? = null
        internal var isEnd = false
        internal var value: V? = null
    }
}
