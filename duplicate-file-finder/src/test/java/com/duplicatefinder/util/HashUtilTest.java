package com.duplicatefinder.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@DisplayName("HashUtil Tests")
class HashUtilTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Clear cache before each test
        HashUtil.clearCache();
    }

    @Test
    @DisplayName("Should compute CRC32 for simple file")
    void testComputeCrc32SimpleFile() throws IOException {
        // Given
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello, World!");

        // When
        long crc32 = HashUtil.computeCrc32(testFile);

        // Then
        assertThat(crc32).isNotZero();
        assertThat(crc32).isPositive();
    }

    @Test
    @DisplayName("Should return same CRC32 for identical content")
    void testSameContentSameCrc32() throws IOException {
        // Given
        String content = "Test content for CRC32";
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        
        Files.writeString(file1, content);
        Files.writeString(file2, content);

        // When
        long crc1 = HashUtil.computeCrc32(file1);
        long crc2 = HashUtil.computeCrc32(file2);

        // Then
        assertThat(crc1).isEqualTo(crc2);
    }

    @Test
    @DisplayName("Should return different CRC32 for different content")
    void testDifferentContentDifferentCrc32() throws IOException {
        // Given
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        
        Files.writeString(file1, "Content A");
        Files.writeString(file2, "Content B");

        // When
        long crc1 = HashUtil.computeCrc32(file1);
        long crc2 = HashUtil.computeCrc32(file2);

        // Then
        assertThat(crc1).isNotEqualTo(crc2);
    }

    @Test
    @DisplayName("Should handle empty file")
    void testEmptyFile() throws IOException {
        // Given
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.createFile(emptyFile);

        // When
        long crc32 = HashUtil.computeCrc32(emptyFile);

        // Then
        assertThat(crc32).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle large file")
    void testLargeFile() throws IOException {
        // Given
        Path largeFile = tempDir.resolve("large.txt");
        byte[] largeContent = new byte[1024 * 1024]; // 1MB
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) (i % 256);
        }
        Files.write(largeFile, largeContent);

        // When
        long crc32 = HashUtil.computeCrc32(largeFile);

        // Then
        assertThat(crc32).isNotZero();
        assertThat(crc32).isPositive();
    }

    @Test
    @DisplayName("Should use cache for unchanged files")
    void testCacheUsage() throws IOException {
        // Given
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Cache test content");

        // When - first computation
        long startTime1 = System.nanoTime();
        long crc1 = HashUtil.computeCrc32(testFile);
        long endTime1 = System.nanoTime();

        // When - second computation (should use cache)
        long startTime2 = System.nanoTime();
        long crc2 = HashUtil.computeCrc32(testFile);
        long endTime2 = System.nanoTime();

        // Then
        assertThat(crc1).isEqualTo(crc2);
        assertThat(endTime2 - startTime2).isLessThan(endTime1 - startTime1);
    }

    @Test
    @DisplayName("Should invalidate cache when file is modified")
    void testCacheInvalidation() throws IOException {
        // Given
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Original content");

        // When
        long crc1 = HashUtil.computeCrc32(testFile);
        
        // Modify file
        try {
            Thread.sleep(1000); // Ensure different timestamp
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Continue test even if interrupted
        }
        Files.writeString(testFile, "Modified content");
        
        long crc2 = HashUtil.computeCrc32(testFile);

        // Then
        assertThat(crc1).isNotEqualTo(crc2);
    }

    @Test
    @DisplayName("Should handle files with binary content")
    void testBinaryContent() throws IOException {
        // Given
        Path binaryFile = tempDir.resolve("binary.bin");
        byte[] binaryData = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}; // PNG header
        Files.write(binaryFile, binaryData);

        // When
        long crc32 = HashUtil.computeCrc32(binaryFile);

        // Then
        assertThat(crc32).isNotZero();
        assertThat(crc32).isPositive();
    }

    @Test
    @DisplayName("Should handle files with Unicode content")
    void testUnicodeContent() throws IOException {
        // Given
        Path unicodeFile = tempDir.resolve("unicode.txt");
        String unicodeContent = "Привет мир! 🌍 Здравствуй мир! 🌎";
        Files.writeString(unicodeFile, unicodeContent);

        // When
        long crc32 = HashUtil.computeCrc32(unicodeFile);

        // Then
        assertThat(crc32).isNotZero();
        assertThat(crc32).isPositive();
    }

    @Test
    @DisplayName("Should throw exception for non-existent file")
    void testNonExistentFile() {
        // Given
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");

        // When & Then
        assertThatThrownBy(() -> HashUtil.computeCrc32(nonExistentFile))
            .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Should handle files with special characters in names")
    void testSpecialCharactersInNames() throws IOException {
        // Given
        Path specialFile = tempDir.resolve("file with spaces & symbols!@#$%^&().txt");
        Files.writeString(specialFile, "Special file content");

        // When
        long crc32 = HashUtil.computeCrc32(specialFile);

        // Then
        assertThat(crc32).isNotZero();
        assertThat(crc32).isPositive();
    }

    @Test
    @DisplayName("Should maintain cache size correctly")
    void testCacheSizeManagement() throws IOException {
        // Given
        int fileCount = 10;
        for (int i = 0; i < fileCount; i++) {
            Path file = tempDir.resolve("file" + i + ".txt");
            Files.writeString(file, "Content " + i);
            HashUtil.computeCrc32(file);
        }

        // When
        int cacheSize = HashUtil.getCacheSize();

        // Then
        assertThat(cacheSize).isEqualTo(fileCount);
    }

    @Test
    @DisplayName("Should clear cache correctly")
    void testClearCache() throws IOException {
        // Given
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Test content");
        HashUtil.computeCrc32(testFile);
        
        assertThat(HashUtil.getCacheSize()).isGreaterThan(0);

        // When
        HashUtil.clearCache();

        // Then
        assertThat(HashUtil.getCacheSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle concurrent access to cache")
    void testConcurrentCacheAccess() throws IOException {
        // Given
        int threadCount = 5;
        int filesPerThread = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < filesPerThread; j++) {
                        Path file = tempDir.resolve("thread" + threadId + "_file" + j + ".txt");
                        Files.writeString(file, "Content from thread " + threadId + " file " + j);
                        HashUtil.computeCrc32(file);
                    }
                } catch (IOException e) {
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
                thread.join(5000); // Wait max 5 seconds per thread
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Thread interrupted: " + e.getMessage());
            }
        }

        // Then
        assertThat(HashUtil.getCacheSize()).isEqualTo(threadCount * filesPerThread);
    }

    @Test
    @DisplayName("Should compute consistent CRC32 values")
    void testConsistentCrc32Values() throws IOException {
        // Given
        String content = "Consistent test content";
        Path testFile = tempDir.resolve("consistent.txt");
        Files.writeString(testFile, content);

        // When
        long crc1 = HashUtil.computeCrc32(testFile);
        long crc2 = HashUtil.computeCrc32(testFile);
        long crc3 = HashUtil.computeCrc32(testFile);

        // Then
        assertThat(crc1).isEqualTo(crc2).isEqualTo(crc3);
    }
}
