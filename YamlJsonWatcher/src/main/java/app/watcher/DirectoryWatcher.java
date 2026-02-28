package app.watcher;

import app.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Monitors a directory using Java NIO {@link WatchService} for newly created or
 * modified files, then delegates to {@link FileEventHandler} for processing.
 *
 * <p>Design choices:
 * <ul>
 *   <li>Events are debounced using a {@link ConcurrentHashMap} keyed by Path so that
 *       a rapid burst of ENTRY_MODIFY events for the same file results in a single
 *       conversion attempt.</li>
 *   <li>Each event is dispatched asynchronously on a cached thread pool, so large
 *       files do not block the watch loop.</li>
 *   <li>{@link AtomicBoolean#set(boolean) running} flag lets the shutdown hook stop
 *       the loop cleanly without {@link Thread#interrupt()}.</li>
 * </ul>
 */
public class DirectoryWatcher implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(DirectoryWatcher.class);

    private final AppConfig        config;
    private final FileEventHandler handler;
    private final AtomicBoolean    running  = new AtomicBoolean(false);

    /** Maps Path → timestamp of last queued event (for debouncing). */
    private final ConcurrentHashMap<Path, Long> debounceMap = new ConcurrentHashMap<>();

    /** Thread pool for dispatching conversion tasks without blocking the watch loop. */
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "converter-worker");
        t.setDaemon(true);
        return t;
    });

    public DirectoryWatcher(AppConfig config) {
        this.config  = config;
        this.handler = new FileEventHandler(config);
    }

    /** Constructor for testing with a custom event handler. */
    public DirectoryWatcher(AppConfig config, FileEventHandler handler) {
        this.config  = config;
        this.handler = handler;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Signals the watcher loop to stop at the next poll timeout. */
    public void stop() {
        running.set(false);
        log.info("DirectoryWatcher stop requested.");
    }

    /** Returns true if the watcher is currently running. */
    public boolean isRunning() {
        return running.get();
    }

    // ── Runnable entry point ───────────────────────────────────────────────────

    @Override
    public void run() {
        Path watchDir = Paths.get(config.targetDir()).toAbsolutePath();

        try {
            Files.createDirectories(watchDir);
        } catch (IOException e) {
            log.error("Cannot create watch directory {}: {}", watchDir, e.getMessage());
            return;
        }

        log.info("Watching directory: {}", watchDir);

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            watchDir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
            running.set(true);

            while (running.get()) {
                WatchKey key;
                try {
                    // Poll with a timeout so we can check the running flag regularly
                    key = watchService.poll(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (key == null) {
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == OVERFLOW) {
                        log.warn("WatchService OVERFLOW — some events may have been missed.");
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path relative = pathEvent.context();
                    Path absolute = watchDir.resolve(relative);

                    dispatchDebounced(absolute);
                }

                boolean valid = key.reset();
                if (!valid) {
                    log.warn("Watch key invalidated — directory may have been deleted.");
                    break;
                }
            }
        } catch (IOException e) {
            log.error("WatchService error: {}", e.getMessage(), e);
        } finally {
            running.set(false);
            shutdownExecutor();
            log.info("DirectoryWatcher stopped.");
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Debounces the event: if the same path was already queued within
     * {@link AppConfig#debounceMs()} milliseconds, the new event is discarded.
     */
    private void dispatchDebounced(Path path) {
        long now      = System.currentTimeMillis();
        long window   = config.debounceMs();

        Long last = debounceMap.get(path);
        if (last != null && (now - last) < window) {
            log.debug("Debounced duplicate event for {}", path.getFileName());
            return;
        }
        debounceMap.put(path, now);

        // Short delay to let the OS finish writing the file before we read it
        executor.submit(() -> {
            try {
                Thread.sleep(Math.min(window / 2, 500));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            handler.handle(path);
        });
    }

    private void shutdownExecutor() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
