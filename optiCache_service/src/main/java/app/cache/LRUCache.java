package app.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class LRUCache<K, V> implements Cache<K, V> {
    private final int maxSize;
    private final long ttlNanos;
    private final ExpirationPolicy expirationPolicy;
    private final Map<K, CacheEntry<V>> store;
    private final Object storeLock = new Object();
    private final ConcurrentHashMap<K, Object> keyLocks = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    /**
     * Expiration policy: determines how entries are expired.
     * WRITE: expired based on creation time
     * ACCESS: expired based on last access time
     */
    public enum ExpirationPolicy {
        WRITE, ACCESS
    }

    public LRUCache(int maxSize, long ttlSeconds) {
        this(maxSize, ttlSeconds, ExpirationPolicy.WRITE);
    }

    public LRUCache(int maxSize, long ttlSeconds, ExpirationPolicy expirationPolicy) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be positive");
        }
        this.maxSize = maxSize;
        this.ttlNanos = ttlSeconds * 1_000_000_000L;
        this.expirationPolicy = expirationPolicy;
        // LinkedHashMap with access-order (true = LRU ordering)
        this.store = new LinkedHashMap<K, CacheEntry<V>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                // Let the cache decide eviction, not automatic removal
                return false;
            }
        };
    }

    @Override
    public V get(K key) {
        if (key == null) {
            return null;
        }
        long now = System.nanoTime();
        synchronized (storeLock) {
            CacheEntry<V> entry = store.get(key);
            if (entry == null) {
                misses.incrementAndGet();
                return null;
            }
            if (isExpired(entry, now)) {
                store.remove(key);
                misses.incrementAndGet();
                return null;
            }
            entry.access(now);
            hits.incrementAndGet();
            return entry.getValue();
        }
    }

    @Override
    public void put(K key, V value) {
        if (key == null || value == null) {
            return;
        }
        long now = System.nanoTime();
        synchronized (storeLock) {
            store.put(key, new CacheEntry<>(value, now));
            evictIfNeeded();
        }
    }

    @Override
    public void invalidate(K key) {
        if (key == null) {
            return;
        }
        synchronized (storeLock) {
            store.remove(key);
        }
        // Clean up the lock to prevent memory leak
        // This helps reduce accumulation over time
        keyLocks.remove(key);
    }

    @Override
    public CacheStats getStats() {
        return new CacheStats(hits.get(), misses.get(), evictions.get());
    }

    @Override
    public Object lockForKey(K key) {
        return keyLocks.computeIfAbsent(key, k -> new Object());
    }

    /**
     * Determines if a cache entry has expired based on configured policy.
     * WRITE: expires based on creation time
     * ACCESS: expires based on last access time
     */
    private boolean isExpired(CacheEntry<V> entry, long now) {
        return switch (expirationPolicy) {
            case WRITE -> now - entry.getCreatedAtNanos() >= ttlNanos;
            case ACCESS -> now - entry.getLastAccessedAtNanos() >= ttlNanos;
        };
    }

    /**
     * Evicts the least recently used entry when cache exceeds maxSize.
     * Uses LinkedHashMap's natural LRU ordering (access-order iteration).
     */
    private void evictIfNeeded() {
        while (store.size() > maxSize) {
            // LinkedHashMap iteration in insertion-order gives us eldest entry
            K eldestKey = store.keySet().iterator().next();
            store.remove(eldestKey);
            evictions.incrementAndGet();
        }
    }
}
