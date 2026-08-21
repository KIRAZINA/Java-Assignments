package app.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Micrometer-based observability for the search engine (Task 4).
 *
 * <p>Exposes the four metrics required by the spec:
 * <ul>
 *   <li>{@code search.queries.total} - counter of all executed queries.</li>
 *   <li>{@code search.queries.duration} - timer recording per-query latency.</li>
 *   <li>{@code search.documents.indexed} - gauge of live document count.</li>
 *   <li>{@code search.cache.hit.rate} - gauge of parsed-query cache hit ratio.</li>
 * </ul>
 *
 * <p>The class is tolerant of a {@code null} registry (e.g. lightweight unit
 * tests that do not spin up a DI container); in that case all calls become
 * no-ops so the search path stays observable-free and fast.
 */
@Component
public class SearchMetrics {

    private final MeterRegistry registry;
    private final Counter queriesTotal;
    private final Timer queriesDuration;
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final AtomicBoolean gaugesRegistered = new AtomicBoolean(false);

    public SearchMetrics(MeterRegistry registry) {
        this.registry = registry;
        if (registry != null) {
            this.queriesTotal = registry.counter("search.queries.total");
            this.queriesDuration = Timer.builder("search.queries.duration")
                    .description("Latency of search queries")
                    .register(registry);
            this.cacheHits = registry.counter("search.cache.hits");
            this.cacheMisses = registry.counter("search.cache.misses");
        } else {
            this.queriesTotal = null;
            this.queriesDuration = null;
            this.cacheHits = null;
            this.cacheMisses = null;
        }
    }

    /** Record a completed query and its latency. */
    public void recordQuery(java.time.Duration duration) {
        if (registry == null) return;
        queriesTotal.increment();
        queriesDuration.record(duration);
    }

    public void markCacheHit() {
        if (cacheHits != null) cacheHits.increment();
    }

    public void markCacheMiss() {
        if (cacheMisses != null) cacheMisses.increment();
    }

    public double cacheHitRate() {
        if (cacheHits == null || cacheMisses == null) return 0.0;
        double h = cacheHits.count();
        double m = cacheMisses.count();
        return (h + m) == 0.0 ? 0.0 : h / (h + m);
    }

    /**
     * Register the live gauges. Safe to call multiple times - the gauges are
     * only registered once (Micrometer rejects duplicate meters with identical
     * identifiers).
     */
    public void registerGauges(InvertedIndex index, QueryCache cache) {
        if (registry == null || !gaugesRegistered.compareAndSet(false, true)) return;
        Gauge.builder("search.documents.indexed", index, i -> i.getDocumentCount())
                .description("Number of documents currently indexed")
                .register(registry);
        Gauge.builder("search.cache.hit.rate", this, SearchMetrics::cacheHitRate)
                .description("Parsed-query cache hit ratio")
                .register(registry);
        Gauge.builder("search.cache.size", cache, c -> c.size())
                .description("Number of cached parsed queries")
                .register(registry);
    }

    /** Package-private view of the query cache for gauge registration. */
    public interface QueryCache {
        long size();
    }
}
