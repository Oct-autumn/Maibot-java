package org.maibot.core.cache

import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.ExpiryPolicyBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import org.ehcache.config.units.EntryUnit
import org.ehcache.config.units.MemoryUnit
import org.ehcache.jsr107.Eh107Configuration
import org.ehcache.jsr107.EhcacheCachingProvider
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.DestroyableComponent
import org.maibot.sdk.storage.GlobalCacheManager
import java.net.URISyntaxException
import java.net.URL
import java.time.Duration
import javax.cache.Cache
import javax.cache.CacheManager
import javax.cache.Caching

@Component
class GlobalCacheManagerImpl private constructor() : DestroyableComponent, GlobalCacheManager {
    private val jCacheManager: CacheManager
    private val globalCache: Cache<String, String>

    init {
        // 获取 Ehcache 的 CachingProvider
        val cachingProvider = Caching.getCachingProvider(EhcacheCachingProvider::class.java.getName())

        // 创建 JCache CacheManager
        try {
            val url: URL = GlobalCacheManagerImpl::class.java.getResource("/META-INF/ehcache.xml")
                ?: throw RuntimeException("Failed to load ehcache.xml from classpath")
            val uri = url.toURI()

            this.jCacheManager = cachingProvider.getCacheManager(
                uri,
                Thread.currentThread().getContextClassLoader(),
                null
            )
        } catch (e: URISyntaxException) {
            throw RuntimeException("Failed to create CacheManager", e)
        }


        // 创建并获取全局缓存
        this.globalCache = this.jCacheManager.createCache(
            GLOBAL_CACHE_NAME,
            Eh107Configuration.fromEhcacheCacheConfiguration(
                CacheConfigurationBuilder.newCacheConfigurationBuilder(
                    String::class.java, String::class.java, ResourcePoolsBuilder.newResourcePoolsBuilder()
                        .heap(200, EntryUnit.ENTRIES) // 堆内存中存储200个条目
                        .offheap(100, MemoryUnit.MB) // 堆外内存中存储100MB
                        .disk(500, MemoryUnit.MB, true) // 磁盘中存储500MB，持久化
                ).build()
            )
        )
    }

    fun cacheManager(): CacheManager {
        return this.jCacheManager
    }

    override fun preDestroy() {
        this.jCacheManager.close()
    }

    override fun globalCache(): Cache<String, String> {
        return this.globalCache
    }

    override fun <K, V> createCacheIfAbsent(
        cacheName: String,
        keyType: Class<K>,
        valueType: Class<V>,
        heapEntries: Int,
        offHeapMB: Int,
        diskMB: Int,
        ttl: Duration?
    ): Cache<K, V> {
        if (this.jCacheManager.cacheNames.contains(cacheName)) {
            return this.jCacheManager.getCache(cacheName, keyType, valueType)
                ?: throw RuntimeException("Cache with name '$cacheName' already exists but has incompatible key/value types")
        }

        require(heapEntries > 0) { "heapEntries must be greater than 0" }
        require(offHeapMB >= 0) { "offHeapMB must be non-negative, if you don't want off-heap storage, set it to 0" }
        require(diskMB >= 0) { "diskMB must be non-negative, if you don't want disk storage, set it to 0" }
        require(!(ttl != null && (ttl.isNegative || ttl.isZero))) { "ttl must be positive, if you don't want expiration, set it to null" }

        val resourcePoolsBuilder =
            ResourcePoolsBuilder.newResourcePoolsBuilder().heap(heapEntries.toLong(), EntryUnit.ENTRIES).apply {
                if (offHeapMB > 0) {
                    offheap(offHeapMB.toLong(), MemoryUnit.MB)
                }
                if (diskMB > 0) {
                    disk(diskMB.toLong(), MemoryUnit.MB, true)
                }
            }

        val cacheConfigurationBuilder = CacheConfigurationBuilder.newCacheConfigurationBuilder(
            keyType,
            valueType,
            resourcePoolsBuilder
        ).apply {
            ttl?.let {
                withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(it))
            }
        }

        return this.jCacheManager.createCache(
            cacheName,
            Eh107Configuration.fromEhcacheCacheConfiguration(cacheConfigurationBuilder.build())
        )
    }

    companion object {
        private const val GLOBAL_CACHE_NAME = "global"
    }
}
