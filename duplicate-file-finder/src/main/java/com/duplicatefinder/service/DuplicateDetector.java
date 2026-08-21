package com.duplicatefinder.service;

import com.duplicatefinder.model.FileInfo;
import com.duplicatefinder.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Detects duplicate files using a strictly short-circuited 3-level funnel strategy.
 *
 * <h2>The Funnel Strategy — Why This Order?</h2>
 *
 * <pre>
 *  Level 1 — Name comparison   (CPU-only,  O(n),          cheapest)
 *  Level 2 — Size comparison   (CPU-only,  O(candidates), cheap)
 *  Level 3 — CRC32 computation (Disk I/O,  O(candidates), MOST expensive)
 * </pre>
 *
 * <p>Each level drastically reduces the candidate set before the next, more expensive
 * level runs.  In a typical home directory with 50 000 files, Level 1 might reduce
 * the set to 2 000 files; Level 2 might further reduce it to 300; Level 3 only hashes
 * those 300.  Without this funnel, we would hash all 50 000 files — a 166× penalty.</p>
 *
 * <h2>Thread Safety in Level 3</h2>
 * <p>
 * CRC32 computation is CPU/IO bound and safe to parallelise because each thread reads
 * a different file.  The only shared mutable state is the {@code groupedByCrc} map.
 * We use {@code ConcurrentHashMap<Long, List<FileInfo>>} where each list is wrapped in
 * {@code Collections.synchronizedList(new ArrayList<>())}.
 * </p>
 * <p>
 * <b>Why not {@code CopyOnWriteArrayList}?</b><br>
 * {@code CopyOnWriteArrayList.add()} copies the entire underlying array on every call.
 * For a CRC32 key shared by 500 duplicate files that means 500 full-array copy operations
 * (~125 000 element copies), causing GC thrashing that completely negates parallelism gains.
 * {@code Collections.synchronizedList(new ArrayList<>())} uses a single intrinsic monitor
 * lock on add — cheap, and the list grows in-place.
 * </p>
 */
public class DuplicateDetector {

    private static final Logger logger = LoggerFactory.getLogger(DuplicateDetector.class);

    /** Number of threads = logical CPU count (including hyper-threads). */
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    private boolean caseInsensitive = false;
    private Consumer<Integer> progressListener;

    /**
     * Tracks how many files were actually submitted to the CRC32 hasher during the
     * last call to {@link #detectDuplicates(List)}.  Used by tests to verify
     * the funnel is correctly short-circuiting (empty files and size-mismatches
     * must NOT appear in this count).
     */
    private final AtomicInteger lastCrc32CandidateCount = new AtomicInteger(0);

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    public void setCaseInsensitive(boolean caseInsensitive) {
        this.caseInsensitive = caseInsensitive;
    }

    /**
     * Registers a progress listener.  The listener receives the count of files whose
     * CRC32 has been computed so far during Level 3.  It is called after every single
     * file (not every 10th) so that the {@link com.duplicatefinder.util.ProgressBar}
     * can display a smooth, accurate progress percentage.
     */
    public void setProgressListener(Consumer<Integer> progressListener) {
        this.progressListener = progressListener;
    }

    /**
     * Returns how many non-empty files were submitted to the CRC32 hasher during the
     * last invocation of {@link #detectDuplicates(List)}.  Zero if the most recent
     * call had only empty-file candidates.
     */
    public int getLastCrc32CandidateCount() {
        return lastCrc32CandidateCount.get();
    }

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------

