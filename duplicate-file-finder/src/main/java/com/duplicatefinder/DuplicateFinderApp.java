package com.duplicatefinder;

import com.duplicatefinder.cli.CommandLineParser;
import com.duplicatefinder.cli.DuplicateFinderCommand;
import com.duplicatefinder.model.FileInfo;
import com.duplicatefinder.service.DirectoryScanner;
import com.duplicatefinder.service.DuplicateDetector;
import com.duplicatefinder.util.FileUtils;
import com.duplicatefinder.util.HashUtil;
import com.duplicatefinder.util.ProgressBar;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main application class for the Duplicate File Finder.
 *
 * <h2>Entry Point Strategy</h2>
 * <p>
 * {@link #main(String[])} delegates to picocli's {@link CommandLine#execute} which
 * invokes {@link DuplicateFinderCommand#call()}.  That method populates a
 * {@link CommandLineParser} POJO and calls {@link #run(CommandLineParser)} here.
 * Splitting the picocli layer from the business logic means unit tests can call
 * {@code run()} directly with a pre-populated parser, without any CLI parsing overhead.
 * </p>
 *
 * <h2>Deterministic Deletion</h2>
 * <p>
 * When {@code --keep-newest} or {@code --keep-oldest} is specified, the file to keep
 * is chosen by a two-key comparator:
 * <ol>
 *   <li>Primary key: {@code lastModified} (ascending for oldest, descending for newest)</li>
 *   <li>Tie-breaker: {@code absolutePath.compareTo()} ascending — the lexicographically
 *       first path is always kept when two files share the exact same {@code lastModified}
 *       timestamp.</li>
 * </ol>
 * <b>Why the tie-breaker matters for idempotency:</b>
 * Without it, {@code Stream.min/max} makes an arbitrary choice when timestamps collide
 * (the JDK spec only guarantees "some element", not "a consistent element").  Running
 * the tool twice on the same set of equal-timestamp duplicates could delete a different
 * file each time.  The lexicographic tie-breaker makes every run produce the exact same
 * outcome — safe to run repeatedly without unintended data loss.
 * </p>
 */
public class DuplicateFinderApp {

    private static final Logger logger = LoggerFactory.getLogger(DuplicateFinderApp.class);

    // ─────────────────────────────────────────────────────────────────────────
    // JVM entry point
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Main entry point.  Delegates fully to picocli for argument parsing,
     * help, version, and ANSI-aware usage text.
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new DuplicateFinderCommand())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(args);
        System.exit(exitCode);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Business logic entry point (callable directly from tests)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Executes the full duplicate-detection lifecycle:
     * <ol>
     *   <li>Phase 1 — Scan directories (progress bar updates per file)</li>
     *   <li>Phase 2 — Filter by size (spinner; effectively instantaneous)</li>
     *   <li>Phase 3 — Compute CRC32 checksums in parallel (progress bar updates per file)</li>
     *   <li>Display / export / delete results</li>
     *   <li>Atomically flush the cache to disk (in a finally block)</li>
     * </ol>
     *
     * @param parser pre-populated {@link CommandLineParser} POJO
     * @return exit code (0 = success)
     */
    public static int run(CommandLineParser parser) {
        List<String> directories = parser.getDirectories();
        if (directories.isEmpty()) {
            printError("No directories specified. Use --help for usage information.");
            return 1;
        }

        try {
            Map<String, List<String>> allDuplicates = new HashMap<>();

            // ── Shared phase-aware progress bar ─────────────────────────────
            ProgressBar progress = new ProgressBar(0, "");

            for (String dir : directories) {
                logger.info("Processing directory: {}", dir);
                Path dirPath = Path.of(dir);

                if (!Files.isDirectory(dirPath)) {
                    printWarning("Path is not a directory, skipping: " + dir);
                    logger.warn("Skipping non-directory: {}", dir);
                    continue;
                }

                // ── Phase 1: Directory scan ──────────────────────────────────
                // Progress fires on every 100th file during the walk.
                DirectoryScanner scanner = new DirectoryScanner(
                    dirPath, parser.getMaxDepth(), parser.getMinSize(), false);

                progress.setPhase(ProgressBar.Phase.SCANNING, 0);
                AtomicInteger scannedCount = new AtomicInteger(0);
                scanner.setProgressListener(count -> {
                    int delta = count - scannedCount.getAndSet(count);
                    progress.update(delta);
                    if (count % 100 == 0) {
                        logger.info("Scanned {} files...", count);
                    }
                });

                List<FileInfo> files = scanner.scan();
                progress.complete();
                logger.info("Found {} files in {}", files.size(), dir);

                // ── Phase 2: Size filtering (fast — just a spinner tick) ──────
                progress.setPhase(ProgressBar.Phase.FILTERING, files.size());
                progress.update(1);  // single tick — filtering is sub-millisecond

                // ── Phase 3: CRC32 computation ───────────────────────────────
                DuplicateDetector detector = new DuplicateDetector();
                detector.setCaseInsensitive(parser.isCaseInsensitive());

                // Wire detector progress to the progress bar's HASHING phase.
                // The total is the number of non-empty CRC32 candidates (set lazily
                // after the detector partitions empty vs. non-empty files).
                // We use a AtomicInteger to allow the lambda to update the bar.
                AtomicInteger hashingTotal = new AtomicInteger(0);

                detector.setProgressListener(count -> {
                    // On first update, switch the bar to HASHING phase with the real total.
                    if (hashingTotal.compareAndSet(0, detector.getLastCrc32CandidateCount())) {
                        progress.setPhase(ProgressBar.Phase.HASHING,
                                          detector.getLastCrc32CandidateCount());
                    }
                    progress.setCurrent(count);
                    if (count % 10 == 0) {
                        logger.info("Hashed {}/{} files...", count,
                                    detector.getLastCrc32CandidateCount());
                    }
                });

                Map<String, List<String>> duplicates = detector.detectDuplicates(files);
                progress.setPhase(ProgressBar.Phase.DONE, 0);

                allDuplicates.putAll(duplicates);
            }

            // ── Display results ──────────────────────────────────────────────
            if (allDuplicates.isEmpty()) {
                System.out.println(Ansi.AUTO.string("@|yellow No duplicates found.|@"));
                logger.info("No duplicates found.");
            } else {
                displayDuplicates(allDuplicates);

                if (parser.isDelete()) {
                    handleDeletion(allDuplicates, parser);
                }

                if (parser.getExport() != null) {
                    exportResults(allDuplicates, parser.getExport());
                }
            }

            return 0;

        } catch (Exception e) {
            printError("Unexpected error: " + e.getMessage());
            logger.error("Unexpected error", e);
            return 1;
        } finally {
            // ── Atomic cache flush ───────────────────────────────────────────
            // Always runs, even if an exception bubbled up.
            // HashUtil.saveCache() uses Files.move(ATOMIC_MOVE) internally, so the
            // cache file on disk is never left in a partially-written state.
            try {
                HashUtil.saveCache();
                logger.info("Cache flushed successfully.");
            } catch (Exception e) {
                logger.warn("Cache flush failed: {}", e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Display
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Prints each duplicate group with ANSI colour.
     * Green highlights the group header; duplicates are printed in plain text.
     */
    private static void displayDuplicates(Map<String, List<String>> duplicates) {
        System.out.println(Ansi.AUTO.string(
            "\n@|bold,green === Duplicate Files Found: " + duplicates.size() + " group(s) ===|@\n"));

        for (Map.Entry<String, List<String>> entry : duplicates.entrySet()) {
            String representative = entry.getKey();
            List<String> allPaths = entry.getValue();

            System.out.println(Ansi.AUTO.string("@|cyan  Original:|@ " + representative));
            for (String path : allPaths) {
                if (!path.equals(representative)) {
                    System.out.println("    Duplicate: " + path);
                }
            }
            System.out.println();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deletion with deterministic tie-breaker
    // ─────────────────────────────────────────────────────────────────────────

    private static void handleDeletion(Map<String, List<String>> duplicates,
                                       CommandLineParser parser) {
        logger.info("Starting deletion process...");
        int deletedCount = 0;

        for (Map.Entry<String, List<String>> entry : duplicates.entrySet()) {
            String keepFile = determineFileToKeep(entry.getValue(), parser);
            List<String> toDelete = new ArrayList<>(entry.getValue());
            toDelete.remove(keepFile);

            for (String filePath : toDelete) {
                if (FileUtils.deleteFile(Path.of(filePath))) {
                    deletedCount++;
                    logger.info("Deleted: {}", filePath);
                } else {
                    printWarning("Could not delete: " + filePath);
                }
            }
        }

        System.out.println(Ansi.AUTO.string(
            "\n@|bold,green Deleted " + deletedCount + " duplicate file(s).|@"));
        logger.info("Deleted {} duplicate files.", deletedCount);
    }

    /**
     * Determines which file to keep from a duplicate group.
     *
     * <h3>Tie-Breaker Contract</h3>
     * <p>
     * When two or more files share the exact same {@code lastModified} timestamp
     * (which can occur when files are copied programmatically within the same
     * filesystem tick, or when their timestamps are explicitly equalised in tests),
     * the primary comparator produces a tie.  Without a secondary key, {@code max()}
     * / {@code min()} returns an <em>arbitrary</em> element — the stream's encounter
     * order is not guaranteed to be stable under all JVM implementations.
     * </p>
     * <p>
     * The secondary key is the absolute file path in lexicographic ascending order.
     * This makes the choice deterministic and idempotent:
     * <ul>
     *   <li>Given the same set of files, the same path is always chosen.</li>
     *   <li>Re-running the tool after a first deletion never "changes its mind".</li>
     *   <li>The choice is predictable and easy to reason about in tests.</li>
     * </ul>
     * </p>
     */
    public static String determineFileToKeep(List<String> files, CommandLineParser parser) {
        if (parser.isKeepNewest()) {
            // Primary: highest lastModified wins (newest).
            // Tie-breaker: lowest path string (lexicographically first) wins.
            return files.stream()
                .max(Comparator
                    .comparing(DuplicateFinderApp::lastModifiedOf)
                    .thenComparing(Comparator.comparing((String p) ->
                        Path.of(p).toAbsolutePath().toString()).reversed()))
                .orElse(files.get(0));
        } else if (parser.isKeepOldest()) {
            // Primary: lowest lastModified wins (oldest).
            // Tie-breaker: lowest path string (lexicographically first) wins.
            return files.stream()
                .min(Comparator
                    .comparing(DuplicateFinderApp::lastModifiedOf)
                    .thenComparing(p -> Path.of(p).toAbsolutePath().toString()))
                .orElse(files.get(0));
        } else {
            // Default: no timestamp preference — keep lexicographically first path.
            return files.stream()
                .min(Comparator.comparing(p -> Path.of(p).toAbsolutePath().toString()))
                .orElse(files.get(0));
        }
    }

    /** Reads the last-modified {@link FileTime} for a path, returning epoch 0 on error. */
    private static FileTime lastModifiedOf(String path) {
        try {
            return Files.getLastModifiedTime(Path.of(path));
        } catch (IOException e) {
            logger.warn("Could not read lastModified for: {}", path, e);
            return FileTime.fromMillis(0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Export
    // ─────────────────────────────────────────────────────────────────────────

    private static void exportResults(Map<String, List<String>> duplicates, String format) {
        logger.info("Exporting results as {}...", format);
        if ("json".equalsIgnoreCase(format)) exportAsJson(duplicates);
        else if ("csv".equalsIgnoreCase(format)) exportAsCsv(duplicates);
        else printWarning("Unknown export format: " + format);
    }

    private static void exportAsJson(Map<String, List<String>> duplicates) {
        try {
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename  = "duplicate_files_" + timestamp + ".json";

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("timestamp",            LocalDateTime.now().toString());
            data.put("totalDuplicateGroups", duplicates.size());
            data.put("duplicates",           duplicates);

            mapper.writeValue(Path.of(filename).toFile(), data);
            System.out.println("Results exported to: " + filename);
            logger.info("JSON export written to {}", filename);
        } catch (IOException e) {
            printError("Failed to export JSON: " + e.getMessage());
        }
    }

    private static void exportAsCsv(Map<String, List<String>> duplicates) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename  = "duplicate_files_" + timestamp + ".csv";

            try (FileWriter w = new FileWriter(filename)) {
                w.write("Original File,Duplicate File,Group Size\n");
                for (Map.Entry<String, List<String>> e : duplicates.entrySet()) {
                    for (String dup : e.getValue()) {
                        if (!dup.equals(e.getKey())) {
                            w.write(String.format("\"%s\",\"%s\",%d%n",
                                e.getKey().replace("\"", "\"\""),
                                dup.replace("\"", "\"\""),
                                e.getValue().size()));
                        }
                    }
                }
            }
            System.out.println("Results exported to: " + filename);
            logger.info("CSV export written to {}", filename);
        } catch (IOException e) {
            printError("Failed to export CSV: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ANSI-aware output helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Prints a red error message. Degrades to plain text on non-ANSI terminals. */
    private static void printError(String msg) {
        System.err.println(Ansi.AUTO.string("@|red Error: " + msg + "|@"));
        logger.error(msg);
    }

    /** Prints a yellow warning message. Degrades to plain text on non-ANSI terminals. */
    private static void printWarning(String msg) {
        System.out.println(Ansi.AUTO.string("@|yellow Warning: " + msg + "|@"));
        logger.warn(msg);
    }
}
