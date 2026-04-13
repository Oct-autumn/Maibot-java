package org.maibot.sdk.storage

import java.time.Duration
import javax.cache.Cache

interface GlobalCacheManager {
    /**
     * 使用全局缓存
     *
     * @return 全局缓存实例
     */
    fun globalCache(): Cache<String, String>

    /**
     * 创建新的缓存实例
     * 
     * @param cacheName   缓存名称
     * @param keyType     键类型
     * @param valueType   值类型
     * @param heapEntries 堆缓存条目数
     * @param offHeapMB   离堆缓存大小（MB），0表示不使用离堆缓存
     * @param diskMB      磁盘缓存大小（MB），0表示不使用磁盘缓存
     * @return 缓存实例
     */
    fun <K, V> createCacheIfAbsent(
        cacheName: String,
        keyType: Class<K>,
        valueType: Class<V>,
        heapEntries: Int,
        offHeapMB: Int = 0,
        diskMB: Int = 0,
        ttl: Duration? = null
    ): Cache<K, V>
}