    /**
     * Detects duplicate files using a 3-level funnel.
     *
     * @param files all files discovered by the directory scanner
     * @return map of {@code (canonical-path → all-duplicate-paths-including-itself)}
     */
    public Map<String, List<String>> detectDuplicates(List<FileInfo> files) {
        logger.info("Starting duplicate detection for {} files", files.size());

        // ── Level 1: Name grouping ── (O(n), CPU-only)
        // Cost: one HashMap.computeIfAbsent per file → amortised O(1).
        // Short-circuit: groups with only 1 member are dropped immediately.
        Map<String, List<FileInfo>> byName = groupByFileName(files);
        logger.info("Level 1 (by name): {} candidate groups remaining", byName.size());

        // ── Level 2: Size filtering ── (O(candidates), CPU-only)
        // Cost: one long comparison per file in each name-group.
        // I/O cost: ZERO — size was captured by the directory scanner via stat().
        // Short-circuit: groups with 2+ files of different sizes are dropped.
        Map<String, List<FileInfo>> byNameAndSize = groupByNameAndSize(byName);
        logger.info("Level 2 (by name+size): {} candidate groups remaining", byNameAndSize.size());

        // ── Level 3: CRC32 checksum ── (disk I/O, only for final candidates)
        // Cost: reading the full file content for every candidate — the expensive step.
        // This is the ONLY level that touches disk; Levels 1 and 2 are pure metadata.
        Map<String, List<String>> duplicates = groupByCrc32(byNameAndSize);
        logger.info("Level 3 (by CRC32): {} confirmed duplicate groups", duplicates.size());

        return duplicates;
    }

    // -------------------------------------------------------------------------
    // Level 1: Group by file name
    // -------------------------------------------------------------------------

