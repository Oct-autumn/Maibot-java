package org.maibot.sdk.storage;

import javax.cache.Cache;
import java.time.Duration;

public interface GlobalCacheManager {
    /**
     * 使用全局缓存
     *
     * @return 全局缓存实例
     */
    Cache<String, String> globalCache();

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
    <K, V> Cache<K, V> createCache(
      String cacheName,
      Class<K> keyType,
      Class<V> valueType,
      int heapEntries,
      int offHeapMB,
      int diskMB,
      Duration ttl
    );
}
