package com.duplicatefinder.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.zip.CRC32;

/**
 * Utility class for computing file checksums (CRC32) with caching support.
 */
public class HashUtil {
    private static final Logger logger = LoggerFactory.getLogger(HashUtil.class);
    private static final int BUFFER_SIZE = 8192;
    private static final CacheManager cacheManager = new CacheManager();
    
    /**
     * Computes CRC32 checksum for a file with caching support.
     *
     * @param path Path to the file
     * @return CRC32 checksum value
     * @throws IOException if file cannot be read
     */
    public static long computeCrc32(Path path) throws IOException {
        // Get file attributes for cache validation
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        Instant lastModified = attrs.lastModifiedTime().toInstant();
        
        // Check cache first
        Long cachedChecksum = cacheManager.getCachedChecksum(path, lastModified);
        if (cachedChecksum != null) {
            return cachedChecksum;
        }
        
        // Compute checksum
        CRC32 crc = new CRC32();
        byte[] buffer = new byte[BUFFER_SIZE];
        
        try (var inputStream = Files.newInputStream(path)) {
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                crc.update(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            logger.error("Failed to compute CRC32 for file: {}", path, e);
            throw e;
        }
        
        long checksum = crc.getValue();
        
        // Cache the result
        cacheManager.putChecksum(path, checksum, lastModified);
        
        return checksum;
    }
    
    /**
     * Saves the checksum cache to disk.
     * Should be called when application exits.
     */
    public static void saveCache() {
        cacheManager.saveCache();
    }
    
    /**
     * Clears the checksum cache.
     */
    public static void clearCache() {
        cacheManager.clearCache();
    }
    
    /**
     * Gets cache statistics.
     * 
     * @return Number of cached entries
     */
    public static int getCacheSize() {
        return cacheManager.getCacheSize();
    }

    /**
     * Computes SHA-256 checksum for a file (future enhancement).
     *
     * @param path Path to the file
     * @return SHA-256 checksum as hex string
     * @throws IOException if file cannot be read
     */
    public static String computeSha256(Path path) throws IOException {
        // TODO: Implement SHA-256 computation if needed
        throw new UnsupportedOperationException("SHA-256 computation not yet implemented");
    }
}
