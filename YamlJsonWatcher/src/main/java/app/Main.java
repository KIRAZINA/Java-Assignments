package app;

import app.config.AppConfig;
import app.config.ConfigLoader;
import app.watcher.DirectoryWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application entry point for the YamlJsonWatcher daemon.
 *
 * <p>Start-up sequence:
 * <ol>
 *   <li>Load configuration from {@code config.yaml} + environment overrides.</li>
 *   <li>Register a JVM shutdown hook for graceful termination.</li>
 *   <li>Start the {@link DirectoryWatcher} on the main thread (blocks until stopped).</li>
 * </ol>
 *
 * <p>Run:
 * <pre>{@code java -jar YamlJsonWatcher-1.0-SNAPSHOT.jar}</pre>
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("=== YamlJsonWatcher starting ===");

        AppConfig      config  = new ConfigLoader().load();
        DirectoryWatcher watcher = new DirectoryWatcher(config);

        // Graceful shutdown on SIGTERM / Ctrl-C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received — stopping watcher...");
            watcher.stop();
        }, "shutdown-hook"));

        // Block the main thread; watcher loop runs here
        watcher.run();

        log.info("=== YamlJsonWatcher stopped ===");
    }
}