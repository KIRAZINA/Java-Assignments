package com.duplicatefinder.service;

import com.duplicatefinder.model.FileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DirectoryScanner Extended Tests")
class DirectoryScannerExtendedTest {

    @TempDir
    Path tempDir;

    private DirectoryScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new DirectoryScanner(tempDir, 10, 0, false);
    }

    @Test
    @DisplayName("Should scan empty directory")
    void testScanEmptyDirectory() throws IOException {
        // When
        List<FileInfo> files = scanner.scan();

        // Then
        assertThat(files).isEmpty();
    }

    @Test
    @DisplayName("Should scan directory with single file")
    void testScanSingleFile() throws IOException {
        // Given
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "test content");

        // When
        List<FileInfo> files = scanner.scan();

        // Then
        assertThat(files).hasSize(1);
        FileInfo fileInfo = files.get(0);
        assertThat(fileInfo.getName()).isEqualTo("test.txt");
        assertThat(fileInfo.getSize()).isEqualTo(12); // "test content".length()
        assertThat(fileInfo.getPath()).contains("test.txt");
    }

    @Test
    @DisplayName("Should scan directory with multiple files")
    void testScanMultipleFiles() throws IOException {
        // Given
        Files.writeString(tempDir.resolve("file1.txt"), "content1");
        Files.writeString(tempDir.resolve("file2.txt"), "content2");
        Files.createDirectory(tempDir.resolve("subdir"));
        Files.writeString(tempDir.resolve("subdir/file3.txt"), "content3");

        // When
        List<FileInfo> files = scanner.scan();

        // Then
        assertThat(files).hasSize(3);
        assertThat(files.stream().map(FileInfo::getName))
            .containsExactlyInAnyOrder("file1.txt", "file2.txt", "file3.txt");
    }

    @Test
    @DisplayName("Should respect max depth parameter")
    void testMaxDepth() throws IOException {
        // Given
        Files.createDirectory(tempDir.resolve("level1"));
        Files.createDirectory(tempDir.resolve("level1/level2"));
        Files.createDirectory(tempDir.resolve("level1/level2/level3"));
        Files.writeString(tempDir.resolve("level1/file1.txt"), "content1");
        Files.writeString(tempDir.resolve("level1/level2/file2.txt"), "content2");
        Files.writeString(tempDir.resolve("level1/level2/level3/file3.txt"), "content3");

        DirectoryScanner shallowScanner = new DirectoryScanner(tempDir, 2, 0, false);

        // When
        List<FileInfo> files = shallowScanner.scan();

        // Then
        assertThat(files).hasSize(2); // Only files up to depth 2
        assertThat(files.stream().map(FileInfo::getName))
            .containsExactlyInAnyOrder("file1.txt", "file2.txt");
    }

    @Test
    @DisplayName("Should filter by minimum file size")
    void testMinSizeFilter() throws IOException {
        // Given
        Files.writeString(tempDir.resolve("small.txt"), "small"); // 5 bytes
        Files.writeString(tempDir.resolve("large.txt"), "large content"); // 13 bytes

        DirectoryScanner sizeScanner = new DirectoryScanner(tempDir, 10, 10, false);

        // When
        List<FileInfo> files = sizeScanner.scan();

        // Then
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getName()).isEqualTo("large.txt");
        assertThat(files.get(0).getSize()).isGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("Should handle symbolic links when followSymlinks is false")
    void testSymbolicLinksNotFollowed() throws IOException {
        // Given
        Path targetFile = tempDir.resolve("target.txt");
        Path linkFile = tempDir.resolve("link.txt");
        
        Files.writeString(targetFile, "target content");
        try {
            Files.createSymbolicLink(linkFile, targetFile);
        } catch (UnsupportedOperationException e) {
            // Skip test if symbolic links are not supported
            return;
        }

        // When
        List<FileInfo> files = scanner.scan();

        // Then
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getName()).isEqualTo("target.txt");
    }

    @Test
    @DisplayName("Should call progress listener during scan")
    void testProgressListener() throws IOException {
        // Given
        Files.writeString(tempDir.resolve("file1.txt"), "content1");
        Files.writeString(tempDir.resolve("file2.txt"), "content2");
        
        AtomicInteger progressCount = new AtomicInteger(0);
        scanner.setProgressListener(progressCount::addAndGet);

        // When
        List<FileInfo> files = scanner.scan();

        // Then
        assertThat(files).hasSize(2);
        assertThat(progressCount.get()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should throw exception for non-existent directory")
    void testNonExistentDirectory() {
        // Given
        DirectoryScanner badScanner = new DirectoryScanner(Path.of("/non/existent/path"), 10);

        // When & Then
        assertThatThrownBy(badScanner::scan)
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Directory does not exist");
    }

    @Test
    @DisplayName("Should throw exception for file instead of directory")
    void testFileInsteadOfDirectory() throws IOException {
        // Given
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "content");
        
        DirectoryScanner fileScanner = new DirectoryScanner(testFile, 10);

        // When & Then
        assertThatThrownBy(fileScanner::scan)
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Path is not a directory");
    }

    @Test
    @DisplayName("Should handle files with special characters in names")
    void testSpecialCharactersInNames() throws IOException {
        // Given
        String[] specialNames = {"file with spaces.txt", "file-with-dashes.txt", "file_with_underscores.txt", "файл.txt"};
        
        for (String name : specialNames) {
            Files.writeString(tempDir.resolve(name), "content");
        }

        // When
        List<FileInfo> files = scanner.scan();

        // Then
        assertThat(files).hasSize(specialNames.length);
        assertThat(files.stream().map(FileInfo::getName))
            .containsExactlyInAnyOrder(specialNames);
    }

    @Test
    @DisplayName("Should handle zero-sized files")
    void testZeroSizedFiles() throws IOException {
        // Given
        Files.createFile(tempDir.resolve("empty.txt"));

        // When
        List<FileInfo> files = scanner.scan();

        // Then
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getSize()).isZero();
        assertThat(files.get(0).getName()).isEqualTo("empty.txt");
    }

    @Test
    @DisplayName("Should handle large files")
    void testLargeFiles() throws IOException {
        // Given
        Path largeFile = tempDir.resolve("large.txt");
        byte[] largeContent = new byte[1024 * 1024]; // 1MB
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) (i % 256);
        }
        Files.write(largeFile, largeContent);

        // When
        List<FileInfo> files = scanner.scan();

        // Then
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getSize()).isEqualTo(1024 * 1024);
        assertThat(files.get(0).getName()).isEqualTo("large.txt");
    }

    @Test
    @DisplayName("Should handle directory with many files efficiently")
    void testManyFiles() throws IOException {
        // Given
        int fileCount = 100;
        for (int i = 0; i < fileCount; i++) {
            Files.writeString(tempDir.resolve("file" + i + ".txt"), "content " + i);
        }

        // When
        long startTime = System.currentTimeMillis();
        List<FileInfo> files = scanner.scan();
        long endTime = System.currentTimeMillis();

        // Then
        assertThat(files).hasSize(fileCount);
        assertThat(endTime - startTime).isLessThan(5000); // Should complete within 5 seconds
    }
}
