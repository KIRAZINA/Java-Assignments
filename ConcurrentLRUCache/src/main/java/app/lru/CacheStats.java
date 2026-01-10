package app.lru;

import java.io.Serializable;

public final class CacheStats implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long hits;
    private final long misses;
    private final long evictions;
    private final long requests;

    public CacheStats(long hits, long misses, long evictions, long requests) {
        this.hits = hits;
        this.misses = misses;
        this.evictions = evictions;
        this.requests = requests;
    }

    public long hits() { return hits; }
    public long misses() { return misses; }
    public long evictions() { return evictions; }
    public long requests() { return requests; }

    @Override
    public String toString() {
        return "CacheStats{hits=" + hits + ", misses=" + misses +
                ", evictions=" + evictions + ", requests=" + requests + "}";
    }
}
