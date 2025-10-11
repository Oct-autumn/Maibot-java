package org.maibot.core.cache;

import org.maibot.core.cdi.annotation.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CacheManager {
    private static final Map<String, Object> cache = new ConcurrentHashMap<>();
}
