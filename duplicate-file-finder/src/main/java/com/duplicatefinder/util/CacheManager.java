package com.duplicatefinder.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Simple file-based cache manager for storing file checksums.
 * Uses properties file format for persistence.
 */
public class CacheManager {
    private static final Logger logger = LoggerFactory.getLogger(CacheManager.class);
    private static final String CACHE_FILE = "duplicate_finder_cache.properties";
    
    private final Map<String, CacheEntry> cache = new HashMap<>();
    private final Path cacheFilePath;
    
    /**
     * Represents a cached entry with checksum and modification time.
     */
    private static class CacheEntry {
        final long checksum;
        final Instant lastModified;
        
        CacheEntry(long checksum, Instant lastModified) {
            this.checksum = checksum;
            this.lastModified = lastModified;
        }
    }
    
    public CacheManager() {
        this.cacheFilePath = Path.of(System.getProperty("user.home"), CACHE_FILE);
        loadCache();
    }
    
    /**
     * Loads cache from disk if it exists.
     */
    private void loadCache() {
        if (!Files.exists(cacheFilePath)) {
            logger.debug("Cache file not found, starting with empty cache");
            return;
        }
        
        try (InputStream input = Files.newInputStream(cacheFilePath)) {
            Properties props = new Properties();
            props.load(input);
            
            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);
                String[] parts = value.split(":");
                if (parts.length == 2) {
                    long checksum = Long.parseLong(parts[0]);
                    Instant lastModified = Instant.parse(parts[1]);
                    cache.put(key, new CacheEntry(checksum, lastModified));
                }
            }
            
            logger.info("Loaded {} entries from cache", cache.size());
        } catch (IOException e) {
            logger.warn("Failed to load cache file: {}", cacheFilePath, e);
            cache.clear();
        }
    }
    
    /**
     * Saves cache to disk.
     */
    public void saveCache() {
        try (OutputStream output = Files.newOutputStream(cacheFilePath)) {
            Properties props = new Properties();
            
            for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
                String value = entry.getValue().checksum + ":" + entry.getValue().lastModified;
                props.setProperty(entry.getKey(), value);
            }
            
            props.store(output, "Duplicate File Finder Cache - Generated at " + Instant.now());
            logger.info("Saved {} entries to cache", cache.size());
        } catch (IOException e) {
            logger.error("Failed to save cache file: {}", cacheFilePath, e);
        }
    }
    
    /**
     * Gets cached checksum for a file if it's still valid.
     * 
     * @param filePath Path to the file
     * @param lastModified Last modification time of the file
     * @return Cached checksum or null if not found or outdated
     */
    public Long getCachedChecksum(Path filePath, Instant lastModified) {
        CacheEntry entry = cache.get(filePath.toAbsolutePath().toString());
        
        if (entry != null) {
            if (entry.lastModified.equals(lastModified)) {
                logger.debug("Cache hit for file: {}", filePath);
                return entry.checksum;
            } else {
                logger.debug("Cache entry outdated for file: {}", filePath);
                cache.remove(filePath.toAbsolutePath().toString());
            }
        }
        
        return null;
    }
    
    /**
     * Puts a checksum in the cache.
     * 
     * @param filePath Path to the file
     * @param checksum CRC32 checksum
     * @param lastModified Last modification time of the file
     */
    public void putChecksum(Path filePath, long checksum, Instant lastModified) {
        String key = filePath.toAbsolutePath().toString();
        cache.put(key, new CacheEntry(checksum, lastModified));
        logger.debug("Cached checksum for file: {}", filePath);
    }
    
    /**
     * Clears the cache.
     */
    public void clearCache() {
        cache.clear();
        try {
            Files.deleteIfExists(cacheFilePath);
            logger.info("Cache cleared");
        } catch (IOException e) {
            logger.warn("Failed to delete cache file: {}", cacheFilePath, e);
        }
    }
    
    /**
     * Gets cache statistics.
     * 
     * @return Number of cached entries
     */
    public int getCacheSize() {
        return cache.size();
    }
}
