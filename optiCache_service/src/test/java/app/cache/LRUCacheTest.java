package app.cache;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LRUCacheTest {
    @Test
    void shouldPutGetAndOverwrite() {
        LRUCache<String, String> cache = new LRUCache<>(2, 60);

        cache.put("one", "value1");
        assertEquals("value1", cache.get("one"));

        cache.put("one", "value2");
        assertEquals("value2", cache.get("one"));

        CacheStats stats = cache.getStats();
        assertEquals(2, stats.getHits());
        assertEquals(0, stats.getMisses());
    }

    @Test
    void shouldReturnNullForMissingKeyAndCountMisses() {
        LRUCache<String, String> cache = new LRUCache<>(2, 60);

        assertNull(cache.get("missing"));
        assertEquals(0, cache.getStats().getHits());
        assertEquals(1, cache.getStats().getMisses());
    }

    @Test
    void shouldExpireEntryAfterTtlWritePolicy() throws InterruptedException {
        LRUCache<String, String> cache = new LRUCache<>(2, 1, LRUCache.ExpirationPolicy.WRITE);
        cache.put("expired", "old-value");

        Thread.sleep(1100);

        assertNull(cache.get("expired"));
        assertEquals(0, cache.getStats().getHits());
        assertEquals(1, cache.getStats().getMisses());
    }

    @Test
    void shouldResetExpirationOnAccessWhenUsingAccessPolicy() throws InterruptedException {
        LRUCache<String, String> cache = new LRUCache<>(2, 1, LRUCache.ExpirationPolicy.ACCESS);
        cache.put("keep", "value");

        Thread.sleep(500);
        assertEquals("value", cache.get("keep"));

        Thread.sleep(700);
        assertEquals("value", cache.get("keep"));

        assertEquals(2, cache.getStats().getHits());
        assertEquals(0, cache.getStats().getMisses());
    }

    @Test
    void shouldExpireWithoutAccessWhenUsingAccessPolicy() throws InterruptedException {
        LRUCache<String, String> cache = new LRUCache<>(2, 1, LRUCache.ExpirationPolicy.ACCESS);
        cache.put("expire", "value");

        Thread.sleep(1100);

        assertNull(cache.get("expire"));
        assertEquals(0, cache.getStats().getHits());
        assertEquals(1, cache.getStats().getMisses());
    }

    @Test
    void shouldEvictLeastRecentlyUsedEntry() {
        LRUCache<String, String> cache = new LRUCache<>(2, 300);
        cache.put("first", "1");
        cache.put("second", "2");
        assertEquals("1", cache.get("first"));

        cache.put("third", "3");

        assertNull(cache.get("second"));
        assertEquals("1", cache.get("first"));
        assertEquals("3", cache.get("third"));

        assertEquals(1, cache.getStats().getEvictions());
    }

    @Test
    void shouldInvalidateEntry() {
        LRUCache<String, String> cache = new LRUCache<>(2, 300);
        cache.put("key", "value");

        cache.invalidate("key");
        assertNull(cache.get("key"));
        assertEquals(0, cache.getStats().getHits());
        assertEquals(1, cache.getStats().getMisses());
    }

    @Test
    void shouldTrackHitMissMetricsAndHitRate() {
        LRUCache<String, String> cache = new LRUCache<>(2, 300);
        cache.put("one", "value1");
        assertEquals("value1", cache.get("one"));
        assertNull(cache.get("missing"));

        CacheStats stats = cache.getStats();
        assertEquals(1, stats.getHits());
        assertEquals(1, stats.getMisses());
        assertEquals(0.5, stats.getHitRate());
    }

    @Test
    void shouldReturnZeroHitRateWhenNoRequests() {
        LRUCache<String, String> cache = new LRUCache<>(2, 300);
        assertEquals(0.0, cache.getStats().getHitRate());
    }

    @Test
    void shouldIncrementEvictionCounterOnEviction() {
        LRUCache<String, String> cache = new LRUCache<>(1, 300);
        cache.put("first", "1");
        cache.put("second", "2");

        assertEquals(1, cache.getStats().getEvictions());
    }

    @Test
    void shouldHandleNullKeyAndIgnoreNullValue() {
        LRUCache<String, String> cache = new LRUCache<>(2, 300);

        assertDoesNotThrow(() -> cache.put(null, "value"));
        assertNull(cache.get(null));

        cache.put("key", null);
        assertNull(cache.get("key"));
        assertEquals(0, cache.getStats().getHits());
        assertEquals(1, cache.getStats().getMisses());
    }

    @Test
    void shouldThrowForInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new LRUCache<>(0, 300));
        assertThrows(IllegalArgumentException.class, () -> new LRUCache<>(2, 0));
        assertThrows(IllegalArgumentException.class, () -> new LRUCache<>(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> new LRUCache<>(2, -10));
    }

    @Test
    void shouldSupportConcurrentAccessDifferentKeys() throws InterruptedException, ExecutionException {
        LRUCache<String, String> cache = new LRUCache<>(100, 300);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            final String key = "key" + i;
            final String value = "value" + i;
            tasks.add(() -> {
                cache.put(key, value);
                assertEquals(value, cache.get(key));
                return null;
            });
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        for (Future<Void> future : futures) {
            future.get();
        }
    }
}
