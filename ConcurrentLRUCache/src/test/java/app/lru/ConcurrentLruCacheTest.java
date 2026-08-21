package app.lru;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ConcurrentModificationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Timeout;
import java.util.concurrent.ThreadLocalRandom;

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
        assertEquals(1, stats1.getHits());
        assertEquals(1, stats1.getMisses());
        assertEquals(2, stats1.getRequests());

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
    void nullKeyRejectedNullValueAllowed() {
        ConcurrentLruCache<String, String> cache = new ConcurrentLruCache<>(2);
        assertThrows(NullPointerException.class, () -> cache.put(null, "v"));
        assertThrows(NullPointerException.class, () -> cache.get(null));
        assertThrows(NullPointerException.class, () -> cache.remove(null));

        assertNull(cache.put("k", null));
        assertNull(cache.get("k"));
        assertTrue(cache.containsKey("k"));
    }

    @Test
    void lruOrderMaintained() {
        ConcurrentLruCache<Integer, String> cache = new ConcurrentLruCache<>(3);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.put(3, "c");

        cache.get(1);
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

    /**
     * Task 5.1: Serialize after A,B,C with A promoted to MRU; after deserialize,
     * inserting a 4th entry must evict B (LRU) before C or A.
     */
    @Test
    void serializationOrderPreservesLruForEviction() throws Exception {
        ConcurrentLruCache<String, String> cache = new ConcurrentLruCache<>(3);
        cache.put("A", "a");
        cache.put("B", "b");
        cache.put("C", "c");
        cache.get("A"); // MRU order: A, C, B

        byte[] bytes = serialize(cache);

        ConcurrentLruCache<String, String> restored = deserialize(bytes);
        assertEquals(List.of("A", "C", "B"), new ArrayList<>(restored.keysSnapshot()));

        restored.put("D", "d");
        assertFalse(restored.containsKey("B"), "B was LRU and must be evicted first");
        assertTrue(restored.containsKey("A"));
        assertTrue(restored.containsKey("C"));
        assertTrue(restored.containsKey("D"));
    }

    /**
     * Task 5.2: After forced eviction, detached nodes must have null prev/next
     * so they do not pin the rest of the list in memory.
     */
    @Test
    void evictedNodesHaveNullLinksForGc() throws Exception {
        ConcurrentLruCache<Integer, String> cache = new ConcurrentLruCache<>(2);
        cache.put(1, "one");
        cache.put(2, "two");

        Node<Integer, String> evictedNode = extractNode(cache, 1);
        assertNotNull(evictedNode);

        cache.put(3, "three"); // evicts key 1 (LRU)

        Field prevField = Node.class.getDeclaredField("prev");
        Field nextField = Node.class.getDeclaredField("next");
        prevField.setAccessible(true);
        nextField.setAccessible(true);

        assertNull(prevField.get(evictedNode),
                "prev must be null after unlink so the node cannot pin neighbors");
        assertNull(nextField.get(evictedNode),
                "next must be null after unlink so the node cannot pin neighbors");
        assertFalse(cache.containsKey(1));
    }

    /**
     * Task 5.3: Shrinking capacity evicts LRU entries immediately.
     */
    @Test
    void dynamicCapacityShrinkingEvictsLru() {
        ConcurrentLruCache<Integer, String> cache = new ConcurrentLruCache<>(10);
        for (int i = 0; i < 10; i++) {
            cache.put(i, "v" + i);
        }
        assertEquals(10, cache.size());
        assertEquals(List.of(9, 8, 7, 6, 5, 4, 3, 2, 1, 0), new ArrayList<>(cache.keysSnapshot()));

        cache.setMaxCapacity(5);
        assertEquals(5, cache.size());
        assertEquals(List.of(9, 8, 7, 6, 5), new ArrayList<>(cache.keysSnapshot()));

        for (int i = 0; i < 5; i++) {
            assertFalse(cache.containsKey(i), "LRU key " + i + " should have been evicted");
        }
        for (int i = 5; i < 10; i++) {
            assertTrue(cache.containsKey(i), "MRU key " + i + " should remain");
        }
    }

    @Test
    void failFastIteratorSameThread() {
        ConcurrentLruCache<Integer, String> cache = new ConcurrentLruCache<>(3);
        cache.put(1, "a");
        cache.put(2, "b");

        Iterator<Map.Entry<Integer, String>> it = cache.entryIterator();
        assertTrue(it.hasNext());
        cache.put(3, "c");

        assertThrows(ConcurrentModificationException.class, it::hasNext);
        assertThrows(ConcurrentModificationException.class, it::next);
    }

    /**
     * Task 5.4: Structural modification on another thread after iterator creation
     * must trigger ConcurrentModificationException on next().
     */
    @Test
    @Timeout(10)
    void failFastIteratorCrossThread() throws Exception {
        ConcurrentLruCache<Integer, String> cache = new ConcurrentLruCache<>(5);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.put(3, "c");

        CountDownLatch iteratorReady = new CountDownLatch(1);
        CountDownLatch modifyDone = new CountDownLatch(1);
        AtomicReference<Throwable> iteratorError = new AtomicReference<>();

        Thread reader = new Thread(() -> {
            try {
                Iterator<Map.Entry<Integer, String>> it = cache.entryIterator();
                iteratorReady.countDown();
                modifyDone.await(5, TimeUnit.SECONDS);
                try {
                    it.next();
                    iteratorError.set(new AssertionError("expected ConcurrentModificationException"));
                } catch (ConcurrentModificationException expected) {
                    // success
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                iteratorError.set(e);
            }
        });

        Thread writer = new Thread(() -> {
            try {
                iteratorReady.await(5, TimeUnit.SECONDS);
                cache.put(4, "d");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                modifyDone.countDown();
            }
        });

        reader.start();
        writer.start();
        reader.join(5000);
        writer.join(5000);

        assertNull(iteratorError.get(), "Iterator thread should detect concurrent modification");
    }

    /**
     * Task 5.5: Under heavy contention, requests == hits + misses.
     */
    @Test
    void highContentionStatsInvariant() throws Exception {
        int threads = 10;
        int opsPerThread = 10_000;
        ConcurrentLruCache<Integer, Integer> cache = new ConcurrentLruCache<>(500);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int id = t;
            futures.add(pool.submit(() -> {
                start.await();
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                for (int i = 0; i < opsPerThread; i++) {
                    int k = rnd.nextInt(0, 2000);
                    if (rnd.nextBoolean()) {
                        cache.put(k, id);
                    } else {
                        cache.get(k);
                    }
                }
                return null;
            }));
        }

        start.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        CacheStats stats = cache.getStats();
        assertEquals(stats.getRequests(), stats.getHits() + stats.getMisses(),
                "Every recorded request must be classified as exactly one hit or miss");
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
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
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
        cache.get(1);

        ConcurrentLruCache<Integer, String> restored = deserialize(serialize(cache));

        assertEquals(3, restored.size());
        assertEquals(5, restored.maxCapacity());
        assertEquals("a", restored.get(1));
        assertEquals(List.of(1, 3, 2), new ArrayList<>(restored.keysSnapshot()));
    }

    @Test
    void statsResetOnDeserialization() throws Exception {
        ConcurrentLruCache<Integer, String> cache = new ConcurrentLruCache<>(2);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.get(1);
        cache.get(99);

        ConcurrentLruCache<Integer, String> restored = deserialize(serialize(cache));

        assertEquals(2, restored.size());
        CacheStats stats = restored.getStats();
        assertEquals(0, stats.getHits());
        assertEquals(0, stats.getMisses());
        assertEquals(0, stats.getEvictions());
        assertEquals(0, stats.getRequests());
    }

    @Test
    void containsKeyAndStatsCorrelate() {
        ConcurrentLruCache<Integer, String> cache = new ConcurrentLruCache<>(3);

        assertFalse(cache.containsKey(1));
        assertNull(cache.get(1));

        CacheStats s1 = cache.getStats();
        assertEquals(0, s1.getHits());
        assertEquals(1, s1.getMisses());
        assertEquals(1, s1.getRequests());

        cache.put(1, "a");
        assertTrue(cache.containsKey(1));
        assertEquals("a", cache.get(1));

        CacheStats s2 = cache.getStats();
        assertEquals(1, s2.getHits());
        assertEquals(1, s2.getMisses());
        assertEquals(2, s2.getRequests());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static <K, V> Node<K, V> extractNode(ConcurrentLruCache<K, V> cache, K key) throws Exception {
        Field mapField = ConcurrentLruCache.class.getDeclaredField("map");
        mapField.setAccessible(true);
        ConcurrentHashMap<K, Node<K, V>> map =
                (ConcurrentHashMap<K, Node<K, V>>) mapField.get(cache);
        return map.get(key);
    }

    private static byte[] serialize(Object obj) throws Exception {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            return bos.toByteArray();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserialize(byte[] bytes) throws Exception {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (T) ois.readObject();
        }
    }
}
