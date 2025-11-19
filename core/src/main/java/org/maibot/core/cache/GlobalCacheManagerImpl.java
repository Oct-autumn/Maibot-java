package org.maibot.core.cache;

import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.jsr107.Eh107Configuration;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.ioc.DestroyableComponent;
import org.maibot.sdk.storage.GlobalCacheManager;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

import static java.util.Objects.requireNonNull;

@Component
public final class GlobalCacheManagerImpl implements DestroyableComponent, GlobalCacheManager {
    private final static String GLOBAL_CACHE_NAME = "global";

    private final CacheManager          jCacheManager;
    private final Cache<String, String> globalCache;

    private GlobalCacheManagerImpl() {
        // 获取 Ehcache 的 CachingProvider
        CachingProvider cachingProvider = Caching.getCachingProvider(EhcacheCachingProvider.class.getName());

        // 创建 JCache CacheManager
        try {
            var url = GlobalCacheManagerImpl.class.getResource("/META-INF/ehcache.xml");
            assert url != null;
            URI uri = url.toURI();
            this.jCacheManager = cachingProvider.getCacheManager(
              uri,
              Thread.currentThread().getContextClassLoader(),
              null
            );
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to create CacheManager", e);
        }


        // 创建并获取全局缓存
        this.globalCache = this.jCacheManager.createCache(
          GLOBAL_CACHE_NAME,
          Eh107Configuration.fromEhcacheCacheConfiguration(CacheConfigurationBuilder.newCacheConfigurationBuilder(
            String.class, String.class, ResourcePoolsBuilder.newResourcePoolsBuilder().heap(
                200,
                EntryUnit.ENTRIES
              )               // 堆内存中存储200个条目
              .offheap(100, MemoryUnit.MB)                // 堆外内存中存储100MB
              .disk(500, MemoryUnit.MB, true)              // 磁盘中存储500MB，持久化
          ).build())
        );
    }

    public CacheManager cacheManager() {
        return this.jCacheManager;
    }

    @Override
    public void preDestroy() {
        this.jCacheManager.close();
    }

    @Override
    public Cache<String, String> globalCache() {
        return this.globalCache;
    }

    @Override
    public <K, V> Cache<K, V> createCache(
      String cacheName,
      Class<K> keyType,
      Class<V> valueType,
      int heapEntries,
      int offHeapMB,
      int diskMB,
      Duration ttl
    ) {
        requireNonNull(cacheName);
        requireNonNull(keyType);
        requireNonNull(valueType);

        if (heapEntries <= 0) {
            throw new IllegalArgumentException("heapEntries must be greater than 0");
        } else if (offHeapMB < 0) {
            throw new IllegalArgumentException(
              "offHeapMB must be non-negative, if you don't want off-heap storage, set it to 0");
        } else if (diskMB < 0) {
            throw new IllegalArgumentException(
              "diskMB must be non-negative, if you don't want disk storage, set it to 0");
        } else if (ttl != null && (ttl.isNegative() || ttl.isZero())) {
            throw new IllegalArgumentException("ttl must be positive, if you don't want expiration, set it to null");
        }

        var resourcePoolsBuilder = ResourcePoolsBuilder.newResourcePoolsBuilder().heap(heapEntries, EntryUnit.ENTRIES);

        if (offHeapMB > 0) {
            resourcePoolsBuilder = resourcePoolsBuilder.offheap(offHeapMB, MemoryUnit.MB);
        }
        if (diskMB > 0) {
            resourcePoolsBuilder = resourcePoolsBuilder.disk(diskMB, MemoryUnit.MB, true);
        }

        var cacheConfigurationBuilder = CacheConfigurationBuilder.newCacheConfigurationBuilder(
          keyType,
          valueType,
          resourcePoolsBuilder
        );

        if (ttl != null) {
            var expiry = ExpiryPolicyBuilder.timeToLiveExpiration(ttl);
            cacheConfigurationBuilder = cacheConfigurationBuilder.withExpiry(expiry);
        }

        return this.jCacheManager.createCache(
          cacheName,
          Eh107Configuration.fromEhcacheCacheConfiguration(cacheConfigurationBuilder.build())
        );
    }
}
