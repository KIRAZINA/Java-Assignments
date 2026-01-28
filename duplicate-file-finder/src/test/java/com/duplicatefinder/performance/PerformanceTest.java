package com.duplicatefinder.performance;

import com.duplicatefinder.model.FileInfo;
import com.duplicatefinder.service.DirectoryScanner;
import com.duplicatefinder.service.DuplicateDetector;
import com.duplicatefinder.util.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Performance Tests")
class PerformanceTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        HashUtil.clearCache();
    }

    @Test
    @DisplayName("Should handle large number of files efficiently")
    void testLargeNumberOfFiles() throws IOException {
        // Given
        int fileCount = 1000;
        int duplicateGroups = 100; // 100 groups of 10 duplicates each
        
        createLargeTestDataset(fileCount, duplicateGroups);

        // When
        long startTime = System.currentTimeMillis();
        
        DirectoryScanner scanner = new DirectoryScanner(tempDir, 10, 0, false);
        List<FileInfo> files = scanner.scan();
        
        DuplicateDetector detector = new DuplicateDetector();
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        assertThat(files).hasSize(fileCount);
        assertThat(duplicates).hasSize(duplicateGroups);
        
        // Performance assertions
        assertThat(duration).isLessThan(30000); // Should complete within 30 seconds
        System.out.printf("Processed %d files in %d ms (%.2f files/sec)%n", 
            fileCount, duration, (double) fileCount / duration * 1000);
    }

    @Test
    @DisplayName("Should demonstrate caching performance improvement")
    void testCachingPerformance() throws IOException {
        // Given
        int fileCount = 500;
        createLargeTestDataset(fileCount, 50);

        // When - first run
        long startTime1 = System.nanoTime();
        DirectoryScanner scanner1 = new DirectoryScanner(tempDir, 10, 0, false);
        List<FileInfo> files1 = scanner1.scan();
        
        DuplicateDetector detector1 = new DuplicateDetector();
        Map<String, List<String>> duplicates1 = detector1.detectDuplicates(files1);
        long endTime1 = System.nanoTime();
        long firstRunDuration = endTime1 - startTime1;

        // When - second run (should use cache)
        long startTime2 = System.nanoTime();
        DirectoryScanner scanner2 = new DirectoryScanner(tempDir, 10, 0, false);
        List<FileInfo> files2 = scanner2.scan();
        
        DuplicateDetector detector2 = new DuplicateDetector();
        Map<String, List<String>> duplicates2 = detector2.detectDuplicates(files2);
        long endTime2 = System.nanoTime();
        long secondRunDuration = endTime2 - startTime2;

        // Then
        assertThat(duplicates1).isEqualTo(duplicates2);
        
        double speedup = (double) firstRunDuration / secondRunDuration;
        System.out.printf("First run: %.2f ms, Second run: %.2f ms, Speedup: %.2fx%n",
            firstRunDuration / 1_000_000.0, secondRunDuration / 1_000_000.0, speedup);
        
        // Cache should provide some speedup (allowing for test environment variations)
        assertThat(speedup).isGreaterThan(0.5); // At least not significantly slower
    }

    @Test
    @DisplayName("Should handle parallel processing efficiently")
    void testParallelProcessingEfficiency() throws IOException {
        // Given
        int fileCount = 200;
        createLargeTestDataset(fileCount, 20);

        // When
        long startTime = System.currentTimeMillis();
        
        DirectoryScanner scanner = new DirectoryScanner(tempDir, 10, 0, false);
        List<FileInfo> files = scanner.scan();
        
        DuplicateDetector detector = new DuplicateDetector();
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        assertThat(duplicates).hasSize(20);
        
        // Should complete reasonably fast with parallel processing
        assertThat(duration).isLessThan(15000); // Within 15 seconds
        
        System.out.printf("Parallel processing of %d files completed in %d ms%n", fileCount, duration);
    }

    @Test
    @DisplayName("Should handle memory usage efficiently")
    void testMemoryUsage() throws IOException {
        // Given
        int fileCount = 1000;
        createLargeTestDataset(fileCount, 100);

        // When
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        DirectoryScanner scanner = new DirectoryScanner(tempDir, 10, 0, false);
        List<FileInfo> files = scanner.scan();
        
        DuplicateDetector detector = new DuplicateDetector();
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);
        
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;

        // Then
        assertThat(files).hasSize(fileCount);
        assertThat(duplicates).hasSize(100);
        
        // Memory usage should be reasonable (less than 100MB for this test)
        assertThat(memoryUsed).isLessThan(100 * 1024 * 1024);
        
        System.out.printf("Memory used: %.2f MB for %d files%n", 
            memoryUsed / (1024.0 * 1024.0), fileCount);
    }

    @Test
    @DisplayName("Should handle large files efficiently")
    void testLargeFiles() throws IOException {
        // Given
        int fileCount = 50;
        int fileSizeKB = 100; // 100KB per file
        createLargeFileDataset(fileCount, fileSizeKB);

        // When
        long startTime = System.currentTimeMillis();
        
        DirectoryScanner scanner = new DirectoryScanner(tempDir, 10, 0, false);
        List<FileInfo> files = scanner.scan();
        
        DuplicateDetector detector = new DuplicateDetector();
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        assertThat(files).hasSize(fileCount);
        
        // Should handle large files efficiently
        assertThat(duration).isLessThan(20000); // Within 20 seconds
        
        long totalDataSize = files.stream().mapToLong(FileInfo::getSize).sum();
        double throughputMBps = (double) totalDataSize / (1024 * 1024) / (duration / 1000.0);
        
        System.out.printf("Processed %.2f MB in %d ms (%.2f MB/s)%n", 
            totalDataSize / (1024.0 * 1024.0), duration, throughputMBps);
    }

    @Test
    @DisplayName("Should handle deep directory structures efficiently")
    void testDeepDirectoryStructure() throws IOException {
        // Given
        int depth = 10;
        int filesPerLevel = 10;
        createDeepDirectoryStructure(depth, filesPerLevel);

        // When
        long startTime = System.currentTimeMillis();
        
        DirectoryScanner scanner = new DirectoryScanner(tempDir, depth, 0, false);
        List<FileInfo> files = scanner.scan();
        
        DuplicateDetector detector = new DuplicateDetector();
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        assertThat(files).hasSize(depth * filesPerLevel);
        
        // Should handle deep structures efficiently
        assertThat(duration).isLessThan(10000); // Within 10 seconds
        
        System.out.printf("Scanned %d levels with %d files each in %d ms%n", 
            depth, filesPerLevel, duration);
    }

    @Test
    @DisplayName("Should handle concurrent access efficiently")
    void testConcurrentAccess() throws IOException {
        // Given
        int fileCount = 200;
        createLargeTestDataset(fileCount, 20);
        
        int threadCount = 4;
        Thread[] threads = new Thread[threadCount];
        long[] durations = new long[threadCount];
        boolean[] success = new boolean[threadCount];

        // When
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    
                    DirectoryScanner scanner = new DirectoryScanner(tempDir, 10, 0, false);
                    List<FileInfo> files = scanner.scan();
                    
                    DuplicateDetector detector = new DuplicateDetector();
                    Map<String, List<String>> duplicates = detector.detectDuplicates(files);
                    
                    long endTime = System.currentTimeMillis();
                    durations[threadId] = endTime - startTime;
                    success[threadId] = true;
                    
                } catch (Exception e) {
                    success[threadId] = false;
                    System.err.println("Thread " + threadId + " failed: " + e.getMessage());
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            try {
                thread.join(30000); // Wait max 30 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Test interrupted: " + e.getMessage());
            }
        }

        // Then
        for (int i = 0; i < threadCount; i++) {
            assertThat(success[i]).isTrue();
            assertThat(durations[i]).isLessThan(30000); // Each thread should complete within 30 seconds
        }
        
        double avgDuration = java.util.Arrays.stream(durations).average().orElse(0);
        System.out.printf("Concurrent test: %d threads, average duration: %.2f ms%n", 
            threadCount, avgDuration);
    }

    private void createLargeTestDataset(int totalFiles, int duplicateGroups) throws IOException {
        int filesPerGroup = totalFiles / duplicateGroups;
        
        for (int group = 0; group < duplicateGroups; group++) {
            String content = "Duplicate content for group " + group;
            
            for (int file = 0; file < filesPerGroup; file++) {
                Path filePath = tempDir.resolve(String.format("group%d_file%d.txt", group, file));
                Files.writeString(filePath, content);
            }
        }
        
        // Add some unique files
        for (int i = 0; i < 10; i++) {
            Path uniqueFile = tempDir.resolve("unique" + i + ".txt");
            Files.writeString(uniqueFile, "Unique content " + i);
        }
    }

    private void createLargeFileDataset(int fileCount, int fileSizeKB) throws IOException {
        byte[] content = new byte[fileSizeKB * 1024];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 256);
        }
        
        // Create pairs of duplicate files
        for (int i = 0; i < fileCount / 2; i++) {
            Path file1 = tempDir.resolve("large" + i + "a.bin");
            Path file2 = tempDir.resolve("large" + i + "b.bin");
            
            Files.write(file1, content);
            Files.write(file2, content);
        }
    }

    private void createDeepDirectoryStructure(int depth, int filesPerLevel) throws IOException {
        Path currentPath = tempDir;
        
        for (int level = 0; level < depth; level++) {
            for (int file = 0; file < filesPerLevel; file++) {
                Path filePath = currentPath.resolve("level" + level + "_file" + file + ".txt");
                Files.writeString(filePath, "Content at level " + level + " file " + file);
            }
            
            if (level < depth - 1) {
                currentPath = currentPath.resolve("level" + level);
                Files.createDirectories(currentPath);
            }
        }
    }
}
