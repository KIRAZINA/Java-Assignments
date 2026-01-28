package com.duplicatefinder.service;

import com.duplicatefinder.model.FileInfo;
import com.duplicatefinder.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Service for detecting duplicate files using a three-level strategy:
 * 1. Group by file name (fast, cheap)
 * 2. Filter by file size (medium)
 * 3. Compute CRC32 checksum (slow, expensive - only for candidates)
 */
public class DuplicateDetector {
    
    private static final Logger logger = LoggerFactory.getLogger(DuplicateDetector.class);
    private boolean caseInsensitive = false;
    private Consumer<Integer> progressListener;

    /**
     * Sets whether file names should be compared case-insensitively.
     *
     * @param caseInsensitive true for case-insensitive comparison
     */
    public void setCaseInsensitive(boolean caseInsensitive) {
        this.caseInsensitive = caseInsensitive;
    }

    /**
     * Sets a progress listener for detection updates.
     *
     * @param progressListener Consumer that receives count of processed files
     */
    public void setProgressListener(Consumer<Integer> progressListener) {
        this.progressListener = progressListener;
    }

    /**
     * Detects duplicate files using three-level strategy.
     *
     * @param files List of FileInfo objects to check
     * @return Map where key is canonical file path and value is list of duplicate paths
     */
    public Map<String, List<String>> detectDuplicates(List<FileInfo> files) {
        logger.info("Starting duplicate detection for {} files", files.size());
        
        // Level 1: Group by file name
        Map<String, List<FileInfo>> groupedByName = groupByFileName(files);
        logger.info("Level 1 (by name): {} potential duplicate groups", groupedByName.size());

        // Level 2: Filter by file size
        Map<String, List<FileInfo>> groupedByNameAndSize = groupByNameAndSize(groupedByName);
        logger.info("Level 2 (by name+size): {} potential duplicate groups", groupedByNameAndSize.size());

        // Level 3: Compute CRC32 for candidates
        Map<String, List<String>> finalDuplicates = groupByCrc32(groupedByNameAndSize);
        logger.info("Level 3 (by CRC32): {} duplicate groups found", finalDuplicates.size());

        return finalDuplicates;
    }

    /**
     * Groups files by name (case-sensitive or case-insensitive based on configuration).
     */
    private Map<String, List<FileInfo>> groupByFileName(List<FileInfo> files) {
        Map<String, List<FileInfo>> grouped = new HashMap<>();

        for (FileInfo file : files) {
            String key = caseInsensitive 
                ? file.getName().toLowerCase() 
                : file.getName();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(file);
        }

        // Remove groups with only one file
        return grouped.entrySet().stream()
            .filter(e -> e.getValue().size() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Further groups files by name and size.
     */
    private Map<String, List<FileInfo>> groupByNameAndSize(Map<String, List<FileInfo>> groupedByName) {
        Map<String, List<FileInfo>> grouped = new HashMap<>();

        for (List<FileInfo> fileList : groupedByName.values()) {
            for (FileInfo file : fileList) {
                String key = file.getName() + "|" + file.getSize();
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(file);
            }
        }

        // Remove groups with only one file
        return grouped.entrySet().stream()
            .filter(e -> e.getValue().size() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Groups files by CRC32 checksum (expensive operation).
     */
    private Map<String, List<String>> groupByCrc32(Map<String, List<FileInfo>> groupedByNameAndSize) {
        Map<Long, List<FileInfo>> groupedByCrc = new HashMap<>();
        AtomicInteger processedCount = new AtomicInteger(0);
        int totalToProcess = (int) groupedByNameAndSize.values().stream()
            .mapToInt(List::size)
            .count();

        for (List<FileInfo> fileList : groupedByNameAndSize.values()) {
            for (FileInfo file : fileList) {
                try {
                    long crc32 = HashUtil.computeCrc32(java.nio.file.Paths.get(file.getPath()));
                    file.setCrc32(crc32);
                    
                    groupedByCrc.computeIfAbsent(crc32, k -> new ArrayList<>()).add(file);
                    
                    int processed = processedCount.incrementAndGet();
                    if (processed % 10 == 0) {
                        logger.debug("CRC32 computed for {} files", processed);
                        if (progressListener != null) {
                            progressListener.accept(processed);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to compute CRC32 for file: {}", file.getPath(), e);
                }
            }
        }

        // Convert to final format: group by CRC32, return original file + duplicates
        Map<String, List<String>> result = new HashMap<>();
        for (List<FileInfo> fileList : groupedByCrc.values()) {
            if (fileList.size() > 1) {
                // Use first file as key, all others as duplicates
                FileInfo firstFile = fileList.get(0);
                List<String> paths = fileList.stream()
                    .map(FileInfo::getPath)
                    .collect(Collectors.toList());
                result.put(firstFile.getPath(), paths);
            }
        }

        return result;
    }
}
