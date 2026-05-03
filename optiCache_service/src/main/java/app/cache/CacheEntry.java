package app.cache;

public final class CacheEntry<V> {
    private final V value;
    private final long createdAtNanos;
    private volatile long lastAccessedAtNanos;

    public CacheEntry(V value, long nowNanos) {
        this.value = value;
        this.createdAtNanos = nowNanos;
        this.lastAccessedAtNanos = nowNanos;
    }

    public V getValue() {
        return value;
    }

    public long getCreatedAtNanos() {
        return createdAtNanos;
    }

    public long getLastAccessedAtNanos() {
        return lastAccessedAtNanos;
    }

    public void access(long nowNanos) {
        lastAccessedAtNanos = nowNanos;
    }
}
