package com.duplicatefinder.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents information about a file for duplicate detection.
 * Contains file metadata and supports lazy CRC32 computation.
 */
public class FileInfo {
    
    private final String name;
    private final String path;
    private final String absolutePath;
    private final long size;
    private final Instant lastModified;
    private Long crc32; // Lazy computed - null until calculated
    
    /**
     * Constructor for FileInfo.
     *
     * @param name          File name
     * @param path          File path
     * @param size          File size in bytes
     * @param lastModified  Last modified timestamp
     */
    public FileInfo(String name, String path, long size, Instant lastModified) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.lastModified = lastModified;
        this.absolutePath = Path.of(path).toAbsolutePath().toString();
    }

    /**
     * Gets the file name.
     *
     * @return File name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the file path.
     *
     * @return File path
     */
    public String getPath() {
        return path;
    }

    /**
     * Gets the absolute path.
     *
     * @return Absolute path
     */
    public String getAbsolutePath() {
        return absolutePath;
    }

    /**
     * Gets the file size.
     *
     * @return Size in bytes
     */
    public long getSize() {
        return size;
    }

    /**
     * Gets the last modified timestamp.
     *
     * @return Instant of last modification
     */
    public Instant getLastModified() {
        return lastModified;
    }

    /**
     * Gets the CRC32 checksum, computing it lazily if needed.
     *
     * @return CRC32 checksum value
     */
    public Long getCrc32() {
        return crc32;
    }

    /**
     * Sets the CRC32 checksum value.
     *
     * @param crc32 The CRC32 value
     */
    public void setCrc32(Long crc32) {
        this.crc32 = crc32;
    }

    /**
     * Checks if CRC32 has been computed.
     *
     * @return true if CRC32 is computed, false otherwise
     */
    public boolean isCrc32Computed() {
        return crc32 != null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FileInfo)) return false;
        FileInfo other = (FileInfo) obj;
        return Objects.equals(path, other.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public String toString() {
        return String.format("FileInfo{name='%s', path='%s', size=%d, crc32=%s, lastModified=%s}", 
            name, path, size, crc32, lastModified);
    }
}
