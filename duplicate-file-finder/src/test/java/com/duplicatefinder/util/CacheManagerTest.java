package com.duplicatefinder.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CacheManager Tests")
class CacheManagerTest {

    @TempDir
    Path tempDir;

    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = new CacheManager();
    }

    @Test
    @DisplayName("Should return null for non-existent cache entry")
    void testGetNonExistentEntry() throws IOException {
        // Given
        Path testFile = tempDir.resolve("nonexistent.txt");
        Instant lastModified = Instant.now();

        // When
        Long cachedChecksum = cacheManager.getCachedChecksum(testFile, lastModified);

        // Then
        assertThat(cachedChecksum).isNull();
    }

    @Test
    @DisplayName("Should store and retrieve cache entry")
    void testStoreAndRetrieveEntry() throws IOException {
        // Given
        Path testFile = tempDir.resolve("test.txt");
        Instant lastModified = Instant.now();
        long checksum = 12345L;

        // When
        cacheManager.putChecksum(testFile, checksum, lastModified);
        Long retrievedChecksum = cacheManager.getCachedChecksum(testFile, lastModified);

        // Then
        assertThat(retrievedChecksum).isEqualTo(checksum);
    }

    @Test
    @DisplayName("Should return null for outdated cache entry")
    void testOutdatedCacheEntry() throws IOException {
        // Given
        Path testFile = tempDir.resolve("test.txt");
        Instant oldModified = Instant.now().minusSeconds(3600); // 1 hour ago
        Instant newModified = Instant.now(); // now
        long checksum = 12345L;

        cacheManager.putChecksum(testFile, checksum, oldModified);

        // When
        Long retrievedChecksum = cacheManager.getCachedChecksum(testFile, newModified);

        // Then
        assertThat(retrievedChecksum).isNull();
    }

    @Test
    @DisplayName("Should remove outdated entry from cache")
    void testRemoveOutdatedEntry() throws IOException {
        // Given
        Path testFile = tempDir.resolve("test.txt");
        Instant oldModified = Instant.now().minusSeconds(3600);
        Instant newModified = Instant.now();
        long checksum = 12345L;

        cacheManager.putChecksum(testFile, checksum, oldModified);
        assertThat(cacheManager.getCacheSize()).isEqualTo(1);

        // When
        cacheManager.getCachedChecksum(testFile, newModified);

        // Then
        assertThat(cacheManager.getCacheSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle multiple cache entries")
    void testMultipleCacheEntries() throws IOException {
        // Given
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Path file3 = tempDir.resolve("file3.txt");
        
        Instant now = Instant.now();
        long checksum1 = 1111L;
        long checksum2 = 2222L;
        long checksum3 = 3333L;

        // When
        cacheManager.putChecksum(file1, checksum1, now);
        cacheManager.putChecksum(file2, checksum2, now);
        cacheManager.putChecksum(file3, checksum3, now);

        // Then
        assertThat(cacheManager.getCacheSize()).isEqualTo(3);
        assertThat(cacheManager.getCachedChecksum(file1, now)).isEqualTo(checksum1);
        assertThat(cacheManager.getCachedChecksum(file2, now)).isEqualTo(checksum2);
        assertThat(cacheManager.getCachedChecksum(file3, now)).isEqualTo(checksum3);
    }

    @Test
    @DisplayName("Should clear cache correctly")
    void testClearCache() throws IOException {
        // Given
        Path testFile = tempDir.resolve("test.txt");
        cacheManager.putChecksum(testFile, 12345L, Instant.now());
        assertThat(cacheManager.getCacheSize()).isGreaterThan(0);

        // When
        cacheManager.clearCache();

        // Then
        assertThat(cacheManager.getCacheSize()).isEqualTo(0);
        assertThat(cacheManager.getCachedChecksum(testFile, Instant.now())).isNull();
    }

    @Test
    @DisplayName("Should update existing cache entry")
    void testUpdateExistingEntry() throws IOException {
        // Given
        Path testFile = tempDir.resolve("test.txt");
        Instant modified = Instant.now();
        long oldChecksum = 1111L;
        long newChecksum = 2222L;

        cacheManager.putChecksum(testFile, oldChecksum, modified);
        assertThat(cacheManager.getCachedChecksum(testFile, modified)).isEqualTo(oldChecksum);

        // When
        cacheManager.putChecksum(testFile, newChecksum, modified);

        // Then
        assertThat(cacheManager.getCachedChecksum(testFile, modified)).isEqualTo(newChecksum);
        assertThat(cacheManager.getCacheSize()).isEqualTo(1); // Still only one entry
    }

    @Test
    @DisplayName("Should handle files with same content but different paths")
    void testDifferentPathsSameContent() throws IOException {
        // Given
        Path file1 = tempDir.resolve("subdir1").resolve("test.txt");
        Path file2 = tempDir.resolve("subdir2").resolve("test.txt");
        
        Files.createDirectories(file1.getParent());
        Files.createDirectories(file2.getParent());
        
        Instant now = Instant.now();
        long checksum = 12345L;

        // When
        cacheManager.putChecksum(file1, checksum, now);
        cacheManager.putChecksum(file2, checksum, now);

        // Then
        assertThat(cacheManager.getCacheSize()).isEqualTo(2);
        assertThat(cacheManager.getCachedChecksum(file1, now)).isEqualTo(checksum);
        assertThat(cacheManager.getCachedChecksum(file2, now)).isEqualTo(checksum);
    }

    @Test
    @DisplayName("Should handle files with special characters in paths")
    void testSpecialCharactersInPaths() throws IOException {
        // Given
        Path specialFile = tempDir.resolve("file with spaces & symbols!@#$%^&().txt");
        Instant now = Instant.now();
        long checksum = 12345L;

        // When
        cacheManager.putChecksum(specialFile, checksum, now);
        Long retrievedChecksum = cacheManager.getCachedChecksum(specialFile, now);

        // Then
        assertThat(retrievedChecksum).isEqualTo(checksum);
    }

    @Test
    @DisplayName("Should handle large number of cache entries")
    void testLargeNumberOfEntries() throws IOException {
        // Given
        int entryCount = 1000;
        Instant now = Instant.now();

        // When
        for (int i = 0; i < entryCount; i++) {
            Path file = tempDir.resolve("file" + i + ".txt");
            cacheManager.putChecksum(file, (long) i, now);
        }

        // Then
        assertThat(cacheManager.getCacheSize()).isEqualTo(entryCount);
        
        // Verify some random entries
        assertThat(cacheManager.getCachedChecksum(tempDir.resolve("file100.txt"), now)).isEqualTo(100L);
        assertThat(cacheManager.getCachedChecksum(tempDir.resolve("file500.txt"), now)).isEqualTo(500L);
        assertThat(cacheManager.getCachedChecksum(tempDir.resolve("file999.txt"), now)).isEqualTo(999L);
    }

    @Test
    @DisplayName("Should handle concurrent access to cache")
    void testConcurrentAccess() throws IOException {
        // Given
        int threadCount = 10;
        int operationsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        Path file = tempDir.resolve("thread" + threadId + "_file" + j + ".txt");
                        Instant now = Instant.now();
                        long checksum = threadId * 1000L + j;
                        
                        cacheManager.putChecksum(file, checksum, now);
                        Long retrieved = cacheManager.getCachedChecksum(file, now);
                        assertThat(retrieved).isEqualTo(checksum);
                    }
                } catch (Exception e) {
                    fail("Thread " + threadId + " failed: " + e.getMessage());
                }
            });
        }

        // When
        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Thread interrupted: " + e.getMessage());
            }
        }

        // Then
        assertThat(cacheManager.getCacheSize()).isEqualTo(threadCount * operationsPerThread);
    }

    @Test
    @DisplayName("Should handle zero checksum values")
    void testZeroChecksum() throws IOException {
        // Given
        Path testFile = tempDir.resolve("zero.txt");
        Instant now = Instant.now();
        long checksum = 0L;

        // When
        cacheManager.putChecksum(testFile, checksum, now);
        Long retrievedChecksum = cacheManager.getCachedChecksum(testFile, now);

        // Then
        assertThat(retrievedChecksum).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should handle negative checksum values")
    void testNegativeChecksum() throws IOException {
        // Given
        Path testFile = tempDir.resolve("negative.txt");
        Instant now = Instant.now();
        long checksum = -12345L;

        // When
        cacheManager.putChecksum(testFile, checksum, now);
        Long retrievedChecksum = cacheManager.getCachedChecksum(testFile, now);

        // Then
        assertThat(retrievedChecksum).isEqualTo(-12345L);
    }

    @Test
    @DisplayName("Should handle maximum checksum values")
    void testMaxChecksum() throws IOException {
        // Given
        Path testFile = tempDir.resolve("max.txt");
        Instant now = Instant.now();
        long checksum = Long.MAX_VALUE;

        // When
        cacheManager.putChecksum(testFile, checksum, now);
        Long retrievedChecksum = cacheManager.getCachedChecksum(testFile, now);

        // Then
        assertThat(retrievedChecksum).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("Should handle minimum checksum values")
    void testMinChecksum() throws IOException {
        // Given
        Path testFile = tempDir.resolve("min.txt");
        Instant now = Instant.now();
        long checksum = Long.MIN_VALUE;

        // When
        cacheManager.putChecksum(testFile, checksum, now);
        Long retrievedChecksum = cacheManager.getCachedChecksum(testFile, now);

        // Then
        assertThat(retrievedChecksum).isEqualTo(Long.MIN_VALUE);
    }
}
