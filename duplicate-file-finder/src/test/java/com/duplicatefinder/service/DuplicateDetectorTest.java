package com.duplicatefinder.service;

import com.duplicatefinder.DuplicateFinderApp;
import com.duplicatefinder.cli.CommandLineParser;
import com.duplicatefinder.model.FileInfo;
import com.duplicatefinder.util.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit and integration tests for {@link DuplicateDetector}.
 *
 * <p>Covers the strict 3-level funnel, empty-file fast-path, progress listener contract,
 * and deterministic deletion tie-breaking logic.  All tests that touch the filesystem
 * use {@code @TempDir} for isolation.</p>
 */
@DisplayName("DuplicateDetector Tests")
class DuplicateDetectorTest {

    private DuplicateDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DuplicateDetector();
        // Clear the HashUtil cache before each test so cache state from prior tests
        // does not bleed through and produce false positives or negatives.
        HashUtil.clearCache();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Existing tests (preserved, unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should not report files with same name but different size")
    void testNoDuplicatesForDifferentSize() {
        List<FileInfo> files = Arrays.asList(
            new FileInfo("test.txt", "/dir1/test.txt", 100, Instant.now()),
            new FileInfo("test.txt", "/dir2/test.txt", 200, Instant.now())
        );
        assertThat(detector.detectDuplicates(files)).isEmpty();
    }

    @Test
    @DisplayName("Should be case-sensitive by default")
    void testCaseSensitiveDetection() {
        List<FileInfo> files = Arrays.asList(
            new FileInfo("Test.txt", "/dir1/Test.txt", 100, Instant.now()),
            new FileInfo("test.txt", "/dir2/test.txt", 100, Instant.now())
        );
        assertThat(detector.detectDuplicates(files)).isEmpty();
    }

    @Test
    @DisplayName("Should not report files with unique names")
    void testNoUniqueFilesDuplicated() {
        List<FileInfo> files = Arrays.asList(
            new FileInfo("file1.txt", "/dir/file1.txt", 100, Instant.now()),
            new FileInfo("file2.txt", "/dir/file2.txt", 100, Instant.now()),
            new FileInfo("file3.txt", "/dir/file3.txt", 100, Instant.now())
        );
        assertThat(detector.detectDuplicates(files)).isEmpty();
    }

    @Test
    @DisplayName("Should report multiple duplicate groups")
    void testMultipleDuplicateGroups(@TempDir Path tempDir) throws IOException {
        Path dir1 = Files.createDirectories(tempDir.resolve("dir1"));
        Path dir2 = Files.createDirectories(tempDir.resolve("dir2"));

        Files.writeString(dir1.resolve("file1.txt"), "content1");
        Files.writeString(dir2.resolve("file1.txt"), "content1");
        Files.writeString(dir1.resolve("file2.txt"), "content2");
        Files.writeString(dir2.resolve("file2.txt"), "content2");

        List<FileInfo> files = new DirectoryScanner(tempDir, 10).scan();
        assertThat(detector.detectDuplicates(files)).hasSize(2);
    }

    @Test
    @DisplayName("Should handle empty file list")
    void testEmptyFileList() {
        assertThat(detector.detectDuplicates(List.of())).isEmpty();
    }

    @Test
    @DisplayName("Should include all duplicate paths in the result list")
    void testAllDuplicatesIncluded(@TempDir Path tempDir) throws IOException {
        for (String sub : List.of("dir1", "dir2", "dir3")) {
            Path d = Files.createDirectories(tempDir.resolve(sub));
            Files.writeString(d.resolve("test.txt"), "duplicate content");
        }
        List<FileInfo> files = new DirectoryScanner(tempDir, 10).scan();
        Map<String, List<String>> dups = detector.detectDuplicates(files);

        long totalPaths = dups.values().stream().flatMap(List::stream).count();
        assertThat(totalPaths).isEqualTo(3);
    }

    @Test
    @DisplayName("Should work with a single duplicate pair")
    void testSingleDuplicatePair(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("dup.bin"), "test content");
        Files.writeString(
            Files.createDirectories(tempDir.resolve("sub")).resolve("dup.bin"), "test content");

        List<FileInfo> files = new DirectoryScanner(tempDir, 10).scan();
        Map<String, List<String>> dups = detector.detectDuplicates(files);

        assertThat(dups).hasSize(1);
        assertThat(dups.values().iterator().next()).hasSize(2);
    }

    @Test
    @DisplayName("Should support progress listener")
    void testProgressListener(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("f1.txt"), "hello");
        Files.writeString(
            Files.createDirectories(tempDir.resolve("d")).resolve("f1.txt"), "hello");

