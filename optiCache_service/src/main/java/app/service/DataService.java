package app.service;

import app.cache.Cache;
import app.cache.CacheStats;
import app.client.ExternalApiClient;
import app.model.DataResponse;
import org.springframework.stereotype.Service;

@Service
public class DataService {
    private final Cache<String, String> queryCache;
    private final ExternalApiClient externalApiClient;

    public DataService(Cache<String, String> queryCache, ExternalApiClient externalApiClient) {
        this.queryCache = queryCache;
        this.externalApiClient = externalApiClient;
    }

    public DataResponse fetchData(String query) {
        String normalized = normalize(query);
        Object keyLock = queryCache.lockForKey(normalized);
        synchronized (keyLock) {
            String cachedValue = queryCache.get(normalized);
            if (cachedValue != null) {
                return new DataResponse(query, cachedValue, true, currentEpochSeconds());
            }
            String fetched = externalApiClient.fetchData(normalized);
            queryCache.put(normalized, fetched);
            return new DataResponse(query, fetched, false, currentEpochSeconds());
        }
    }

    public CacheStats getCacheStats() {
        return queryCache.getStats();
    }

    public void invalidate(String key) {
        if (key == null) {
            return;
        }
        queryCache.invalidate(normalize(key));
    }

    private String normalize(String query) {
        return query == null ? "" : query.trim().toLowerCase();
    }

    private long currentEpochSeconds() {
        return System.currentTimeMillis() / 1000;
    }
}
