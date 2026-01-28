package com.duplicatefinder.service;

import com.duplicatefinder.model.FileInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DirectoryScanner Tests")
class DirectoryScannerTest {

    @Test
    @DisplayName("Should scan directory and find all files")
    void testScanDirectory(@TempDir Path tempDir) throws IOException {
        // Given
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");

        DirectoryScanner scanner = new DirectoryScanner(tempDir, 10);

        // When
        List<FileInfo> files = scanner.scan();

        // Then
        assertThat(files)
            .hasSize(2)
            .extracting(FileInfo::getName)
            .containsExactlyInAnyOrder("file1.txt", "file2.txt");
    }

    @Test
    @DisplayName("Should handle empty directory")
    void testScanEmptyDirectory(@TempDir Path tempDir) throws IOException {
        // Given
        DirectoryScanner scanner = new DirectoryScanner(tempDir, 10);

        // When
        List<FileInfo> files = scanner.scan();

        // Then
        assertThat(files).isEmpty();
    }

    @Test
    @DisplayName("Should scan subdirectories")
    void testScanNestedDirectory(@TempDir Path tempDir) throws IOException {
        // Given
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        Files.writeString(tempDir.resolve("file1.txt"), "content1");
        Files.writeString(subDir.resolve("file2.txt"), "content2");

        DirectoryScanner scanner = new DirectoryScanner(tempDir, 10);

        // When
        List<FileInfo> files = scanner.scan();

        // Then
        assertThat(files)
            .hasSize(2)
            .extracting(FileInfo::getName)
            .containsExactlyInAnyOrder("file1.txt", "file2.txt");
    }

    @Test
    @DisplayName("Should respect max depth")
    void testMaxDepth(@TempDir Path tempDir) throws IOException {
        // Given
        Path level1 = tempDir.resolve("level1");
        Path level2 = level1.resolve("level2");
        Files.createDirectory(level1);
        Files.createDirectory(level2);
        Files.writeString(tempDir.resolve("file0.txt"), "content0");
        Files.writeString(level1.resolve("file1.txt"), "content1");
        Files.writeString(level2.resolve("file2.txt"), "content2");

        DirectoryScanner scanner = new DirectoryScanner(tempDir, 3);

        // When
        List<FileInfo> files = scanner.scan();

        // Then
        assertThat(files)
            .hasSize(3)
            .extracting(FileInfo::getName)
            .containsExactlyInAnyOrder("file0.txt", "file1.txt", "file2.txt");
    }

    @Test
    @DisplayName("Should filter files by minimum size")
    void testMinSizeFilter(@TempDir Path tempDir) throws IOException {
        // Given
        Files.writeString(tempDir.resolve("small.txt"), "a");
        Files.writeString(tempDir.resolve("large.txt"), "this is a large content");

        DirectoryScanner scanner = new DirectoryScanner(tempDir, 10, 5, false);

        // When
        List<FileInfo> files = scanner.scan();

        // Then
        assertThat(files)
            .hasSize(1)
            .extracting(FileInfo::getName)
            .containsExactly("large.txt");
    }

    @Test
    @DisplayName("Should throw IOException for non-existent directory")
    void testNonExistentDirectory() {
        // Given
        Path nonExistentPath = Path.of("/this/path/does/not/exist");
        DirectoryScanner scanner = new DirectoryScanner(nonExistentPath, 10);

        // When & Then
        assertThatThrownBy(scanner::scan)
            .isInstanceOf(IOException.class)
            .hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("Should throw IOException for file path")
    void testFilePath(@TempDir Path tempDir) throws IOException {
        // Given
        Path filePath = tempDir.resolve("file.txt");
        Files.writeString(filePath, "content");
        DirectoryScanner scanner = new DirectoryScanner(filePath, 10);

        // When & Then
        assertThatThrownBy(scanner::scan)
            .isInstanceOf(IOException.class)
            .hasMessageContaining("not a directory");
    }

    @Test
    @DisplayName("Should store correct file size")
    void testFileSize(@TempDir Path tempDir) throws IOException {
        // Given
        String content = "1234567890";
        Files.writeString(tempDir.resolve("file.txt"), content);
        DirectoryScanner scanner = new DirectoryScanner(tempDir, 10);

        // When
        List<FileInfo> files = scanner.scan();

        // Then
        assertThat(files)
            .hasSize(1)
            .first()
            .extracting(FileInfo::getSize)
            .isEqualTo((long) content.length());
    }

    @Test
    @DisplayName("Should support progress listener")
    void testProgressListener(@TempDir Path tempDir) throws IOException {
        // Given
        Files.writeString(tempDir.resolve("file1.txt"), "content1");
        Files.writeString(tempDir.resolve("file2.txt"), "content2");

        DirectoryScanner scanner = new DirectoryScanner(tempDir, 10);
        java.util.concurrent.atomic.AtomicInteger progressCount = new java.util.concurrent.atomic.AtomicInteger(0);
        scanner.setProgressListener(count -> progressCount.set(count));

        // When
        List<FileInfo> files = scanner.scan();

        // Then
        assertThat(files).hasSize(2);
        assertThat(progressCount.get()).isEqualTo(2);
    }
}
