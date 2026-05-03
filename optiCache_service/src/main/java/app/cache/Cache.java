package app.cache;

public interface Cache<K, V> {
    V get(K key);
    void put(K key, V value);
    void invalidate(K key);
    CacheStats getStats();
    Object lockForKey(K key);
}