    private Map<String, List<FileInfo>> groupByFileName(List<FileInfo> files) {
        Map<String, List<FileInfo>> grouped = new HashMap<>();

        for (FileInfo file : files) {
            // Normalise name for comparison (case-insensitive mode is optional).
            String key = caseInsensitive ? file.getName().toLowerCase() : file.getName();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(file);
        }

        // Drop singleton groups — a file with a unique name cannot be a duplicate.
        return grouped.entrySet().stream()
            .filter(e -> e.getValue().size() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // -------------------------------------------------------------------------
    // Level 2: Group by name + size
    // -------------------------------------------------------------------------

    private Map<String, List<FileInfo>> groupByNameAndSize(Map<String, List<FileInfo>> byName) {
        Map<String, List<FileInfo>> grouped = new HashMap<>();

        for (List<FileInfo> nameGroup : byName.values()) {
            for (FileInfo file : nameGroup) {
                // Composite key: "filename|sizeInBytes"
                // Size was obtained from BasicFileAttributes during the directory scan —
                // a free stat() result, no additional I/O required here.
                String normalizedName = caseInsensitive ? file.getName().toLowerCase() : file.getName();
                String key = normalizedName + "|" + file.getSize();
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(file);
            }
        }

        // Drop groups where all files have the same name but different sizes.
        // Different sizes → guaranteed different content → cannot be duplicates.
        return grouped.entrySet().stream()
            .filter(e -> e.getValue().size() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // -------------------------------------------------------------------------
    // Level 3: Group by CRC32 (parallel, thread-safe)
    // -------------------------------------------------------------------------

    /**
     * Computes CRC32 checksums in parallel and groups files by their checksum.
     *
     * <h3>Empty-file fast-path</h3>
     * <p>Files with {@code size == 0} are partitioned out before the thread pool is
     * even created.  They receive a hardcoded CRC32 of {@code 0L} (the mathematical
     * CRC32 of zero bytes) without opening a FileChannel.  This avoids a
     * {@code open()} + {@code read()} + {@code close()} round-trip per empty file.</p>
     *
     * <h3>Thread-safe list accumulation</h3>
     * <p>{@code Collections.synchronizedList(new ArrayList<>())} is used as the
     * value type.  The list grows in-place under a single intrinsic lock on each
     * {@code add()} call — no array copying, no GC pressure.  The ConcurrentHashMap
     * guarantees that only one thread ever wins the {@code computeIfAbsent} race
     * for a given CRC32 key, so the list reference itself is stable.</p>
     */
    private Map<String, List<String>> groupByCrc32(Map<String, List<FileInfo>> byNameAndSize) {

        // Thread-safe map: CRC32 → list of files with that checksum.
        // Value lists are synchronizedList wrappers to make .add() thread-safe.
        Map<Long, List<FileInfo>> groupedByCrc = new ConcurrentHashMap<>();

        AtomicInteger processedCount = new AtomicInteger(0);

        // ── Flatten the candidate map into a single list ──
        List<FileInfo> allCandidates = byNameAndSize.values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toList());

        // ── Partition: empty files vs. files that need hashing ──
        // Partitioning happens before the thread pool starts, on the calling thread.
        // This is O(candidates) and does zero I/O.
        List<FileInfo> emptyFiles    = new ArrayList<>();
        List<FileInfo> nonEmptyFiles = new ArrayList<>();

        for (FileInfo file : allCandidates) {
            if (file.getSize() == 0) {
                emptyFiles.add(file);
            } else {
                nonEmptyFiles.add(file);
            }
        }

        // ── Fast-path: assign CRC32 = 0L to all empty files immediately ──
        // No I/O whatsoever — just a HashMap insertion per empty file.
        for (FileInfo emptyFile : emptyFiles) {
            emptyFile.setCrc32(HashUtil.EMPTY_FILE_CRC32);
            groupedByCrc.computeIfAbsent(
                    HashUtil.EMPTY_FILE_CRC32,
                    k -> Collections.synchronizedList(new ArrayList<>())
            ).add(emptyFile);
        }

        // Record how many non-empty files will actually be hashed.
        lastCrc32CandidateCount.set(nonEmptyFiles.size());

        int totalToProcess = nonEmptyFiles.size();
        logger.info("Level 3: {} empty-file fast-paths, {} files to hash using {} threads",
                emptyFiles.size(), totalToProcess, THREAD_POOL_SIZE);

        // ── Parallel CRC32 computation for non-empty files ──
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<?>> futures  = new ArrayList<>(totalToProcess);

        try {
            for (FileInfo file : nonEmptyFiles) {
                Future<?> future = executor.submit(() -> {
                    try {
                        long crc32 = HashUtil.computeCrc32(java.nio.file.Paths.get(file.getPath()));
                        file.setCrc32(crc32);

                        // Thread-safe add: synchronizedList's add() acquires the list's
                        // intrinsic monitor, which is cheap (uncontended in practice because
                        // collisions on the same CRC32 key are rare for non-duplicate files).
                        groupedByCrc.computeIfAbsent(
                                crc32,
                                k -> Collections.synchronizedList(new ArrayList<>())
                        ).add(file);

                        int done = processedCount.incrementAndGet();
                        if (progressListener != null) {
                            progressListener.accept(done);
                        }
                        logger.debug("Hashed ({}/{}) {}", done, totalToProcess, file.getPath());

                    } catch (Exception e) {
                        logger.error("Failed to compute CRC32 for: {}", file.getPath(), e);
                    }
                });
                futures.add(future);
            }

            // Wait for all hashing tasks to finish before proceeding to result assembly.
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Interrupted while waiting for CRC32 tasks", e);
                } catch (ExecutionException e) {
                    logger.error("CRC32 task threw an exception", e.getCause());
                }
            }

        } finally {
            // Always shut down the thread pool — failure to do so leaks OS threads.
            // shutdown() lets in-flight tasks complete; shutdownNow() is the safety net.
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.warn("Thread pool did not terminate in 60 s; forcing shutdown.");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("CRC32 phase complete. Hashed {} files.", processedCount.get());

        // ── Assemble the final result ──
        // Only keep groups with 2+ members — those are confirmed duplicates.
        Map<String, List<String>> result = new HashMap<>();
        for (List<FileInfo> group : groupedByCrc.values()) {
            if (group.size() > 1) {
                List<String> paths = group.stream()
                    .map(FileInfo::getPath)
                    .map(path -> java.nio.file.Paths.get(path).toAbsolutePath().toString())
                    .sorted()
                    .collect(Collectors.toList());
                // Sort before choosing the representative so parallel completion order
                // cannot change output or cache/export comparisons between runs.
                result.put(paths.get(0), paths);
            }
        }

        return result;
    }
}


