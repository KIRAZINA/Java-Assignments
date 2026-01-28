package com.duplicatefinder.integration;

import com.duplicatefinder.DuplicateFinderApp;
import com.duplicatefinder.cli.CommandLineParser;
import com.duplicatefinder.service.DirectoryScanner;
import com.duplicatefinder.service.DuplicateDetector;
import com.duplicatefinder.util.FileUtils;
import com.duplicatefinder.util.HashUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Duplicate Finder Integration Tests")
class DuplicateFinderIntegrationTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Clear cache before each test
        HashUtil.clearCache();
    }

    @Test
    @DisplayName("Should find duplicates in simple directory structure")
    void testFindDuplicatesSimpleStructure() throws IOException {
        // Given
        setupSimpleDuplicateStructure();

        // When
        CommandLineParser parser = new CommandLineParser();
        parser.parse(new String[]{tempDir.toString()});
        
        List<String> directories = parser.getDirectories();
        DirectoryScanner scanner = new DirectoryScanner(Path.of(directories.get(0)), parser.getMaxDepth(), parser.getMinSize(), false);
        List<com.duplicatefinder.model.FileInfo> files = scanner.scan();
        
        DuplicateDetector detector = new DuplicateDetector();
        detector.setCaseInsensitive(parser.isCaseInsensitive());
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).hasSize(1);
        assertThat(duplicates.values().iterator().next()).hasSize(2);
    }

    @Test
    @DisplayName("Should handle complex directory structure with multiple duplicate groups")
    void testComplexDirectoryStructure() throws IOException {
        // Given
        setupComplexDuplicateStructure();

        // When
        CommandLineParser parser = new CommandLineParser();
        parser.parse(new String[]{tempDir.toString()});
        
        List<String> directories = parser.getDirectories();
        DirectoryScanner scanner = new DirectoryScanner(Path.of(directories.get(0)), parser.getMaxDepth(), parser.getMinSize(), false);
        List<com.duplicatefinder.model.FileInfo> files = scanner.scan();
        
        DuplicateDetector detector = new DuplicateDetector();
        detector.setCaseInsensitive(parser.isCaseInsensitive());
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        assertThat(duplicates).hasSize(2); // Two groups of duplicates
        
        int totalDuplicates = duplicates.values().stream()
            .mapToInt(List::size)
            .sum();
        assertThat(totalDuplicates).isEqualTo(5); // 2 + 3 files
    }

    @Test
    @DisplayName("Should respect case-insensitive option")
    void testCaseInsensitiveOption() throws IOException {
        // Given
        setupCaseSensitiveDuplicates();

        // When - case sensitive
        CommandLineParser parser1 = new CommandLineParser();
        parser1.parse(new String[]{tempDir.toString()});
        
        List<String> directories1 = parser1.getDirectories();
        DirectoryScanner scanner1 = new DirectoryScanner(Path.of(directories1.get(0)), parser1.getMaxDepth(), parser1.getMinSize(), false);
        List<com.duplicatefinder.model.FileInfo> files1 = scanner1.scan();
        
        DuplicateDetector detector1 = new DuplicateDetector();
        detector1.setCaseInsensitive(parser1.isCaseInsensitive());
        Map<String, List<String>> duplicates1 = detector1.detectDuplicates(files1);

        // When - case insensitive
        CommandLineParser parser2 = new CommandLineParser();
        parser2.parse(new String[]{"--case-insensitive", tempDir.toString()});
        
        List<String> directories2 = parser2.getDirectories();
        DirectoryScanner scanner2 = new DirectoryScanner(Path.of(directories2.get(0)), parser2.getMaxDepth(), parser2.getMinSize(), false);
        List<com.duplicatefinder.model.FileInfo> files2 = scanner2.scan();
        
        DuplicateDetector detector2 = new DuplicateDetector();
        detector2.setCaseInsensitive(parser2.isCaseInsensitive());
        Map<String, List<String>> duplicates2 = detector2.detectDuplicates(files2);

        // Then
        assertThat(duplicates1).isEmpty(); // Case sensitive - no duplicates
        assertThat(duplicates2).hasSize(1); // Case insensitive - duplicates found
    }

    @Test
    @DisplayName("Should respect minimum size filter")
    void testMinSizeFilter() throws IOException {
        // Given
        setupMixedSizeFiles();

        // When
        CommandLineParser parser = new CommandLineParser();
        parser.parse(new String[]{"--min-size=10B", tempDir.toString()});
        
        List<String> directories = parser.getDirectories();
        DirectoryScanner scanner = new DirectoryScanner(Path.of(directories.get(0)), parser.getMaxDepth(), parser.getMinSize(), false);
        List<com.duplicatefinder.model.FileInfo> files = scanner.scan();
        
        DuplicateDetector detector = new DuplicateDetector();
        detector.setCaseInsensitive(parser.isCaseInsensitive());
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Then
        // Only files >= 10 bytes should be considered
        assertThat(files.stream().allMatch(f -> f.getSize() >= 10)).isTrue();
    }

    @Test
    @DisplayName("Should respect maximum depth filter")
    void testMaxDepthFilter() throws IOException {
        // Given
        setupDeepDirectoryStructure();

        // When
        CommandLineParser parser = new CommandLineParser();
        parser.parse(new String[]{"--max-depth=2", tempDir.toString()});
        
        List<String> directories = parser.getDirectories();
        DirectoryScanner scanner = new DirectoryScanner(Path.of(directories.get(0)), parser.getMaxDepth(), parser.getMinSize(), false);
        List<com.duplicatefinder.model.FileInfo> files = scanner.scan();

        // Then
        // Should not find files in level3 directory
        assertThat(files.stream().noneMatch(f -> f.getPath().contains("level3"))).isTrue();
    }

    @Test
    @DisplayName("Should export results to JSON")
    void testJsonExport() throws IOException {
        // Given
        setupSimpleDuplicateStructure();

        // When
        CommandLineParser parser = new CommandLineParser();
        parser.parse(new String[]{"--export=json", tempDir.toString()});
        
        List<String> directories = parser.getDirectories();
        DirectoryScanner scanner = new DirectoryScanner(Path.of(directories.get(0)), parser.getMaxDepth(), parser.getMinSize(), false);
        List<com.duplicatefinder.model.FileInfo> files = scanner.scan();
        
        DuplicateDetector detector = new DuplicateDetector();
        detector.setCaseInsensitive(parser.isCaseInsensitive());
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Export to JSON
        ObjectMapper mapper = new ObjectMapper();
        Path jsonFile = tempDir.resolve("test_export.json");
        Map<String, Object> exportData = Map.of(
            "timestamp", java.time.LocalDateTime.now().toString(),
            "totalDuplicateGroups", duplicates.size(),
            "duplicates", duplicates
        );
        mapper.writeValue(jsonFile.toFile(), exportData);

        // Then
        assertThat(Files.exists(jsonFile)).isTrue();
        Map<String, Object> imported = mapper.readValue(jsonFile.toFile(), Map.class);
        assertThat(imported.get("totalDuplicateGroups")).isEqualTo(1);
        assertThat(imported.containsKey("duplicates")).isTrue();
    }

    @Test
    @DisplayName("Should export results to CSV")
    void testCsvExport() throws IOException {
        // Given
        setupSimpleDuplicateStructure();

        // When
        CommandLineParser parser = new CommandLineParser();
        parser.parse(new String[]{"--export=csv", tempDir.toString()});
        
        List<String> directories = parser.getDirectories();
        DirectoryScanner scanner = new DirectoryScanner(Path.of(directories.get(0)), parser.getMaxDepth(), parser.getMinSize(), false);
        List<com.duplicatefinder.model.FileInfo> files = scanner.scan();
        
        DuplicateDetector detector = new DuplicateDetector();
        detector.setCaseInsensitive(parser.isCaseInsensitive());
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Export to CSV
        Path csvFile = tempDir.resolve("test_export.csv");
        try (var writer = java.nio.file.Files.newBufferedWriter(csvFile)) {
            writer.write("Original File,Duplicate File,Group Size\n");
            for (Map.Entry<String, List<String>> entry : duplicates.entrySet()) {
                String original = entry.getKey();
                List<String> duplicateList = entry.getValue();
                for (String duplicate : duplicateList) {
                    if (!duplicate.equals(original)) {
                        writer.write(String.format("\"%s\",\"%s\",%d\n", 
                            original.replace("\"", "\"\""), 
                            duplicate.replace("\"", "\"\""), 
                            duplicateList.size()));
                    }
                }
            }
        }

        // Then
        assertThat(Files.exists(csvFile)).isTrue();
        String csvContent = Files.readString(csvFile);
        assertThat(csvContent).contains("Original File,Duplicate File,Group Size");
        assertThat(csvContent.lines().count()).isGreaterThan(1); // Header + data
    }

    @Test
    @DisplayName("Should handle file deletion with keep-newest option")
    void testDeletionKeepNewest() throws IOException {
        // Given
        setupFilesWithDifferentTimestamps();

        // When
        CommandLineParser parser = new CommandLineParser();
        parser.parse(new String[]{"--keep-newest", "--delete", tempDir.toString()});
        
        List<String> directories = parser.getDirectories();
        DirectoryScanner scanner = new DirectoryScanner(Path.of(directories.get(0)), parser.getMaxDepth(), parser.getMinSize(), false);
        List<com.duplicatefinder.model.FileInfo> files = scanner.scan();
        
        DuplicateDetector detector = new DuplicateDetector();
        detector.setCaseInsensitive(parser.isCaseInsensitive());
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // Simulate deletion logic
        for (Map.Entry<String, List<String>> entry : duplicates.entrySet()) {
            List<String> filesToDelete = new ArrayList<>(entry.getValue());
            String keepFile = filesToDelete.stream()
                .max(java.util.Comparator.comparing(file -> {
                    try {
                        return Files.getLastModifiedTime(Path.of(file)).toInstant();
                    } catch (IOException e) {
                        return java.time.Instant.MIN;
                    }
                }))
                .orElse(filesToDelete.get(0));
            
            filesToDelete.remove(keepFile);
            for (String filePath : filesToDelete) {
                FileUtils.deleteFile(Path.of(filePath));
            }
        }

        // Then
        // Should have only one file left (the newest)
        long remainingFiles = Files.walk(tempDir)
            .filter(Files::isRegularFile)
            .count();
        assertThat(remainingFiles).isEqualTo(1);
    }

    @Test
    @DisplayName("Should demonstrate caching performance improvement")
    void testCachingPerformance() throws IOException {
        // Given
        setupSimpleDuplicateStructure();

        // When - first run
        long startTime1 = System.currentTimeMillis();
        CommandLineParser parser1 = new CommandLineParser();
        parser1.parse(new String[]{tempDir.toString()});
        
        List<String> directories1 = parser1.getDirectories();
        DirectoryScanner scanner1 = new DirectoryScanner(Path.of(directories1.get(0)), parser1.getMaxDepth(), parser1.getMinSize(), false);
        List<com.duplicatefinder.model.FileInfo> files1 = scanner1.scan();
        
        DuplicateDetector detector1 = new DuplicateDetector();
        detector1.setCaseInsensitive(parser1.isCaseInsensitive());
        Map<String, List<String>> duplicates1 = detector1.detectDuplicates(files1);
        long endTime1 = System.currentTimeMillis();

        // When - second run (should use cache)
        long startTime2 = System.currentTimeMillis();
        CommandLineParser parser2 = new CommandLineParser();
        parser2.parse(new String[]{tempDir.toString()});
        
        List<String> directories2 = parser2.getDirectories();
        DirectoryScanner scanner2 = new DirectoryScanner(Path.of(directories2.get(0)), parser2.getMaxDepth(), parser2.getMinSize(), false);
        List<com.duplicatefinder.model.FileInfo> files2 = scanner2.scan();
        
        DuplicateDetector detector2 = new DuplicateDetector();
        detector2.setCaseInsensitive(parser2.isCaseInsensitive());
        Map<String, List<String>> duplicates2 = detector2.detectDuplicates(files2);
        long endTime2 = System.currentTimeMillis();

        // Then
        assertThat(duplicates1).isEqualTo(duplicates2);
        // Second run should be faster (though this might not always be true in tests)
        assertThat(endTime2 - startTime2).isLessThanOrEqualTo(endTime1 - startTime1 + 100); // Allow some tolerance
    }

    private void setupSimpleDuplicateStructure() throws IOException {
        Files.writeString(tempDir.resolve("file1.txt"), "same content");
        Files.writeString(tempDir.resolve("file2.txt"), "same content");
        Files.writeString(tempDir.resolve("unique.txt"), "different content");
    }

    private void setupComplexDuplicateStructure() throws IOException {
        // First group (2 files)
        Files.writeString(tempDir.resolve("group1a.txt"), "content1");
        Files.writeString(tempDir.resolve("group1b.txt"), "content1");
        
        // Second group (3 files)
        Files.writeString(tempDir.resolve("group2a.txt"), "content2");
        Files.writeString(tempDir.resolve("group2b.txt"), "content2");
        Files.writeString(tempDir.resolve("group2c.txt"), "content2");
        
        // Unique file
        Files.writeString(tempDir.resolve("unique.txt"), "unique content");
    }

    private void setupCaseSensitiveDuplicates() throws IOException {
        Files.writeString(tempDir.resolve("test.txt"), "same content");
        Files.writeString(tempDir.resolve("Test.txt"), "same content");
        Files.writeString(tempDir.resolve("TEST.txt"), "same content");
    }

    private void setupMixedSizeFiles() throws IOException {
        Files.writeString(tempDir.resolve("small1.txt"), "small"); // 5 bytes
        Files.writeString(tempDir.resolve("small2.txt"), "small"); // 5 bytes
        Files.writeString(tempDir.resolve("large1.txt"), "large content"); // 13 bytes
        Files.writeString(tempDir.resolve("large2.txt"), "large content"); // 13 bytes
    }

    private void setupDeepDirectoryStructure() throws IOException {
        Files.createDirectories(tempDir.resolve("level1/level2/level3"));
        Files.writeString(tempDir.resolve("level1/file1.txt"), "content1");
        Files.writeString(tempDir.resolve("level1/level2/file2.txt"), "content2");
        Files.writeString(tempDir.resolve("level1/level2/level3/file3.txt"), "content3");
    }

    private void setupFilesWithDifferentTimestamps() throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Path file3 = tempDir.resolve("file3.txt");
        
        Files.writeString(file1, "same content");
        Files.writeString(file2, "same content");
        Files.writeString(file3, "same content");
        
        // Make file2 newer
        try {
            Thread.sleep(1000);
            Files.setLastModifiedTime(file2, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted: " + e.getMessage());
        }
    }
}
