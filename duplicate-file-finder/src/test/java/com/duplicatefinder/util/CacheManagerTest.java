package com.duplicatefinder.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CacheManager}.
 *
 * <p>All tests that persist state use the {@link CacheManager#CacheManager(Path)}
 * constructor with a {@code @TempDir}-scoped path, ensuring complete test isolation.
 * Existing tests that use the default constructor continue to work as before.</p>
 */
@DisplayName("CacheManager Tests")
class CacheManagerTest {

    @TempDir
    Path tempDir;

    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // Use a TempDir-isolated cache file so tests are hermetic.
        cacheManager = new CacheManager(tempDir.resolve("test-cache.properties"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Existing tests (preserved, adapted to use legacy Instant overload)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return null for non-existent cache entry")
    void testGetNonExistentEntry() throws IOException {
        Path testFile = tempDir.resolve("nonexistent.txt");
        assertThat(cacheManager.getCachedChecksum(testFile, Instant.now())).isNull();
    }

    @Test
    @DisplayName("Should store and retrieve a cache entry")
    void testStoreAndRetrieveEntry() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "hello");
        Instant now = Files.getLastModifiedTime(testFile).toInstant();
        long checksum = 12345L;

        cacheManager.putChecksum(testFile, checksum, now);

        assertThat(cacheManager.getCachedChecksum(testFile, now)).isEqualTo(checksum);
    }

    @Test
    @DisplayName("Should return null for an outdated cache entry (timestamp changed)")
    void testOutdatedCacheEntry() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "data");
        Instant oldTime = Instant.now().minusSeconds(3600);
        Instant newTime = Instant.now();
        long size = Files.size(testFile);

        cacheManager.putChecksum(testFile, 99L, size, oldTime.toEpochMilli());
        assertThat(cacheManager.getCachedChecksum(testFile, size, newTime.toEpochMilli())).isNull();
    }

    @Test
    @DisplayName("Should remove a stale entry from the in-memory map on access")
    void testRemoveOutdatedEntry() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "data");
        long size = Files.size(testFile);
        long oldEpoch = Instant.now().minusSeconds(3600).toEpochMilli();
        long newEpoch = Instant.now().toEpochMilli();

        cacheManager.putChecksum(testFile, 12345L, size, oldEpoch);
        assertThat(cacheManager.getCacheSize()).isEqualTo(1);

        cacheManager.getCachedChecksum(testFile, size, newEpoch); // triggers removal

        assertThat(cacheManager.getCacheSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle multiple independent cache entries")
    void testMultipleCacheEntries() throws IOException {
        Path f1 = tempDir.resolve("f1.txt");
        Path f2 = tempDir.resolve("f2.txt");
        Path f3 = tempDir.resolve("f3.txt");
        for (Path f : List.of(f1, f2, f3)) Files.writeString(f, "x");

        Instant now = Instant.now();
        cacheManager.putChecksum(f1, 1111L, now);
        cacheManager.putChecksum(f2, 2222L, now);
        cacheManager.putChecksum(f3, 3333L, now);

        assertThat(cacheManager.getCacheSize()).isEqualTo(3);
        assertThat(cacheManager.getCachedChecksum(f1, now)).isEqualTo(1111L);
        assertThat(cacheManager.getCachedChecksum(f2, now)).isEqualTo(2222L);
        assertThat(cacheManager.getCachedChecksum(f3, now)).isEqualTo(3333L);
    }

    @Test
    @DisplayName("Should clear all entries and delete the cache file")
    void testClearCache() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "data");
        cacheManager.putChecksum(testFile, 12345L, Instant.now());
        assertThat(cacheManager.getCacheSize()).isGreaterThan(0);

        cacheManager.clearCache();

        assertThat(cacheManager.getCacheSize()).isEqualTo(0);
        assertThat(cacheManager.getCachedChecksum(testFile, Instant.now())).isNull();
    }

    @Test
    @DisplayName("Should update an existing cache entry without increasing the count")
    void testUpdateExistingEntry() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "data");
        Instant now = Instant.now();

        cacheManager.putChecksum(testFile, 1111L, now);
        cacheManager.putChecksum(testFile, 2222L, now);

        assertThat(cacheManager.getCachedChecksum(testFile, now)).isEqualTo(2222L);
        assertThat(cacheManager.getCacheSize()).isEqualTo(1); // still just one entry
    }

    @Test
    @DisplayName("Should handle files with identical content but at different paths")
    void testDifferentPathsSameContent() throws IOException {
        Path f1 = Files.createDirectories(tempDir.resolve("s1")).resolve("test.txt");
        Path f2 = Files.createDirectories(tempDir.resolve("s2")).resolve("test.txt");
        Files.writeString(f1, "same");
        Files.writeString(f2, "same");
        Instant now = Instant.now();

        cacheManager.putChecksum(f1, 99L, now);
        cacheManager.putChecksum(f2, 99L, now);

        assertThat(cacheManager.getCacheSize()).isEqualTo(2);
        assertThat(cacheManager.getCachedChecksum(f1, now)).isEqualTo(99L);
        assertThat(cacheManager.getCachedChecksum(f2, now)).isEqualTo(99L);
    }

    @Test
    @DisplayName("Should handle files with special characters in paths")
    void testSpecialCharactersInPaths() throws IOException {
        Path specialFile = tempDir.resolve("file with spaces & symbols!@#$%^&().txt");
        Files.writeString(specialFile, "content");
        Instant now = Instant.now();

        cacheManager.putChecksum(specialFile, 12345L, now);
        assertThat(cacheManager.getCachedChecksum(specialFile, now)).isEqualTo(12345L);
    }

    @Test
    @DisplayName("Should handle a large number of cache entries")
    void testLargeNumberOfEntries() throws IOException {
        int count = 1000;
        Instant now = Instant.now();
        for (int i = 0; i < count; i++) {
            cacheManager.putChecksum(tempDir.resolve("file" + i + ".txt"), (long) i, now);
        }
        assertThat(cacheManager.getCacheSize()).isEqualTo(count);
        assertThat(cacheManager.getCachedChecksum(tempDir.resolve("file100.txt"), now)).isEqualTo(100L);
        assertThat(cacheManager.getCachedChecksum(tempDir.resolve("file999.txt"), now)).isEqualTo(999L);
    }

    @Test
    @DisplayName("Should handle concurrent put/get without data corruption")
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int opsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int id = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < opsPerThread; j++) {
                    Path file = tempDir.resolve("t" + id + "_f" + j + ".txt");
                    Instant now = Instant.now();
                    long cs = id * 1000L + j;
                    cacheManager.putChecksum(file, cs, now);
                    Long got = cacheManager.getCachedChecksum(file, now);
                    assertThat(got).isEqualTo(cs);
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join(5000);

        assertThat(cacheManager.getCacheSize()).isEqualTo(threadCount * opsPerThread);
    }

    @Test @DisplayName("Should handle zero checksum")
    void testZeroChecksum() throws IOException {
        Path f = tempDir.resolve("zero.txt");
        Files.writeString(f, "x");
        Instant now = Instant.now();
        cacheManager.putChecksum(f, 0L, now);
        assertThat(cacheManager.getCachedChecksum(f, now)).isEqualTo(0L);
    }

    @Test @DisplayName("Should handle negative checksum")
    void testNegativeChecksum() throws IOException {
        Path f = tempDir.resolve("neg.txt");
        Files.writeString(f, "x");
        Instant now = Instant.now();
        cacheManager.putChecksum(f, -12345L, now);
        assertThat(cacheManager.getCachedChecksum(f, now)).isEqualTo(-12345L);
    }

    @Test @DisplayName("Should handle Long.MAX_VALUE checksum")
    void testMaxChecksum() throws IOException {
        Path f = tempDir.resolve("max.txt");
        Files.writeString(f, "x");
        Instant now = Instant.now();
        cacheManager.putChecksum(f, Long.MAX_VALUE, now);
        assertThat(cacheManager.getCachedChecksum(f, now)).isEqualTo(Long.MAX_VALUE);
    }

    @Test @DisplayName("Should handle Long.MIN_VALUE checksum")
    void testMinChecksum() throws IOException {
        Path f = tempDir.resolve("min.txt");
        Files.writeString(f, "x");
        Instant now = Instant.now();
        cacheManager.putChecksum(f, Long.MIN_VALUE, now);
        assertThat(cacheManager.getCachedChecksum(f, now)).isEqualTo(Long.MIN_VALUE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NEW: Cache invalidation when file is modified
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that modifying a file's content (which changes its {@code lastModified}
     * timestamp) causes the cache entry to be invalidated on the next lookup.
     *
     * <p>The test sleeps 1 100 ms between writes to guarantee the filesystem records a
     * different {@code lastModifiedTime} (most filesystems have at least 1-second granularity;
     * on Windows NTFS the granularity is 100 ns, but a 1-second sleep gives plenty of margin).</p>
     */
    @Test
    @DisplayName("Cache invalidated when file content is modified (different lastModified)")
    void testCacheInvalidatedWhenFileModified() throws IOException, InterruptedException {
        Path file = tempDir.resolve("mutable.txt");

        // First write — establish the initial cache entry.
        Files.writeString(file, "original content");
        long size1   = Files.size(file);
        long epoch1  = Files.getLastModifiedTime(file).toMillis();
        long crc1    = 0xABCD1234L; // simulated checksum
        cacheManager.putChecksum(file, crc1, size1, epoch1);

        // Verify the entry is in cache before modification.
        assertThat(cacheManager.getCachedChecksum(file, size1, epoch1))
            .as("Entry should be in cache before modification")
            .isEqualTo(crc1);

        // Wait for filesystem timestamp to advance.
        Thread.sleep(1_100);

        // Rewrite the file — different content, guaranteed new lastModified.
        Files.writeString(file, "modified content — this is different!");
        long size2  = Files.size(file);
        long epoch2 = Files.getLastModifiedTime(file).toMillis();

        // Timestamps must actually differ (protects test integrity).
        assertThat(epoch2).as("Filesystem must have recorded a new lastModified").isGreaterThan(epoch1);

        // The cache lookup with the NEW metadata must return null (invalidated).
        assertThat(cacheManager.getCachedChecksum(file, size2, epoch2))
            .as("Stale cache entry must be invalidated after file modification")
            .isNull();

        // The stale entry must have been removed from the in-memory map.
        assertThat(cacheManager.getCacheSize())
            .as("Stale entry must be removed from the in-memory cache")
            .isEqualTo(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NEW: Atomic save — no cache corruption
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that {@link CacheManager#saveCache()} writes a valid, parseable file and that
     * a second {@link CacheManager} instance loading from the same file reconstructs all entries.
     *
     * <p>This also implicitly verifies the atomic-move mechanism: if the tmp→actual rename
     * fails with an exception, no file should exist or the old file should remain intact.
     * In the happy path (rename succeeds), the entries loaded by {@code cm2} must exactly
     * match those stored by {@code cacheManager}.</p>
     */
    @Test
    @DisplayName("Atomic save: second CacheManager reads back the same entries")
    void testAtomicSaveNoCacheCorruption() throws IOException {
        Path cacheFile = tempDir.resolve("shared-cache.properties");
        CacheManager cm1 = new CacheManager(cacheFile);

        // Populate with known entries.
        Path f1 = tempDir.resolve("alpha.txt");
        Path f2 = tempDir.resolve("beta.txt");
        Files.writeString(f1, "alpha content");
        Files.writeString(f2, "beta content");
        long size1   = Files.size(f1);
        long size2   = Files.size(f2);
        long epoch   = Instant.now().toEpochMilli();
        long crc1    = 0x11112222L;
        long crc2    = 0x33334444L;

        cm1.putChecksum(f1, crc1, size1, epoch);
        cm1.putChecksum(f2, crc2, size2, epoch);

        // Flush to disk atomically.
        cm1.saveCache();

        // The cache file must exist after saveCache().
        assertThat(cacheFile).exists();
        // The .tmp sibling must NOT remain after a successful atomic move.
        assertThat(cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp")).doesNotExist();

        // Instantiate a fresh CacheManager pointing at the same file.
        CacheManager cm2 = new CacheManager(cacheFile);

        // Both entries must be loadable with the exact checksums.
        assertThat(cm2.getCachedChecksum(f1, size1, epoch))
            .as("cm2 must read back entry for f1 saved by cm1")
            .isEqualTo(crc1);

        assertThat(cm2.getCachedChecksum(f2, size2, epoch))
            .as("cm2 must read back entry for f2 saved by cm1")
            .isEqualTo(crc2);

        assertThat(cm2.getCacheSize()).isEqualTo(2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NEW: Strict size invalidation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that a cache entry is invalidated when the file's size changes,
     * even if the {@code lastModified} timestamp is identical.
     *
     * <p>This guards against the edge case of same-tick rewrites on coarse-grained
     * filesystems (e.g., FAT32 with 2-second granularity) where the timestamp does
     * not advance even though the file's content has changed.</p>
     */
    @Test
    @DisplayName("Strict size invalidation: different size with same timestamp yields null")
    void testStrictSizeInvalidation() throws IOException {
        Path file = tempDir.resolve("resize.txt");
        Files.writeString(file, "original");
        long epoch = Instant.now().toEpochMilli();

        // Cache with size = 8 (length of "original").
        cacheManager.putChecksum(file, 0xDEADBEEFL, 8L, epoch);

        // Look up with a DIFFERENT size (e.g., the file was rewritten to 20 bytes)
        // but the SAME epoch millisecond — simulating a same-tick overwrite.
        Long result = cacheManager.getCachedChecksum(file, 20L, epoch);

        assertThat(result)
            .as("Size mismatch must invalidate the cache entry even when timestamp matches")
            .isNull();

        assertThat(cacheManager.getCacheSize())
            .as("Invalidated entry must be removed from the in-memory map")
            .isEqualTo(0);
    }
}
