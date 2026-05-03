package app.cache;

public final class CacheStats {
    private final long hits;
    private final long misses;
    private final long evictions;
    private final long requests;

    public CacheStats(long hits, long misses, long evictions) {
        this.hits = hits;
        this.misses = misses;
        this.evictions = evictions;
        this.requests = hits + misses;
    }

    public long getHits() {
        return hits;
    }

    public long getMisses() {
        return misses;
    }

    public long getEvictions() {
        return evictions;
    }

    public double getHitRate() {
        return requests == 0 ? 0.0 : ((double) hits) / requests;
    }
}
