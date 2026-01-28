package com.duplicatefinder.service;

import com.duplicatefinder.model.FileInfo;
import com.duplicatefinder.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Service for scanning directories and collecting file information.
 * Supports configurable depth, minimum size filtering, and progress reporting.
 */
public class DirectoryScanner {
    private static final Logger logger = LoggerFactory.getLogger(DirectoryScanner.class);
    
    private final Path directory;
    private final int maxDepth;
    private final long minSize;
    private final boolean followSymlinks;
    private Consumer<Integer> progressListener;

    /**
     * Constructor for DirectoryScanner.
     *
     * @param directory Root directory to scan
     * @param maxDepth Maximum depth for scanning
     */
    public DirectoryScanner(Path directory, int maxDepth) {
        this(directory, maxDepth, 0, false);
    }

    /**
     * Full constructor for DirectoryScanner.
     *
     * @param directory Root directory to scan
     * @param maxDepth Maximum depth for scanning
     * @param minSize Minimum file size to consider (in bytes)
     * @param followSymlinks Whether to follow symbolic links
     */
    public DirectoryScanner(Path directory, int maxDepth, long minSize, boolean followSymlinks) {
        this.directory = directory;
        this.maxDepth = maxDepth;
        this.minSize = minSize;
        this.followSymlinks = followSymlinks;
    }

    /**
     * Sets a progress listener for scan updates.
     *
     * @param progressListener Consumer that receives count of processed files
     */
    public void setProgressListener(Consumer<Integer> progressListener) {
        this.progressListener = progressListener;
    }

    /**
     * Scans the directory and collects file information.
     *
     * @return List of FileInfo objects
     * @throws IOException if an I/O error occurs
     */
    public List<FileInfo> scan() throws IOException {
        List<FileInfo> files = new ArrayList<>();
        AtomicInteger fileCount = new AtomicInteger(0);

        if (!Files.exists(directory)) {
            logger.error("Directory does not exist: {}", directory);
            throw new IOException("Directory does not exist: " + directory);
        }

        if (!Files.isDirectory(directory)) {
            logger.error("Path is not a directory: {}", directory);
            throw new IOException("Path is not a directory: " + directory);
        }

        logger.info("Starting scan of directory: {}", directory);

        Files.walkFileTree(directory, java.util.EnumSet.noneOf(FileVisitOption.class),
            maxDepth, new SimpleFileVisitor<Path>() {
            
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                // Double check it's actually a file, not a directory
                if (attrs.isDirectory()) {
                    return FileVisitResult.CONTINUE;
                }
                
                // Skip symbolic links if not following them
                if (!followSymlinks && Files.isSymbolicLink(file)) {
                    logger.debug("Skipping symbolic link: {}", file);
                    return FileVisitResult.CONTINUE;
                }

                try {
                    long fileSize = attrs.size();
                    if (fileSize >= minSize) {
                        Instant lastModified = attrs.lastModifiedTime().toInstant();
                        FileInfo fileInfo = new FileInfo(
                            file.getFileName().toString(),
                            file.toString(),
                            fileSize,
                            lastModified
                        );
                        files.add(fileInfo);
                        
                        int count = fileCount.incrementAndGet();
                        if (count % 100 == 0) {
                            logger.info("Scanned {} files so far...", count);
                            if (progressListener != null) {
                                progressListener.accept(count);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error accessing file: {}", file, e);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                logger.warn("Failed to access directory/file: {} - {}", file, exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });

        logger.info("Scan completed. Total files found: {}", files.size());
        if (progressListener != null) {
            progressListener.accept(files.size());
        }

        return files;
    }

    /**
     * Gets the root directory being scanned.
     *
     * @return Path to the directory
     */
    public Path getDirectory() {
        return directory;
    }

    /**
     * Gets the maximum depth for scanning.
     *
     * @return Maximum depth
     */
    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * Gets the minimum file size filter.
     *
     * @return Minimum size in bytes
     */
    public long getMinSize() {
        return minSize;
    }
}
