package app.lru;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free cache statistics backed by {@link LongAdder} instances.
 * <p>
 * Under high contention, {@code LongAdder} stripes increments across internal cells
 * so threads rarely synchronize on a single atomic word — unlike {@code AtomicLong},
 * which forces every increment through one hot CAS loop.
 * <p>
 * Invariant maintained by callers: {@code requests == hits + misses} (every recorded
 * request is classified as exactly one hit or one miss).
 */
public final class CacheStats implements Serializable {

    private static final long serialVersionUID = 1L;

    private transient LongAdder hits;
    private transient LongAdder misses;
    private transient LongAdder evictions;
    private transient LongAdder requests;

    public CacheStats() {
        initAdders();
    }

    private void initAdders() {
        hits = new LongAdder();
        misses = new LongAdder();
        evictions = new LongAdder();
        requests = new LongAdder();
    }

    /** Records a cache lookup (must be followed by {@link #recordHit()} or {@link #recordMiss()}). */
    public void recordRequest() {
        requests.increment();
    }

    public void recordHit() {
        hits.increment();
    }

    public void recordMiss() {
        misses.increment();
    }

    public void recordEviction() {
        evictions.increment();
    }

    public long getHits() {
        return hits.sum();
    }

    public long getMisses() {
        return misses.sum();
    }

    public long getEvictions() {
        return evictions.sum();
    }

    public long getRequests() {
        return requests.sum();
    }

    /** Alias for {@link #getHits()}. */
    public long hits() {
        return getHits();
    }

    /** Alias for {@link #getMisses()}. */
    public long misses() {
        return getMisses();
    }

    /** Alias for {@link #getEvictions()}. */
    public long evictions() {
        return getEvictions();
    }

    /** Alias for {@link #getRequests()}. */
    public long requests() {
        return getRequests();
    }

    /** Returns an immutable point-in-time snapshot of the current counters. */
    public CacheStats snapshot() {
        return new CacheStats(getHits(), getMisses(), getEvictions(), getRequests());
    }

    /** Immutable snapshot constructor (used by {@link #snapshot()} and {@link ConcurrentLruCache#getStats()}). */
    private CacheStats(long hits, long misses, long evictions, long requests) {
        initAdders();
        this.hits.add(hits);
        this.misses.add(misses);
        this.evictions.add(evictions);
        this.requests.add(requests);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initAdders();
    }

    @Override
    public String toString() {
        return "CacheStats{hits=" + getHits() + ", misses=" + getMisses()
                + ", evictions=" + getEvictions() + ", requests=" + getRequests() + "}";
    }
}
