package app.config;

import app.cache.Cache;
import app.cache.LRUCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfiguration {
    private final CacheProperties cacheProperties;

    public CacheConfiguration(CacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    @Bean
    public Cache<String, String> queryCache() {
        return new LRUCache<>(cacheProperties.getMaxSize(), cacheProperties.getTtlSeconds());
    }
}
