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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DuplicateDetector Extended Tests")
class DuplicateDetectorExtendedTest {

    @TempDir
    Path tempDir;

    private DuplicateDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DuplicateDetector();
    }

    @Test
    @DisplayName("Should detect duplicates with case-sensitive file names")
    void testCaseSensitiveFileNames() throws IOException {
        // Given
        List<FileInfo> files = createTestFiles("test.txt", "Test.txt", "test.txt");
        // Create files with same content for duplicates
        Files.writeString(tempDir.resolve("test1.txt"), "same content");
        Files.writeString(tempDir.resolve("test2.txt"), "same content");
        Files.writeString(tempDir.resolve("different.txt"), "different content");

        List<FileInfo> realFiles = List.of(
            createFileInfo("test1.txt", tempDir.resolve("test1.txt").toString(), 12, Instant.now()),
            createFileInfo("test2.txt", tempDir.resolve("test2.txt").toString(), 12, Instant.now()),
            createFileInfo("different.txt", tempDir.resolve("different.txt").toString(), 15, Instant.now())
        );

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(realFiles);

        // Then
        assertThat(duplicates).hasSize(1);
        assertThat(duplicates.values().iterator().next()).hasSize(2);
    }

    @Test
    @DisplayName("Should detect duplicates with case-insensitive file names")
    void testCaseInsensitiveFileNames() throws IOException {
        // Given
        Files.writeString(tempDir.resolve("test1.txt"), "same content");
        Files.writeString(tempDir.resolve("TEST2.txt"), "same content");
        Files.writeString(tempDir.resolve("different.txt"), "different content");

        List<FileInfo> files = List.of(
            createFileInfo("test1.txt", tempDir.resolve("test1.txt").toString(), 12, Instant.now()),
            createFileInfo("TEST2.txt", tempDir.resolve("TEST2.txt").toString(), 12, Instant.now()),
            createFileInfo("different.txt", tempDir.resolve("different.txt").toString(), 15, Instant.now())
        );

        detector.setCaseInsensitive(true);

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).hasSize(1);
        assertThat(duplicates.values().iterator().next()).hasSize(2);
    }

    @Test
    @DisplayName("Should handle empty file list")
    void testEmptyFileList() {
        // Given
        List<FileInfo> files = new ArrayList<>();

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).isEmpty();
    }

    @Test
    @DisplayName("Should handle single file")
    void testSingleFile() throws IOException {
        // Given
        Files.writeString(tempDir.resolve("single.txt"), "content");
        List<FileInfo> files = List.of(
            createFileInfo("single.txt", tempDir.resolve("single.txt").toString(), 7, Instant.now())
        );

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).isEmpty();
    }

    @Test
    @DisplayName("Should handle files with same name but different sizes")
    void testSameNameDifferentSizes() throws IOException {
        // Given
        Files.writeString(tempDir.resolve("file1.txt"), "short");
        Files.writeString(tempDir.resolve("file2.txt"), "much longer content");

        List<FileInfo> files = List.of(
            createFileInfo("file.txt", tempDir.resolve("file1.txt").toString(), 5, Instant.now()),
            createFileInfo("file.txt", tempDir.resolve("file2.txt").toString(), 19, Instant.now())
        );

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).isEmpty();
    }

    @Test
    @DisplayName("Should handle files with same name and size but different content")
    void testSameNameAndSizeDifferentContent() throws IOException {
        // Given
        Files.writeString(tempDir.resolve("file1.txt"), "content1");
        Files.writeString(tempDir.resolve("file2.txt"), "content2");

        List<FileInfo> files = List.of(
            createFileInfo("file.txt", tempDir.resolve("file1.txt").toString(), 8, Instant.now()),
            createFileInfo("file.txt", tempDir.resolve("file2.txt").toString(), 8, Instant.now())
        );

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).isEmpty();
    }

    @Test
    @DisplayName("Should handle multiple duplicate groups")
    void testMultipleDuplicateGroups() throws IOException {
        // Given
        Files.writeString(tempDir.resolve("group1a.txt"), "content1");
        Files.writeString(tempDir.resolve("group1b.txt"), "content1");
        Files.writeString(tempDir.resolve("group2a.txt"), "content2");
        Files.writeString(tempDir.resolve("group2b.txt"), "content2");
        Files.writeString(tempDir.resolve("group2c.txt"), "content2");
        Files.writeString(tempDir.resolve("unique.txt"), "unique");

        List<FileInfo> files = List.of(
            createFileInfo("group1.txt", tempDir.resolve("group1a.txt").toString(), 8, Instant.now()),
            createFileInfo("group1.txt", tempDir.resolve("group1b.txt").toString(), 8, Instant.now()),
            createFileInfo("group2.txt", tempDir.resolve("group2a.txt").toString(), 8, Instant.now()),
            createFileInfo("group2.txt", tempDir.resolve("group2b.txt").toString(), 8, Instant.now()),
            createFileInfo("group2.txt", tempDir.resolve("group2c.txt").toString(), 8, Instant.now()),
            createFileInfo("unique.txt", tempDir.resolve("unique.txt").toString(), 6, Instant.now())
        );

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).hasSize(2);
        
        // Check first group (2 files)
        boolean foundGroup1 = false;
        boolean foundGroup2 = false;
        
        for (List<String> group : duplicates.values()) {
            if (group.size() == 2) {
                foundGroup1 = true;
            } else if (group.size() == 3) {
                foundGroup2 = true;
            }
        }
        
        assertThat(foundGroup1).isTrue();
        assertThat(foundGroup2).isTrue();
    }

    @Test
    @DisplayName("Should call progress listener during processing")
    void testProgressListener() throws IOException {
        // Given
        Files.writeString(tempDir.resolve("file1.txt"), "content1");
        Files.writeString(tempDir.resolve("file2.txt"), "content1");
        Files.writeString(tempDir.resolve("file3.txt"), "content2");

        List<FileInfo> files = List.of(
            createFileInfo("file.txt", tempDir.resolve("file1.txt").toString(), 8, Instant.now()),
            createFileInfo("file.txt", tempDir.resolve("file2.txt").toString(), 8, Instant.now()),
            createFileInfo("file.txt", tempDir.resolve("file3.txt").toString(), 8, Instant.now())
        );

        AtomicInteger progressCount = new AtomicInteger(0);
        detector.setProgressListener(count -> progressCount.addAndGet(count));

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).hasSize(1);
        assertThat(progressCount.get()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should handle zero-sized files")
    void testZeroSizedFiles() throws IOException {
        // Given
        Files.createFile(tempDir.resolve("empty1.txt"));
        Files.createFile(tempDir.resolve("empty2.txt"));

        List<FileInfo> files = List.of(
            createFileInfo("empty.txt", tempDir.resolve("empty1.txt").toString(), 0, Instant.now()),
            createFileInfo("empty.txt", tempDir.resolve("empty2.txt").toString(), 0, Instant.now())
        );

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).hasSize(1);
        assertThat(duplicates.values().iterator().next()).hasSize(2);
    }

    @Test
    @DisplayName("Should handle large number of files efficiently")
    void testLargeNumberOfFiles() throws IOException {
        // Given
        int fileCount = 50;
        List<FileInfo> files = new ArrayList<>();
        
        // Create pairs of duplicate files
        for (int i = 0; i < fileCount / 2; i++) {
            String content = "content" + i;
            Files.writeString(tempDir.resolve("file" + i + "a.txt"), content);
            Files.writeString(tempDir.resolve("file" + i + "b.txt"), content);
            
            files.add(createFileInfo("file" + i + ".txt", tempDir.resolve("file" + i + "a.txt").toString(), content.length(), Instant.now()));
            files.add(createFileInfo("file" + i + ".txt", tempDir.resolve("file" + i + "b.txt").toString(), content.length(), Instant.now()));
        }

        // When
        long startTime = System.currentTimeMillis();
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);
        long endTime = System.currentTimeMillis();

        // Then
        assertThat(duplicates).hasSize(fileCount / 2);
        assertThat(endTime - startTime).isLessThan(10000); // Should complete within 10 seconds
        
        // Each duplicate group should have exactly 2 files
        for (List<String> group : duplicates.values()) {
            assertThat(group).hasSize(2);
        }
    }

    @Test
    @DisplayName("Should handle files with special characters in paths")
    void testSpecialCharactersInPaths() throws IOException {
        // Given
        Files.writeString(tempDir.resolve("file with spaces.txt"), "content");
        Files.writeString(tempDir.resolve("file-with-dashes.txt"), "content");
        Files.writeString(tempDir.resolve("file_with_underscores.txt"), "content");

        List<FileInfo> files = List.of(
            createFileInfo("file.txt", tempDir.resolve("file with spaces.txt").toString(), 7, Instant.now()),
            createFileInfo("file.txt", tempDir.resolve("file-with-dashes.txt").toString(), 7, Instant.now()),
            createFileInfo("file.txt", tempDir.resolve("file_with_underscores.txt").toString(), 7, Instant.now())
        );

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).hasSize(1);
        assertThat(duplicates.values().iterator().next()).hasSize(3);
    }

    @Test
    @DisplayName("Should handle files with identical content but different timestamps")
    void testDifferentTimestamps() throws IOException {
        // Given
        Files.writeString(tempDir.resolve("file1.txt"), "same content");
        Files.writeString(tempDir.resolve("file2.txt"), "same content");

        Instant time1 = Instant.now().minusSeconds(3600); // 1 hour ago
        Instant time2 = Instant.now(); // now

        List<FileInfo> files = List.of(
            createFileInfo("file.txt", tempDir.resolve("file1.txt").toString(), 12, time1),
            createFileInfo("file.txt", tempDir.resolve("file2.txt").toString(), 12, time2)
        );

        // When
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).hasSize(1);
        assertThat(duplicates.values().iterator().next()).hasSize(2);
    }

    private List<FileInfo> createTestFiles(String... names) {
        List<FileInfo> files = new ArrayList<>();
        for (String name : names) {
            files.add(createFileInfo(name, tempDir.resolve(name).toString(), name.length(), Instant.now()));
        }
        return files;
    }

    private FileInfo createFileInfo(String name, String path, long size, Instant lastModified) {
        FileInfo fileInfo = new FileInfo(name, path, size, lastModified);
        return fileInfo;
    }
}
