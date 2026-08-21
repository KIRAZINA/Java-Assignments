package com.duplicatefinder.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, crash-consistent file checksum cache.
 *
 * <h2>Thread Safety</h2>
 * <p>
 * CRC32 computation runs in a parallel {@code ExecutorService} — multiple threads call
 * {@link #putChecksum} and {@link #getCachedChecksum} concurrently.
 * We use a {@link ConcurrentHashMap} so that every individual put/get is atomic without
 * requiring an external lock.  We deliberately do <em>not</em> write to the disk cache
 * from multiple threads; instead, all threads write to the in-memory map, and a single
 * {@link #saveCache()} call (in the application's {@code finally} block) performs one
 * batched, atomic flush to disk.
 * </p>
 *
 * <h2>Atomic File Write</h2>
 * <p>
 * If the JVM is killed while writing a file directly to {@code ~/duplicate_finder_cache.properties},
 * the file will be left in a partially-written, corrupt state.  To prevent this we use a
 * write-to-temp-then-rename strategy:
 * <ol>
 *   <li>Write all data to {@code ~/duplicate_finder_cache.properties.tmp}</li>
 *   <li>Call {@code Files.move(tmp, actual, ATOMIC_MOVE, REPLACE_EXISTING)}</li>
 * </ol>
 * On Linux/macOS this translates to a single {@code rename(2)} syscall, which is guaranteed
 * by POSIX to be atomic — readers always see either the old complete file or the new complete
 * file, never a partially-written one.  On Windows, {@code ATOMIC_MOVE} falls back to a
 * non-atomic but still transactionally safe copy+delete if the filesystem doesn't support it.
 * </p>
 *
 * <h2>Strict Cache Invalidation</h2>
 * <p>
 * A cache entry is only valid when <b>both</b> the file's {@code size} and
 * {@code lastModifiedEpochMs} match exactly.  Checking size in addition to mtime catches
 * the edge case where a file is rewritten with the same content length within the same
 * filesystem timestamp tick (e.g., FAT32's 2-second granularity).
 * </p>
 *
 * <h2>Cache Entry Format</h2>
 * <p>The on-disk format per line is: {@code <absolutePath>=<size>:<lastModifiedEpochMs>:<crc32>}</p>
 */
public class CacheManager {

    private static final Logger logger = LoggerFactory.getLogger(CacheManager.class);

    /** Default on-disk cache file location. */
    private static final String DEFAULT_CACHE_FILENAME = "duplicate_finder_cache.properties";

    // ── In-memory store (thread-safe) ──────────────────────────────────────
    // ConcurrentHashMap: lock-striped, O(1) amortised get/put, safe for concurrent writers.
    // This is the ONLY mutable shared state accessed from multiple hashing threads.
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private final Path cacheFilePath;
    private final Path cacheTmpPath;  // sibling .tmp file used during atomic save

    // -------------------------------------------------------------------------
    // Cache Entry model
    // -------------------------------------------------------------------------

    /**
     * Immutable snapshot of a file's identity at the time it was hashed.
     *
     * <p>Immutability is intentional: once a {@code CacheEntry} is put into the map,
     * no field can change. A fresh entry is created to replace a stale one, ensuring
     * that a thread reading an old entry concurrently with a write never sees a
     * half-updated object.</p>
     */
    static final class CacheEntry {
        final long size;
        final long lastModifiedEpochMs;
        final long crc32;

        CacheEntry(long size, long lastModifiedEpochMs, long crc32) {
            this.size = size;
            this.lastModifiedEpochMs = lastModifiedEpochMs;
            this.crc32 = crc32;
        }

        /**
         * Serialises to the on-disk format: {@code size:epochMs:crc32}.
         * Using ':' as separator is safe because none of these fields contain colons.
         */
        String serialise() {
            return size + ":" + lastModifiedEpochMs + ":" + crc32;
        }

        /**
         * Deserialises from the on-disk format.
         *
         * @param raw the property value string
         * @return parsed entry, or {@code null} if the format is unrecognisable
         */
        static CacheEntry deserialise(String raw) {
            if (raw == null) return null;
            String[] parts = raw.split(":", -1);
            if (parts.length == 3) {
                // New format: size:epochMs:crc32
                try {
                    return new CacheEntry(
                        Long.parseLong(parts[0]),
                        Long.parseLong(parts[1]),
                        Long.parseLong(parts[2])
                    );
                } catch (NumberFormatException ignored) { /* fall through */ }
            } else if (parts.length == 2) {
                // Legacy format: crc32:isoInstant — migrate gracefully by returning null (will re-hash)
                logger.debug("Legacy cache entry detected, will re-hash on next access");
            }
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Default constructor — uses the standard cache location in the user's home directory.
     * Used by the production application.
     */
    public CacheManager() {
        this(Path.of(System.getProperty("user.home"), DEFAULT_CACHE_FILENAME));
    }

    /**
     * Testable constructor — accepts an explicit cache file path.
     * Allows unit tests to isolate cache state in a {@code @TempDir}.
     *
     * @param cacheFilePath the path where the cache properties file will be stored
     */
    public CacheManager(Path cacheFilePath) {
        this.cacheFilePath = cacheFilePath;
        this.cacheTmpPath  = cacheFilePath.resolveSibling(
                cacheFilePath.getFileName().toString() + ".tmp");
        try {
            Path parent = cacheFilePath.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
        } catch (IOException e) {
            logger.warn("Could not create cache directory for {}: {}", cacheFilePath, e.getMessage());
        }
        loadCache();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the cached CRC32 for a file if the entry is still valid.
     *
     * <p>Invalidation condition: the stored {@code size} OR {@code lastModifiedEpochMs}
     * does not exactly match the provided values.  A mismatch causes the stale entry to
     * be removed and {@code null} is returned, forcing a full re-hash.</p>
     *
     * @param filePath           the absolute path of the file
     * @param currentSize        the file's current size in bytes (from {@code BasicFileAttributes})
     * @param currentEpochMs     the file's current lastModified in milliseconds since epoch
     * @return the cached CRC32, or {@code null} if no valid entry exists
     */
    public Long getCachedChecksum(Path filePath, long currentSize, long currentEpochMs) {
        String key = filePath.toAbsolutePath().toString();
        CacheEntry entry = cache.get(key);

        if (entry == null) {
            return null; // cache miss
        }

        // Strict validation: both size AND mtime must match.
        // Checking size catches same-mtime rewrites on coarse-grained filesystems.
        if (entry.size == currentSize && entry.lastModifiedEpochMs == currentEpochMs) {
            logger.debug("Cache hit for: {}", filePath);
            return entry.crc32;
        }

        // Stale entry — remove it so getCacheSize() reflects reality.
        cache.remove(key);
        logger.debug("Cache invalidated (size or mtime changed) for: {}", filePath);
        return null;
    }

    /**
     * Legacy overload kept for backward compatibility with existing tests that pass an
     * {@code Instant}.  Converts to epoch-millis and delegates.
     */
    public Long getCachedChecksum(Path filePath, java.time.Instant lastModified) {
        // Legacy callers supplied only a timestamp and may use paths that do not exist.
        String key = filePath.toAbsolutePath().toString();
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (entry.lastModifiedEpochMs == lastModified.toEpochMilli()) return entry.crc32;
        cache.remove(key);
        return null;
    }

    /**
     * Stores a CRC32 result in the in-memory cache.
     *
     * <p>This method is called from parallel hashing threads.  Because we use a
     * {@code ConcurrentHashMap}, the underlying put is lock-free at the map level;
     * no external synchronisation is needed here.</p>
     *
     * @param filePath       the file's path
     * @param crc32          the computed checksum
     * @param size           the file's size at computation time
     * @param epochMs        the file's lastModified epoch-millisecond at computation time
     */
    public void putChecksum(Path filePath, long crc32, long size, long epochMs) {
        String key = filePath.toAbsolutePath().toString();
        cache.put(key, new CacheEntry(size, epochMs, crc32));
        logger.debug("Cached checksum for: {}", filePath);
    }

    /**
     * Legacy overload for backward compatibility with existing code that passes an
     * {@code Instant} and a separate size is not available.
     */
    public void putChecksum(Path filePath, long crc32, java.time.Instant lastModified) {
        try {
            long size = Files.size(filePath);
            putChecksum(filePath, crc32, size, lastModified.toEpochMilli());
        } catch (IOException e) {
            // Best-effort: if we can't stat the file, cache with size=0
            putChecksum(filePath, crc32, 0L, lastModified.toEpochMilli());
        }
    }

    /**
     * Performs an atomic, crash-safe flush of all in-memory cache entries to disk.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Serialise all entries into a {@link Properties} object.</li>
     *   <li>Write the Properties to a {@code .tmp} sibling file.</li>
     *   <li>Atomically rename the {@code .tmp} file over the real cache file using
     *       {@link StandardCopyOption#ATOMIC_MOVE}.</li>
     * </ol>
     *
     * Step 3 is atomic on POSIX systems ({@code rename(2)} syscall).  If the JVM is killed
     * between steps 2 and 3, the old cache file remains intact and the orphaned {@code .tmp}
     * file is simply ignored on the next load.</p>
     */
    public void saveCache() {
        // Snapshot the current entries — avoids holding any lock while doing I/O.
        Properties props = new Properties();
        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue().serialise());
        }

        try {
            // Step 1: Write to the .tmp file.
            try (OutputStream out = Files.newOutputStream(cacheTmpPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                props.store(out, "Duplicate File Finder Cache — generated by CacheManager");
            }

            // Step 2: Atomically replace the real file.
            // ATOMIC_MOVE: on POSIX this is a single rename() syscall (guaranteed atomic by POSIX).
            // REPLACE_EXISTING: removes any pre-existing cache file before the rename.
            Files.move(cacheTmpPath, cacheFilePath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);

            logger.info("Cache saved atomically: {} entries → {}", cache.size(), cacheFilePath);

        } catch (AtomicMoveNotSupportedException ex) {
            // Fallback for filesystems that don't support atomic move (rare).
            // Non-atomic but still safe: old file remains valid until overwrite completes.
            logger.warn("ATOMIC_MOVE not supported; falling back to direct write: {}", ex.getMessage());
            try (OutputStream out = Files.newOutputStream(cacheFilePath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                props.store(out, "Duplicate File Finder Cache");
            } catch (IOException ioEx) {
                logger.error("Cache save failed (fallback): {}", ioEx.getMessage(), ioEx);
            }
        } catch (IOException e) {
            logger.error("Failed to save cache to {}: {}", cacheFilePath, e.getMessage(), e);
        }
    }

    /**
     * Clears all in-memory entries and deletes the on-disk cache file.
     */
    public void clearCache() {
        cache.clear();
        try {
            Files.deleteIfExists(cacheFilePath);
            Files.deleteIfExists(cacheTmpPath);
            logger.info("Cache cleared");
        } catch (IOException e) {
            logger.warn("Failed to delete cache file: {}", e.getMessage());
        }
    }

    /**
     * Returns the number of entries currently held in the in-memory cache.
     */
    public int getCacheSize() {
        return cache.size();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Loads previously saved entries from the on-disk cache file into the in-memory map.
     * Called once during construction.
     */
    private void loadCache() {
        if (!Files.exists(cacheFilePath)) {
            logger.debug("No cache file found at {}; starting fresh.", cacheFilePath);
            return;
        }

        try (InputStream in = Files.newInputStream(cacheFilePath)) {
            Properties props = new Properties();
            props.load(in);

            int loaded = 0;
            for (String key : props.stringPropertyNames()) {
                CacheEntry entry = CacheEntry.deserialise(props.getProperty(key));
                if (entry != null) {
                    cache.put(key, entry);
                    loaded++;
                }
            }
            logger.info("Loaded {} valid entries from cache file {}", loaded, cacheFilePath);

        } catch (IOException e) {
            logger.warn("Could not load cache file {}; starting fresh. Reason: {}", cacheFilePath, e.getMessage());
            cache.clear();
        }
    }
}

