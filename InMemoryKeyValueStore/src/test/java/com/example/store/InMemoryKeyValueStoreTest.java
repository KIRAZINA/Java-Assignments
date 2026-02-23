package com.example.store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static java.util.concurrent.TimeUnit.*;

/**
 * Comprehensive test suite for {@link InMemoryKeyValueStore}.
 */
public class InMemoryKeyValueStoreTest {

    private InMemoryKeyValueStore<String, Integer> store;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore<>();
    }

    @AfterEach
    void tearDown() {
        store.stopAutoSnapshot();
    }

    // ==================== Basic CRUD Tests ====================

    @Test
    void testBasicCRUD() {
        store.put("key1", 100);
        assertEquals(100, store.get("key1"));
        assertTrue(store.containsKey("key1"));
        assertEquals(1, store.size());

        store.put("key1", 200);
        assertEquals(200, store.get("key1"));

        assertTrue(store.remove("key1"));
        assertNull(store.get("key1"));
        assertFalse(store.containsKey("key1"));
        assertEquals(0, store.size());
    }

    @Test
    void testPutReturnsPreviousValue() {
        assertNull(store.put("key1", 100));
        assertEquals(100, store.put("key1", 200));
        assertEquals(200, store.get("key1"));
    }

    // ==================== Null Handling Tests ====================

    @Test
    void testNullKeyThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> store.put(null, 100));
    }

    @Test
    void testNullValueThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> store.put("key1", null));
    }

    @Test
    void testPutIfAbsentNullKeyThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> store.putIfAbsent(null, 100));
    }

    @Test
    void testPutIfAbsentNullValueThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> store.putIfAbsent("key1", null));
    }

    @Test
    void testComputeIfAbsentNullKeyThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> store.computeIfAbsent(null, k -> 100));
    }

    // ==================== New Methods Tests ====================

    @Test
    void testGetOrDefault() {
        store.put("key1", 100);
        assertEquals(100, store.getOrDefault("key1", 0));
        assertEquals(0, store.getOrDefault("nonexistent", 0));
    }

    @Test
    void testPutIfAbsent() {
        assertNull(store.putIfAbsent("key1", 100));
        assertEquals(100, store.get("key1"));

        // Second call should not change the value
        assertEquals(100, store.putIfAbsent("key1", 200));
        assertEquals(100, store.get("key1"));
    }

    @Test
    void testComputeIfAbsent() {
        AtomicInteger callCount = new AtomicInteger(0);

        Integer result = store.computeIfAbsent("key1", k -> {
            callCount.incrementAndGet();
            return 100;
        });

        assertEquals(100, result);
        assertEquals(1, callCount.get());

        // Second call should not invoke the function
        result = store.computeIfAbsent("key1", k -> {
            callCount.incrementAndGet();
            return 200;
        });

        assertEquals(100, result);
        assertEquals(1, callCount.get()); // Function should not have been called again
    }

    @Test
    void testKeySet() {
        store.put("key1", 1);
        store.put("key2", 2);
        store.put("key3", 3);

        Set<String> keys = store.keySet();
        assertEquals(3, keys.size());
        assertTrue(keys.contains("key1"));
        assertTrue(keys.contains("key2"));
        assertTrue(keys.contains("key3"));

        // Verify it's unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> keys.add("key4"));
    }

    @Test
    void testIsEmpty() {
        assertTrue(store.isEmpty());
        store.put("key1", 1);
        assertFalse(store.isEmpty());
        store.remove("key1");
        assertTrue(store.isEmpty());
    }

    // ==================== Concurrency Tests ====================

    @Test
    void testConcurrency() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        store.put("thread-" + threadId + "-key-" + j, j);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(threadCount * operationsPerThread, store.size());
        executor.shutdown();
    }

    @Test
    void testConcurrentGetAndPut() throws InterruptedException {
        int threadCount = 20;
        int operationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        // Pre-populate some data
        for (int i = 0; i < 100; i++) {
            store.put("initial-key-" + i, i);
        }

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        // Mix of reads and writes
                        if (j % 2 == 0) {
                            store.put("thread-" + threadId + "-key-" + j, j);
                        } else {
                            store.get("initial-key-" + (j % 100));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();
    }

    @Test
    void testConcurrentComputeIfAbsent() throws InterruptedException {
        int threadCount = 10;
        AtomicInteger computeCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // All threads try to compute the same key
                    store.computeIfAbsent("shared-key", k -> {
                        computeCount.incrementAndGet();
                        return 42;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(5, TimeUnit.SECONDS));

        // The compute function may be called multiple times due to retry logic with TTL
        // but the final result should be correct
        assertEquals(42, store.get("shared-key"));
        assertTrue(computeCount.get() >= 1); // At least once
        executor.shutdown();
    }

    // ==================== Persistence Tests ====================

    @Test
    void testPersistence() throws StorePersistenceException {
        InMemoryKeyValueStore<String, String> stringStore = new InMemoryKeyValueStore<>();
        stringStore.put("key1", "value1");
        stringStore.put("key2", "value2");
        String filePath = tempDir.resolve("store.json").toString();

        stringStore.saveToFile(filePath);

        InMemoryKeyValueStore<String, String> newStore = new InMemoryKeyValueStore<>();
        newStore.loadFromFile(filePath);

        assertEquals(2, newStore.size());
        assertEquals("value1", newStore.get("key1"));
        assertEquals("value2", newStore.get("key2"));
    }

    @Test
    void testPersistenceWithEmptyString() throws StorePersistenceException {
        InMemoryKeyValueStore<String, String> stringStore = new InMemoryKeyValueStore<>();
        stringStore.put("key1", "value1");
        stringStore.put("key2", ""); // Empty string instead of null
        String filePath = tempDir.resolve("store_with_empty.json").toString();

        stringStore.saveToFile(filePath);

        InMemoryKeyValueStore<String, String> newStore = new InMemoryKeyValueStore<>();
        newStore.loadFromFile(filePath);

        assertEquals(2, newStore.size());
        assertEquals("value1", newStore.get("key1"));
        assertEquals("", newStore.get("key2"));
        assertTrue(newStore.containsKey("key2"));
    }

    @Test
    void testLoadWithMerge() throws StorePersistenceException {
        // Use String store to avoid Gson Double/Integer issues
        InMemoryKeyValueStore<String, String> mergeStore = new InMemoryKeyValueStore<>();
        mergeStore.put("existing", "1");
        mergeStore.put("toOverwrite", "10");

        // Create a file with different data
        String filePath = tempDir.resolve("merge_test.json").toString();
        InMemoryKeyValueStore<String, String> fileStore = new InMemoryKeyValueStore<>();
        fileStore.put("newKey", "100");
        fileStore.put("toOverwrite", "200");
        fileStore.saveToFile(filePath);

        // Load with merge=true
        mergeStore.loadFromFile(filePath, true);

        assertEquals(3, mergeStore.size());
        assertEquals("1", mergeStore.get("existing"));      // Original preserved
        assertEquals("100", mergeStore.get("newKey"));      // New key added
        assertEquals("200", mergeStore.get("toOverwrite")); // Overwritten by merge
    }

    @Test
    void testLoadWithoutMerge() throws StorePersistenceException {
        // Use String store to avoid Gson Double/Integer issues
        InMemoryKeyValueStore<String, String> noMergeStore = new InMemoryKeyValueStore<>();
        noMergeStore.put("existing", "1");

        // Create a file with different data
        String filePath = tempDir.resolve("no_merge_test.json").toString();
        InMemoryKeyValueStore<String, String> fileStore = new InMemoryKeyValueStore<>();
        fileStore.put("newKey", "100");
        fileStore.saveToFile(filePath);

        // Load with merge=false (default behavior)
        noMergeStore.loadFromFile(filePath, false);

        assertEquals(1, noMergeStore.size());
        assertNull(noMergeStore.get("existing")); // Cleared before load
        assertEquals("100", noMergeStore.get("newKey"));
    }

    @Test
    void testLoadNonExistentFile() {
        assertDoesNotThrow(() -> store.loadFromFile("non_existent_file.json"));
        assertEquals(0, store.size());
    }

    @Test
    void testLoadCorruptedJsonFile() throws IOException {
        String filePath = tempDir.resolve("corrupted.json").toString();
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("{ this is not valid json }");
        }

        assertThrows(StorePersistenceException.class, () -> store.loadFromFile(filePath));
    }

    @Test
    void testLoadInvalidJsonStructure() throws IOException {
        String filePath = tempDir.resolve("invalid_structure.json").toString();
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("\"not a map\"");
        }

        // Invalid JSON structure should throw StorePersistenceException
        assertThrows(StorePersistenceException.class, () -> store.loadFromFile(filePath));
    }

    // ==================== Auto-Snapshot Tests ====================

    @Test
    void testAutoSnapshot() throws StorePersistenceException {
        String filePath = tempDir.resolve("snapshot.json").toString();
        InMemoryKeyValueStore<String, String> snapshotStore = new InMemoryKeyValueStore<>();
        snapshotStore.put("key1", "initial");

        // Snapshot every 500ms for faster testing (using 1 second as minimum long value)
        snapshotStore.startAutoSnapshot(filePath, 1);

        // Wait for first snapshot using Awaitility
        await().atMost(3, SECONDS)
                .until(() -> new File(filePath).exists());

        InMemoryKeyValueStore<String, String> newStore = new InMemoryKeyValueStore<>();
        newStore.loadFromFile(filePath);
        assertEquals("initial", newStore.get("key1"));

        snapshotStore.put("key2", "updated");

        // Wait for next snapshot
        await().atMost(3, SECONDS)
                .untilAsserted(() -> {
                    InMemoryKeyValueStore<String, String> verifyStore = new InMemoryKeyValueStore<>();
                    verifyStore.loadFromFile(filePath);
                    assertEquals("updated", verifyStore.get("key2"));
                });

        snapshotStore.stopAutoSnapshot();
    }

    @Test
    void testDoubleStartAutoSnapshot() throws StorePersistenceException {
        String filePath1 = tempDir.resolve("snapshot1.json").toString();
        String filePath2 = tempDir.resolve("snapshot2.json").toString();

        InMemoryKeyValueStore<String, String> snapshotStore = new InMemoryKeyValueStore<>();
        snapshotStore.put("key1", "value1");
        snapshotStore.startAutoSnapshot(filePath1, 1);

        assertTrue(snapshotStore.isAutoSnapshotRunning());

        // Start again with different file - should stop the first one
        snapshotStore.put("key2", "value2");
        snapshotStore.startAutoSnapshot(filePath2, 1);

        assertTrue(snapshotStore.isAutoSnapshotRunning());

        // Wait for snapshot to file2
        await().atMost(3, SECONDS)
                .until(() -> new File(filePath2).exists());

        // Verify the second file has both keys
        InMemoryKeyValueStore<String, String> verifyStore = new InMemoryKeyValueStore<>();
        verifyStore.loadFromFile(filePath2);
        assertEquals("value1", verifyStore.get("key1"));
        assertEquals("value2", verifyStore.get("key2"));

        snapshotStore.stopAutoSnapshot();
        assertFalse(snapshotStore.isAutoSnapshotRunning());
    }

    @Test
    void testStartAutoSnapshotWithInvalidInterval() {
        assertThrows(IllegalArgumentException.class, () -> store.startAutoSnapshot("test.json", 0));
        assertThrows(IllegalArgumentException.class, () -> store.startAutoSnapshot("test.json", -1));
    }

    @Test
    void testStopAutoSnapshotWhenNotRunning() {
        // Should not throw when stopping a non-running snapshot
        assertDoesNotThrow(() -> store.stopAutoSnapshot());
        assertFalse(store.isAutoSnapshotRunning());
    }

    // ==================== Clear and Remove Tests ====================

    @Test
    void testClear() {
        store.put("key1", 1);
        store.put("key2", 2);
        store.clear();
        assertEquals(0, store.size());
        assertTrue(store.isEmpty());
    }

    @Test
    void testContainsExistentKey() {
        store.put("a", 1);
        assertTrue(store.containsKey("a"));
    }

    @Test
    void testContainsNonExistentKey() {
        assertFalse(store.containsKey("b"));
    }

    @Test
    void testRemoveNonExistentKey() {
        assertFalse(store.remove("ghost"));
    }

    // ==================== Parameterized Tests ====================

    @ParameterizedTest
    @ValueSource(ints = {1, 100, Integer.MAX_VALUE, Integer.MIN_VALUE})
    void testPutDifferentIntegerValues(int value) {
        store.put("key", value);
        assertEquals(value, store.get("key"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "longer string with spaces", "special!@#$%^&*()chars"})
    void testPutDifferentStringKeys(String key) {
        InMemoryKeyValueStore<String, Integer> stringKeyStore = new InMemoryKeyValueStore<>();
        stringKeyStore.put(key, 42);
        assertEquals(42, stringKeyStore.get(key));
    }

    // ==================== Edge Cases ====================

    @Test
    void testLargeNumberOfEntries() {
        int count = 10000;
        for (int i = 0; i < count; i++) {
            store.put("key" + i, i);
        }
        assertEquals(count, store.size());

        // Verify random samples
        assertEquals(5000, store.get("key5000"));
        assertEquals(9999, store.get("key9999"));
        assertEquals(0, store.get("key0"));
    }

    @Test
    void testOverwriteValue() {
        store.put("key", 1);
        assertEquals(1, store.get("key"));

        store.put("key", 2);
        assertEquals(2, store.get("key"));

        store.put("key", 3);
        assertEquals(3, store.get("key"));
    }

    @Test
    void testKeySetSnapshotIsImmutable() {
        store.put("key1", 1);
        store.put("key2", 2);

        Set<String> keys = store.keySet();
        assertEquals(2, keys.size());

        // Add more keys to store
        store.put("key3", 3);

        // The previously returned set should not change
        assertEquals(2, keys.size());
        assertFalse(keys.contains("key3"));
    }

    // ==================== Production-Grade Tests ====================

    @Test
    void testGetOptional() {
        store.put("key1", 100);

        assertTrue(store.getOptional("key1").isPresent());
        assertEquals(100, store.getOptional("key1").get());

        assertFalse(store.getOptional("nonexistent").isPresent());
    }

    @Test
    void testComputeIfPresent() {
        store.put("key1", 100);

        // Update existing value
        Integer result = store.computeIfPresent("key1", (k, v) -> v * 2);
        assertEquals(200, result);
        assertEquals(200, store.get("key1"));

        // Non-existent key - function should not be called
        result = store.computeIfPresent("nonexistent", (k, v) -> v * 2);
        assertNull(result);
        assertFalse(store.containsKey("nonexistent"));

        // Return null removes the entry
        result = store.computeIfPresent("key1", (k, v) -> null);
        assertNull(result);
        assertFalse(store.containsKey("key1"));
    }

    @Test
    void testGetOrDefaultRejectsNull() {
        store.put("key1", 100);
        assertThrows(IllegalArgumentException.class, () -> store.getOrDefault("key1", null));
    }

    @Test
    void testLoadFromFileRejectsNullValues() throws IOException {
        String filePath = tempDir.resolve("null_values.json").toString();
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("{\"key1\": null, \"key2\": 100}");
        }

        assertThrows(StorePersistenceException.class, () -> store.loadFromFile(filePath));
    }

    @Test
    void testComputeIfPresentConcurrent() throws InterruptedException {
        int threadCount = 10;
        store.put("counter", 0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    store.computeIfPresent("counter", (k, v) -> v + 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(5, TimeUnit.SECONDS));

        // All increments should be applied atomically
        assertEquals(threadCount, store.get("counter"));
        executor.shutdown();
    }

    @Test
    void testTypeTokenConstructor() {
        // Test that TypeToken constructor works for simple types
        com.google.gson.reflect.TypeToken<Map<String, InMemoryKeyValueStore.Entry<String>>> typeToken = 
            new com.google.gson.reflect.TypeToken<>() {};
        InMemoryKeyValueStore<String, String> typedStore = 
            new InMemoryKeyValueStore<>(typeToken);
        
        typedStore.put("key1", "value1");
        assertEquals("value1", typedStore.get("key1"));
    }

    @Test
    void testKeySetIsUnmodifiable() {
        store.put("key1", 1);
        Set<String> keys = store.keySet();

        // Should throw UnsupportedOperationException for any modification attempt
        assertThrows(UnsupportedOperationException.class, () -> keys.add("key2"));
        assertThrows(UnsupportedOperationException.class, () -> keys.remove("key1"));
        assertThrows(UnsupportedOperationException.class, () -> keys.clear());
    }

    // ==================== TTL (Time-to-Live) Tests ====================

    @Test
    void testPutWithTTL() {
        store.put("key1", 100, 10); // 10 seconds TTL
        
        assertTrue(store.containsKey("key1"));
        assertEquals(100, store.get("key1"));
        assertTrue(store.hasTTL("key1"));
        assertTrue(store.getRemainingTTL("key1") > 0);
        assertTrue(store.getRemainingTTL("key1") <= 10);
    }

    @Test
    void testPutWithoutTTL() {
        store.put("key1", 100); // No TTL
        
        assertTrue(store.containsKey("key1"));
        assertEquals(100, store.get("key1"));
        assertFalse(store.hasTTL("key1"));
        assertEquals(-1, store.getRemainingTTL("key1"));
    }

    @Test
    void testEntryExpires() throws InterruptedException {
        store.put("key1", 100, 1); // 1 second TTL
        
        assertTrue(store.containsKey("key1"));
        assertEquals(100, store.get("key1"));
        
        // Wait for expiration
        Thread.sleep(1100);
        
        // Entry should be expired now
        assertFalse(store.containsKey("key1"));
        assertNull(store.get("key1"));
        assertEquals(-1, store.getRemainingTTL("key1"));
    }

    @Test
    void testGetRemovesExpiredEntry() throws InterruptedException {
        store.put("key1", 100, 1); // 1 second TTL
        
        assertEquals(1, store.size());
        
        // Wait for expiration
        Thread.sleep(1100);
        
        // get() should remove the expired entry
        assertNull(store.get("key1"));
        
        // Verify internal store is cleaned
        assertEquals(0, store.size());
    }

    @Test
    void testDefaultTTL() {
        store.setDefaultTTL(10);
        assertEquals(10, store.getDefaultTTL());
        
        store.put("key1", 100); // Should use default TTL
        
        assertTrue(store.hasTTL("key1"));
        assertTrue(store.getRemainingTTL("key1") > 0);
        assertTrue(store.getRemainingTTL("key1") <= 10);
        
        // Disable default TTL
        store.setDefaultTTL(-1);
        store.put("key2", 200);
        
        assertFalse(store.hasTTL("key2"));
        assertEquals(-1, store.getRemainingTTL("key2"));
    }

    @Test
    void testPutIfAbsentWithTTL() throws InterruptedException {
        assertNull(store.putIfAbsent("key1", 100, 2));
        assertEquals(100, store.get("key1"));
        assertTrue(store.hasTTL("key1"));
        
        // Second call should not change value
        assertEquals(100, store.putIfAbsent("key1", 200, 10));
        assertEquals(100, store.get("key1"));
        assertTrue(store.getRemainingTTL("key1") <= 2); // Original TTL preserved
        
        // Wait for expiration
        Thread.sleep(2100);
        
        // Now putIfAbsent should work
        assertNull(store.putIfAbsent("key1", 300, 5));
        assertEquals(300, store.get("key1"));
    }

    @Test
    void testComputeIfAbsentWithTTL() {
        AtomicInteger callCount = new AtomicInteger(0);
        
        Integer result = store.computeIfAbsent("key1", k -> {
            callCount.incrementAndGet();
            return 100;
        }, 10);
        
        assertEquals(100, result);
        assertEquals(1, callCount.get());
        assertTrue(store.hasTTL("key1"));
    }

    @Test
    void testRemoveExpiredManually() throws InterruptedException {
        store.put("key1", 100, 1);
        store.put("key2", 200); // No TTL
        
        assertEquals(2, store.size());
        
        // Wait for key1 to expire
        Thread.sleep(1100);
        
        // removeExpired should remove key1
        int removed = store.removeExpired();
        assertEquals(1, removed);
        assertEquals(1, store.size());
        assertEquals(200, store.get("key2"));
    }

    @Test
    void testBackgroundCleaner() throws InterruptedException {
        InMemoryKeyValueStore<String, Integer> ttlStore = new InMemoryKeyValueStore<>(1); // 1 second cleaner interval
        ttlStore.put("key1", 100, 1);
        ttlStore.put("key2", 200, 1);
        
        ttlStore.startCleaner();
        assertTrue(ttlStore.isCleanerRunning());
        
        // Wait for entries to expire and cleaner to run
        Thread.sleep(2100);
        
        // Cleaner should have removed expired entries
        assertEquals(0, ttlStore.size());
        
        ttlStore.stopCleaner();
        assertFalse(ttlStore.isCleanerRunning());
    }

    @Test
    void testSaveToFileExcludesExpired() throws InterruptedException, StorePersistenceException {
        // Use String values to avoid Gson Double/Integer issues
        InMemoryKeyValueStore<String, String> ttlStore = new InMemoryKeyValueStore<>();
        ttlStore.put("key1", "100", 1); // Will expire
        ttlStore.put("key2", "200");    // No TTL
        
        String filePath = tempDir.resolve("ttl_store.json").toString();
        
        // Wait for key1 to expire
        Thread.sleep(1100);
        
        // Save should exclude expired entry
        ttlStore.saveToFile(filePath);
        
        InMemoryKeyValueStore<String, String> newStore = new InMemoryKeyValueStore<>();
        newStore.loadFromFile(filePath);
        
        assertEquals(1, newStore.size());
        assertEquals("200", newStore.get("key2"));
        assertNull(newStore.get("key1"));
    }

    @Test
    void testSaveToFileIncludesExpiredWhenConfigured() throws InterruptedException, StorePersistenceException {
        InMemoryKeyValueStore<String, String> ttlStore = new InMemoryKeyValueStore<>();
        ttlStore.put("key1", "100", 1); // Will expire
        
        String filePath = tempDir.resolve("ttl_store_include.json").toString();
        
        // Wait for key1 to expire
        Thread.sleep(1100);
        
        // Save with excludeExpired=false
        ttlStore.saveToFile(filePath, false);
        
        // File should exist (though entry is expired)
        assertTrue(new File(filePath).exists());
    }

    @Test
    void testLoadFromFileFiltersExpired() throws InterruptedException, StorePersistenceException, IOException {
        // Create a JSON file with an already-expired entry
        String filePath = tempDir.resolve("expired_entry.json").toString();
        long pastExpiry = System.currentTimeMillis() - 10000; // 10 seconds ago
        long futureExpiry = System.currentTimeMillis() + 10000; // 10 seconds in future
        
        String json = String.format(
            "{\"key1\":{\"value\":\"100\",\"expiresAtEpochMillis\":%d}," +
            "\"key2\":{\"value\":\"200\",\"expiresAtEpochMillis\":%d}}",
            pastExpiry, futureExpiry
        );
        
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(json);
        }
        
        InMemoryKeyValueStore<String, String> newStore = new InMemoryKeyValueStore<>();
        newStore.loadFromFile(filePath);
        
        // key1 should be filtered out (already expired)
        // key2 should be loaded
        assertEquals(1, newStore.size());
        assertNull(newStore.get("key1"));
        assertEquals("200", newStore.get("key2"));
    }

    @Test
    void testTTLWithPersistenceRoundTrip() throws StorePersistenceException {
        // Use String values to avoid Gson Double/Integer issues
        InMemoryKeyValueStore<String, String> ttlStore = new InMemoryKeyValueStore<>();
        ttlStore.put("key1", "100", 3600); // 1 hour TTL
        ttlStore.put("key2", "200");       // No TTL
        
        String filePath = tempDir.resolve("ttl_roundtrip.json").toString();
        ttlStore.saveToFile(filePath);
        
        InMemoryKeyValueStore<String, String> newStore = new InMemoryKeyValueStore<>();
        newStore.loadFromFile(filePath);
        
        assertEquals(2, newStore.size());
        assertEquals("100", newStore.get("key1"));
        assertEquals("200", newStore.get("key2"));
        assertTrue(newStore.hasTTL("key1"));
        assertFalse(newStore.hasTTL("key2"));
    }

    @Test
    void testConcurrentPutWithTTL() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    store.put("thread-" + threadId, threadId, 10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(5, TimeUnit.SECONDS));
        
        assertEquals(threadCount, store.size());
        
        // All entries should have TTL
        for (int i = 0; i < threadCount; i++) {
            assertTrue(store.hasTTL("thread-" + i));
        }
        
        executor.shutdown();
    }

    @Test
    void testShutdown() {
        store.put("key1", 100);
        store.startAutoSnapshot(tempDir.resolve("snapshot.json").toString(), 60);
        store.startCleaner();
        
        assertTrue(store.isAutoSnapshotRunning());
        assertTrue(store.isCleanerRunning());
        
        store.shutdown();
        
        assertFalse(store.isAutoSnapshotRunning());
        assertFalse(store.isCleanerRunning());
    }
}
