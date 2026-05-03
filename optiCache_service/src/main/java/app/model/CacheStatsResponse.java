package app.model;

public final class CacheStatsResponse {
    private final long hits;
    private final long misses;
    private final double hitRate;
    private final long evictions;

    public CacheStatsResponse(long hits, long misses, double hitRate, long evictions) {
        this.hits = hits;
        this.misses = misses;
        this.hitRate = hitRate;
        this.evictions = evictions;
    }

    public long getHits() {
        return hits;
    }

    public long getMisses() {
        return misses;
    }

    public double getHitRate() {
        return hitRate;
    }

    public long getEvictions() {
        return evictions;
    }
}
