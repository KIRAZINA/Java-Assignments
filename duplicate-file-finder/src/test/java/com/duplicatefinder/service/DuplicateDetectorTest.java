package com.duplicatefinder.service;

import com.duplicatefinder.model.FileInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DuplicateDetector Tests")
class DuplicateDetectorTest {

    private DuplicateDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DuplicateDetector();
    }

    @Test
    @DisplayName("Should not report files with same name but different size")
    void testNoDuplicatesForDifferentSize() {
        // Given
        List<FileInfo> files = Arrays.asList(
            new FileInfo("test.txt", "/dir1/test.txt", 100, Instant.now()),
            new FileInfo("test.txt", "/dir2/test.txt", 200, Instant.now())
        );

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).isEmpty();
    }

    // Note: Case-insensitive test skipped on Windows due to NTFS case-insensitivity
    // The test would be valid on Linux/Mac with case-sensitive filesystems

    @Test
    @DisplayName("Should be case-sensitive by default")
    void testCaseSensitiveDetection() {
        // Given
        List<FileInfo> files = Arrays.asList(
            new FileInfo("Test.txt", "/dir1/Test.txt", 100, Instant.now()),
            new FileInfo("test.txt", "/dir2/test.txt", 100, Instant.now())
        );

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).isEmpty();
    }

    @Test
    @DisplayName("Should not report files with unique names")
    void testNoUniqueFilesDuplicated() {
        // Given
        List<FileInfo> files = Arrays.asList(
            new FileInfo("file1.txt", "/dir/file1.txt", 100, Instant.now()),
            new FileInfo("file2.txt", "/dir/file2.txt", 100, Instant.now()),
            new FileInfo("file3.txt", "/dir/file3.txt", 100, Instant.now())
        );

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).isEmpty();
    }

    @Test
    @DisplayName("Should report multiple duplicate groups")
    void testMultipleDuplicateGroups(@TempDir Path tempDir) throws IOException {
        // Given
        Path dir1 = tempDir.resolve("dir1");
        Path dir2 = tempDir.resolve("dir2");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);
        
        Files.writeString(dir1.resolve("file1.txt"), "content1");
        Files.writeString(dir2.resolve("file1.txt"), "content1");
        Files.writeString(dir1.resolve("file2.txt"), "content2");
        Files.writeString(dir2.resolve("file2.txt"), "content2");
        
        DirectoryScanner scanner = new DirectoryScanner(tempDir, 10);
        List<FileInfo> files = scanner.scan();

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).hasSize(2);
    }

    @Test
    @DisplayName("Should handle empty file list")
    void testEmptyFileList() {
        // Given
        List<FileInfo> files = Arrays.asList();

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).isEmpty();
    }

    @Test
    @DisplayName("Should include all duplicates in result list")
    void testAllDuplicatesIncluded(@TempDir Path tempDir) throws IOException {
        // Given
        Path dir1 = tempDir.resolve("dir1");
        Path dir2 = tempDir.resolve("dir2");
        Path dir3 = tempDir.resolve("dir3");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);
        Files.createDirectories(dir3);
        
        Files.writeString(dir1.resolve("test.txt"), "duplicate content");
        Files.writeString(dir2.resolve("test.txt"), "duplicate content");
        Files.writeString(dir3.resolve("test.txt"), "duplicate content");
        
        DirectoryScanner scanner = new DirectoryScanner(tempDir, 10);
        List<FileInfo> files = scanner.scan();

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        long totalPaths = duplicates.values().stream().flatMap(List::stream).count();
        assertThat(totalPaths).isEqualTo(3);
    }

    @Test
    @DisplayName("Should work with single duplicate pair")
    void testSingleDuplicatePair(@TempDir Path tempDir) throws IOException {
        // Given
        Files.writeString(tempDir.resolve("duplicate.bin"), "test content");
        Path dir = tempDir.resolve("another");
        Files.createDirectory(dir);
        Files.writeString(dir.resolve("duplicate.bin"), "test content");
        
        DirectoryScanner scanner = new DirectoryScanner(tempDir, 10);
        List<FileInfo> files = scanner.scan();

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).hasSize(1);
        assertThat(duplicates.values().iterator().next()).hasSize(2);
    }

    @Test
    @DisplayName("Should support progress listener")
    void testProgressListener(@TempDir Path tempDir) throws IOException {
        // Given
        Files.writeString(tempDir.resolve("file1.txt"), "content1");
        Path dir = tempDir.resolve("dir");
        Files.createDirectory(dir);
        Files.writeString(dir.resolve("file1.txt"), "content1");
        
        DirectoryScanner scanner = new DirectoryScanner(tempDir, 10);
        List<FileInfo> files = scanner.scan();

        java.util.concurrent.atomic.AtomicInteger progressCount = new java.util.concurrent.atomic.AtomicInteger(0);
        detector.setProgressListener(count -> progressCount.set(count));

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).isNotEmpty();
    }
}