        List<FileInfo> files = new DirectoryScanner(tempDir, 10).scan();

        java.util.concurrent.atomic.AtomicInteger seen = new java.util.concurrent.atomic.AtomicInteger(0);
        detector.setProgressListener(seen::set);

        Map<String, List<String>> dups = detector.detectDuplicates(files);
        assertThat(dups).isNotEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NEW: 3-Level Funnel Short-Circuit verification
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that the 3-level funnel strictly short-circuits at each level.
     *
     * <p>Three file families are constructed:
     * <ol>
     *   <li><b>Group A</b> — same name, <em>different</em> size.  Should be eliminated at Level 2.
     *       The hasher must never be called for these files.</li>
     *   <li><b>Group B</b> — same name, same size, <em>different</em> content.  Passes Level 2,
     *       but CRC32s differ so they must NOT appear as duplicates.</li>
     *   <li><b>Group C</b> — same name, same size, same content.  These are true duplicates
     *       and must trigger the hasher.</li>
     * </ol>
     *
     * <p>Funnel verification: {@code detector.getLastCrc32CandidateCount()} returns exactly
     * the number of files that were submitted to the CRC32 thread pool.  Group A files must
     * NOT be counted because they were eliminated by size-filtering.  Group B and C files
     * will both be counted (they share name+size), but only Group C files appear in the
     * final duplicate map.</p>
     */
    @Test
    @DisplayName("3-Level funnel: only same-name + same-size + same-content files are duplicates")
    void testThreeLevelFunnelShortCircuiting(@TempDir Path tempDir) throws IOException {
        // ── Group A: same name "a.txt", different sizes ──────────────────────
        Path aDir1 = Files.createDirectories(tempDir.resolve("a1"));
        Path aDir2 = Files.createDirectories(tempDir.resolve("a2"));
        Files.writeString(aDir1.resolve("a.txt"), "short");           // 5 bytes
        Files.writeString(aDir2.resolve("a.txt"), "much longer text"); // 16 bytes
        // Expected: eliminated at Level 2 (size mismatch) → NEVER hashed.

        // ── Group B: same name "b.txt", same size, different content ─────────
        Path bDir1 = Files.createDirectories(tempDir.resolve("b1"));
        Path bDir2 = Files.createDirectories(tempDir.resolve("b2"));
        Files.writeString(bDir1.resolve("b.txt"), "content1"); // 8 bytes
        Files.writeString(bDir2.resolve("b.txt"), "content2"); // 8 bytes
        // Expected: passes Level 2, hashed, CRC32 differs → NOT reported as duplicate.

        // ── Group C: same name "c.txt", same size, same content ──────────────
        Path cDir1 = Files.createDirectories(tempDir.resolve("c1"));
        Path cDir2 = Files.createDirectories(tempDir.resolve("c2"));
        Path cDir3 = Files.createDirectories(tempDir.resolve("c3"));
        Files.writeString(cDir1.resolve("c.txt"), "identical");  // 9 bytes
        Files.writeString(cDir2.resolve("c.txt"), "identical");  // 9 bytes
        Files.writeString(cDir3.resolve("c.txt"), "identical");  // 9 bytes
        // Expected: passes all 3 levels → reported as one duplicate group of 3.

        List<FileInfo> files = new DirectoryScanner(tempDir, 10).scan();
        Map<String, List<String>> duplicates = detector.detectDuplicates(files);

        // ── Assertion 1: only Group C appears in the result ──────────────────
        assertThat(duplicates).hasSize(1)
            .withFailMessage("Exactly one duplicate group (Group C) must be detected.");

        List<String> groupC = duplicates.values().iterator().next();
        assertThat(groupC).hasSize(3)
            .withFailMessage("Group C must contain all 3 identical files.");

        // ── Assertion 2: funnel short-circuiting ─────────────────────────────
        // Group A (2 files) was eliminated at Level 2 (size mismatch) so the hasher
        // was never called for them.  Only Group B (2 files) + Group C (3 files) = 5
        // files were actually submitted to the CRC32 thread pool.
        int expectedHashedFiles = 5; // 2 (Group B) + 3 (Group C)
        assertThat(detector.getLastCrc32CandidateCount())
            .as("Only files that passed name+size filter should reach the hasher")
            .isEqualTo(expectedHashedFiles);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NEW: Empty-file fast-path
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that 10 empty files are correctly identified as duplicates without
     * throwing an exception and without attempting to open a FileChannel.
     *
     * <p>All empty files share CRC32 = {@code 0L} by definition.  The fast-path in
     * {@link com.duplicatefinder.util.HashUtil#computeCrc32(Path)} and in
     * {@link DuplicateDetector}'s Level 3 partition must handle this case gracefully.</p>
     */
    @Test
    @DisplayName("Empty-file fast-path: 10 empty files detected as one duplicate group")
    void testEmptyFileFastPath(@TempDir Path tempDir) throws IOException {
        int emptyFileCount = 10;

        // Create 10 subdirectories each containing an identically-named empty file.
        for (int i = 0; i < emptyFileCount; i++) {
            Path sub = Files.createDirectories(tempDir.resolve("sub" + i));
            Files.createFile(sub.resolve("empty.dat")); // zero bytes
        }

        List<FileInfo> files = new DirectoryScanner(tempDir, 10).scan();

        // All 10 files are empty → should produce exactly ONE duplicate group of 10.
        assertThatNoException()
            .as("Empty-file fast-path must not throw any exception")
            .isThrownBy(() -> {
                Map<String, List<String>> dups = detector.detectDuplicates(files);

                assertThat(dups).hasSize(1)
                    .withFailMessage("All 10 empty files must form one duplicate group.");

                assertThat(dups.values().iterator().next()).hasSize(emptyFileCount)
                    .withFailMessage("The duplicate group must contain all 10 empty files.");
            });

        // The hasher was NOT called for any of these files (they were fast-pathed).
        assertThat(detector.getLastCrc32CandidateCount())
            .as("Empty files must bypass the CRC32 thread pool entirely")
            .isEqualTo(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NEW: Deterministic deletion tie-breaker
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that when three identical files share the exact same {@code lastModified}
     * timestamp, the deterministic tie-breaker (lexicographic path order) keeps the
     * lexicographically first file and is idempotent across repeated calls.
     *
     * <p>Why {@code FileTime.fromMillis(0)}?  We need a single, known epoch value that all
     * three files can be set to.  Using the current time risks a race condition where
     * two files land in different filesystem timestamp ticks.  Epoch 0 (1970-01-01 00:00:00 UTC)
     * is a distant, stable value guaranteed to be representable on all filesystems.</p>
     */
    @Test
    @DisplayName("Deterministic deletion: lexicographically first path is kept when timestamps tie")
    void testDeterministicDeletionWithTiedTimestamps(@TempDir Path tempDir) throws IOException {
        // Create 3 identical files in lexicographically ordered paths.
        Path fileA = Files.createDirectories(tempDir.resolve("aaa")).resolve("dup.txt");
        Path fileB = Files.createDirectories(tempDir.resolve("bbb")).resolve("dup.txt");
        Path fileC = Files.createDirectories(tempDir.resolve("ccc")).resolve("dup.txt");

        String content = "identical content for tie-breaker test";
        Files.writeString(fileA, content);
        Files.writeString(fileB, content);
        Files.writeString(fileC, content);

        // Force all three to epoch 0 — guaranteed timestamp tie.
        FileTime epoch = FileTime.fromMillis(0);
        Files.setLastModifiedTime(fileA, epoch);
        Files.setLastModifiedTime(fileB, epoch);
        Files.setLastModifiedTime(fileC, epoch);

        List<String> paths = List.of(
            fileA.toAbsolutePath().toString(),
            fileB.toAbsolutePath().toString(),
            fileC.toAbsolutePath().toString()
        );

        // Build a parser configured for --keep-newest (the strategy that uses .max()).
        CommandLineParser keepNewestParser = new CommandLineParser();
        keepNewestParser.setKeepNewest(true);

        // Build a parser configured for --keep-oldest (the strategy that uses .min()).
        CommandLineParser keepOldestParser = new CommandLineParser();
        keepOldestParser.setKeepOldest(true);

        String keptByNewest = DuplicateFinderApp.determineFileToKeep(paths, keepNewestParser);
        String keptByOldest = DuplicateFinderApp.determineFileToKeep(paths, keepOldestParser);

        // The lexicographically first path (fileA/"aaa/dup.txt") must always be kept.
        String expectedKept = fileA.toAbsolutePath().toString();

        assertThat(keptByNewest)
            .as("--keep-newest with tied timestamps must keep lexicographically first path")
            .isEqualTo(expectedKept);

        assertThat(keptByOldest)
            .as("--keep-oldest with tied timestamps must keep lexicographically first path")
            .isEqualTo(expectedKept);

        // Idempotency check: calling again with the same input must produce the same output.
        assertThat(DuplicateFinderApp.determineFileToKeep(paths, keepNewestParser))
            .as("Result must be idempotent across repeated calls")
            .isEqualTo(keptByNewest);
    }
}
