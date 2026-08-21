package com.duplicatefinder.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.util.zip.CRC32;

/**
 * Utility class for computing file checksums (CRC32) with caching support.
 *
 * <h2>Why FileChannel over FileInputStream?</h2>
 * <p>
 * {@code FileInputStream} wraps each read in a JNI call, which transitions from
 * user-space to kernel-space on every invocation, carrying significant overhead for
 * small buffers. {@code FileChannel}, by contrast, maps directly to the OS's
 * {@code read()} / {@code pread()} syscall family and participates in the OS's
 * unified buffer cache (page cache).  The OS keeps recently-read pages hot, so
 * re-reading the same file is effectively free (no disk I/O at all).
 * </p>
 *
 * <h2>64 KB buffer sizing rationale</h2>
 * <p>
 * Modern SSDs and HDD controllers present their optimal transfer size around 64–128 KB.
 * A 64 KB {@code ByteBuffer} amortises the per-read syscall cost over a large chunk,
 * reducing the number of kernel transitions to roughly {@code fileSize / 65536}.
 * For a 10 MB file that drops from ~1 280 reads (with 8 KB) to only ~160 reads.
 * Using a direct (off-heap) {@code ByteBuffer} additionally avoids a copy from the
 * kernel buffer into the Java heap.
 * </p>
 *
 * <h2>Empty-file fast-path</h2>
 * <p>
 * A zero-byte file has a mathematically defined CRC32 of {@code 0L}.  Opening a
 * {@code FileChannel} for such a file would still pay the cost of a {@code open()}
 * syscall, kernel inode lookup, and {@code close()} — all for zero useful work.
 * We short-circuit immediately and return {@code 0L} without touching the filesystem.
 * </p>
 */
public class HashUtil {

    private static final Logger logger = LoggerFactory.getLogger(HashUtil.class);

    /**
     * 64 KB read buffer — chosen to match modern SSD/NVMe optimal read granularity.
     * A direct ByteBuffer avoids one extra copy from the OS kernel buffer into the Java heap.
     */
    private static final int BUFFER_SIZE = 64 * 1024; // 64 KB

    /**
     * Hardcoded CRC32 for zero-length files.
     * The CRC32 of zero bytes is mathematically defined as 0.
     * Every empty file is therefore an exact duplicate of every other empty file.
     */
    public static final long EMPTY_FILE_CRC32 = 0L;

    /**
     * Singleton CacheManager shared across all static helper calls.
     * Declared volatile so the JIT cannot cache a stale reference across threads.
     */
    private static final CacheManager cacheManager = new CacheManager();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Computes the CRC32 checksum for a file using NIO FileChannel.
     *
     * <p><b>Cache contract:</b> a cached entry is valid only when both the file's
     * {@code size} AND {@code lastModified} epoch-millisecond value match what is stored.
     * Changing the file's content without changing its modification time (e.g., same-second
     * write on a FAT32 volume with 2-second granularity) will still trigger re-hashing because
     * the size will differ.</p>
     *
     * @param path Path to the file
     * @return CRC32 checksum as a non-negative long (0L for empty files)
     * @throws IOException if the file cannot be opened or read
     */
    public static long computeCrc32(Path path) throws IOException {
        // Read file metadata — a single stat() syscall, very cheap.
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        long fileSize    = attrs.size();
        long epochMs     = attrs.lastModifiedTime().toMillis();

        // ── Fast-path ── empty file: CRC32 is 0 by definition, no I/O needed.
        if (fileSize == 0) {
            logger.debug("Empty-file fast-path for: {}", path);
            // Still cache the entry so subsequent runs benefit from the cache hit.
            cacheManager.putChecksum(path, EMPTY_FILE_CRC32, fileSize, epochMs);
            return EMPTY_FILE_CRC32;
        }

        // ── Cache lookup ── avoids reading the file if metadata hasn't changed.
        Long cached = cacheManager.getCachedChecksum(path, fileSize, epochMs);
        if (cached != null) {
            logger.debug("Cache hit for: {}", path);
            return cached;
        }

        // ── Full computation via NIO FileChannel ──
        // FileChannel.open() with READ is a thin wrapper around open()/pread() syscalls.
        // The OS kernel maps the file into its page cache; subsequent reads of the same
        // region are served from RAM without any disk I/O.
        long checksum = computeViaChannel(path);

        // Store in cache for future runs.
        cacheManager.putChecksum(path, checksum, fileSize, epochMs);
        return checksum;
    }

    /**
     * Saves the in-memory cache to disk atomically.
     * Must be called once at application shutdown (in a finally block).
     */
    public static void saveCache() {
        cacheManager.saveCache();
    }

    /**
     * Clears all in-memory cache entries and deletes the on-disk cache file.
     * Primarily used in unit tests to ensure a clean state.
     */
    public static void clearCache() {
        cacheManager.clearCache();
    }

    /**
     * Returns the number of entries currently held in the in-memory cache.
     * Useful for test assertions about caching behaviour.
     */
    public static int getCacheSize() {
        return cacheManager.getCacheSize();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Performs the actual CRC32 computation using a NIO FileChannel.
     *
     * <p>We allocate a <em>direct</em> {@code ByteBuffer} once per call.  "Direct" means the
     * buffer lives off-heap in native memory that can be handed directly to the OS DMA engine,
     * bypassing the extra copy from kernel space into the Java heap that an indirect (array-backed)
     * buffer would require.</p>
     *
     * @param path the file to hash
     * @return CRC32 value
     * @throws IOException if the channel cannot be opened or read
     */
    private static long computeViaChannel(Path path) throws IOException {
        CRC32 crc = new CRC32();

        // Direct ByteBuffer — avoids the kernel→heap copy on each channel.read().
        // Allocated once here; the GC will reclaim native memory when the buffer is collected.
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

        // StandardOpenOption.READ opens the file in read-only mode (O_RDONLY on POSIX).
        // No lock is acquired; this is safe for concurrent readers.
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            while (channel.read(buffer) > 0) {
                // Flip switches the buffer from write-mode to read-mode:
                // limit = position, position = 0.
                buffer.flip();

                // Feed all available bytes to the CRC accumulator.
                // CRC32.update(ByteBuffer) reads from position to limit without
                // requiring an intermediate byte[] copy.
                crc.update(buffer);

                // Clear resets position=0, limit=capacity for the next channel.read().
                buffer.clear();
            }
        } catch (IOException e) {
            logger.error("FileChannel read failed for: {}", path, e);
            throw e;
        }

        return crc.getValue();
    }
}
