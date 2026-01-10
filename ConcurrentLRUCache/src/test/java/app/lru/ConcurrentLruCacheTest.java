package app.lru;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentLruCacheTest {

    @Test
    void basicPutGetRemoveClear() {
        ConcurrentLruCache<Integer, String> cache = new ConcurrentLruCache<>(3);
        assertTrue(cache.isEmpty());

        assertNull(cache.put(1, "a"));
        assertEquals("a", cache.get(1));
        assertEquals(1, cache.size());

        assertNull(cache.get(2));
        CacheStats stats1 = cache.getStats();
        assertEquals(1, stats1.hits());
        assertEquals(1, stats1.misses());
        assertEquals(2, stats1.requests());

        assertEquals("a", cache.put(1, "b"));
        assertEquals("b", cache.get(1));

        assertEquals("b", cache.remove(1));
        assertNull(cache.get(1));
        assertEquals(0, cache.size());

        cache.put(2, "x");
        cache.put(3, "y");
        assertEquals(2, cache.size());
        cache.clear();
        assertTrue(cache.isEmpty());
    }

    @Test
    void lruOrderMaintained() {
        ConcurrentLruCache<Integer, String> cache = new ConcurrentLruCache<>(3);
        cache.put(1, "a"); // MRU: 1
        cache.put(2, "b"); // MRU: 2,1
        cache.put(3, "c"); // MRU: 3,2,1

        assertEquals("a", cache.get(1));
        List<Integer> order = new ArrayList<>(cache.keysSnapshot());
        assertEquals(List.of(1, 3, 2), order);
    }

    @Test
    void evictionOnOverflowAndCapacityDecrease() {
        ConcurrentLruCache<Integer, Integer> cache = new ConcurrentLruCache<>(2);
        cache.put(1, 1);
        cache.put(2, 2);
        cache.put(3, 3);
        assertNull(cache.get(1));
        assertEquals(2, cache.size());
        assertTrue(cache.evictions() >= 1);

        cache.setMaxCapacity(1);
        assertEquals(1, cache.maxCapacity());
        assertEquals(1, cache.size());
        assertEquals(Set.of(3), cache.keysSnapshot());
    }

    @Test
    void concurrentReadWriteScenario() throws Exception {
        int threads = 50;
        int opsPerThread = 2000;
        ConcurrentLruCache<Integer, Integer> cache = new ConcurrentLruCache<>(1000);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean failed = new AtomicBoolean(false);

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int id = t;
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    ThreadLocalRandom rnd = ThreadLocalRandom.current();
                    for (int i = 0; i < opsPerThread; i++) {
                        int k = rnd.nextInt(0, 5000);
                        switch (rnd.nextInt(3)) {
                            case 0 -> cache.put(k, id);
                            case 1 -> cache.get(k);
                            case 2 -> cache.remove(k);
                        }
                        if (cache.size() > cache.maxCapacity()) {
                            failed.set(true);
                            break;
                        }
                    }
                } catch (Throwable e) {
                    failed.set(true);
                }
            }));
        }

        start.countDown();
        for (Future<?> f : futures) f.get();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertFalse(failed.get(), "No exceptions and invariants must hold");
        assertTrue(cache.size() <= cache.maxCapacity());
    }

    @Test
    void serializationDeserializationRestoresContentAndOrder() throws Exception {
        ConcurrentLruCache<Integer, String> cache = new ConcurrentLruCache<>(5);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.put(3, "c");
        cache.get(1); // MRU: 1,3,2

        byte[] bytes;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(cache);
            bytes = bos.toByteArray();
        }

        ConcurrentLruCache<Integer, String> restored;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            restored = (ConcurrentLruCache<Integer, String>) ois.readObject();
        }

        assertEquals(3, restored.size());
        assertEquals("a", restored.get(1));
        List<Integer> order = new ArrayList<>(restored.keysSnapshot());
        assertEquals(List.of(1, 3, 2), order);
    }

    @Test
    void failFastIterator() {
        ConcurrentLruCache<Integer, String> cache = new ConcurrentLruCache<>(3);
        cache.put(1, "a");
        cache.put(2, "b");

        Iterator<Map.Entry<Integer, String>> it = cache.entryIterator();
        assertTrue(it.hasNext());
        cache.put(3, "c"); // modify cache => iterator must throw

        assertThrows(ConcurrentModificationException.class, it::hasNext);
    }

    @Test
    void containsKeyAndStatsCorrelate() {
        ConcurrentLruCache<Integer, String> cache = new ConcurrentLruCache<>(3);

        assertFalse(cache.containsKey(1));
        assertNull(cache.get(1)); // miss
        CacheStats s1 = cache.getStats();
        assertEquals(0, s1.hits());
        assertEquals(1, s1.misses());
        assertEquals(1, s1.requests());

        cache.put(1, "a");
        assertTrue(cache.containsKey(1));
        assertEquals("a", cache.get(1));   // hit
        CacheStats s2 = cache.getStats();
        assertEquals(1, s2.hits());
        assertEquals(1, s2.misses());
        assertEquals(2, s2.requests());
    }

    @Test
    void statsResetOnDeserialization() throws Exception {
        ConcurrentLruCache<Integer, String> cache = new ConcurrentLruCache<>(2);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.get(1); // hit
        cache.get(3); // miss

        byte[] bytes;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(cache);
            bytes = bos.toByteArray();
        }

        ConcurrentLruCache<Integer, String> restored;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            restored = (ConcurrentLruCache<Integer, String>) ois.readObject();
        }

        assertEquals(2, restored.size());
        List<Integer> order = new ArrayList<>(restored.keysSnapshot());
        assertEquals(Set.of(1, 2), new LinkedHashSet<>(order));

        CacheStats stats = restored.getStats();
        assertEquals(0, stats.hits());
        assertEquals(0, stats.misses());
        assertEquals(0, stats.evictions());
        assertEquals(0, stats.requests());
    }
}
