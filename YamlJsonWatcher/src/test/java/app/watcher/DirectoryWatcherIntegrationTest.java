package app.watcher;

import app.config.AppConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link DirectoryWatcher} + {@link FileEventHandler}.
 *
 * <p>Tests use a {@link TempDir} as the watched directory and wait up to 5 seconds
 * for expected output files to appear (WatchService + debounce timing).
 */
class DirectoryWatcherIntegrationTest {

    @TempDir
    Path watchDir;

    private AppConfig        config;
    private DirectoryWatcher watcher;
    private Thread           watcherThread;

    @BeforeEach
    void setUp() throws InterruptedException {
        config = new AppConfig(
                watchDir.toString(),   // targetDir
                watchDir.toString(),   // outputDir (same)
                List.of(".json", ".yaml", ".yml"),
                300L,                  // debounceMs (fast for tests)
                "INFO",
                "logs/test.log",
                500                    // largeFileRowThreshold
        );
        watcher       = new DirectoryWatcher(config);
        watcherThread = new Thread(watcher, "watcher-test");
        watcherThread.setDaemon(true);
        watcherThread.start();

        // Give the WatchService a moment to register
        Thread.sleep(500);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        watcher.stop();
        watcherThread.join(3_000);
    }

    // ── JSON input ────────────────────────────────────────────────────────────

    @Test
    @Timeout(10)
    void dropJsonFile_producesYamlFile() throws IOException, InterruptedException {
        String json = "{\"hello\":\"world\",\"count\":7}";
        Path src = watchDir.resolve("test_output.json");
        Files.writeString(src, json);

        Path expected = watchDir.resolve("test_output.yaml");
        waitForFile(expected, 5);

        assertTrue(Files.exists(expected), "YAML output file should be created");
        String content = Files.readString(expected);
        assertTrue(content.contains("hello"), "YAML should contain key 'hello'");
        assertTrue(content.contains("world"), "YAML should contain value 'world'");
    }

    // ── YAML input ────────────────────────────────────────────────────────────

    @Test
    @Timeout(10)
    void dropYamlFile_producesJsonFile() throws IOException, InterruptedException {
        String yaml = "name: integration\nvalue: 99\n";
        Path src = watchDir.resolve("test_output.yaml");
        Files.writeString(src, yaml);

        Path expected = watchDir.resolve("test_output.json");
        waitForFile(expected, 5);

        assertTrue(Files.exists(expected), "JSON output file should be created");
        String content = Files.readString(expected);
        assertTrue(content.contains("integration"), "JSON should contain 'integration'");
        assertTrue(content.contains("99"),           "JSON should contain value 99");
    }

    // ── Temp file ignored ─────────────────────────────────────────────────────

    @Test
    @Timeout(6)
    void dropTempFile_producesNoOutput() throws IOException, InterruptedException {
        Files.writeString(watchDir.resolve("~temp.json"), "{\"x\":1}");
        Files.writeString(watchDir.resolve("ignored.tmp"), "{\"y\":2}");

        // Wait a generous window and then assert nothing was created
        Thread.sleep(2_500);

        long outputCount = Files.list(watchDir)
                .filter(p -> p.getFileName().toString().endsWith(".yaml"))
                .count();
        assertEquals(0, outputCount, "No YAML output should be generated for temp files");
    }

    // ── Invalid JSON ignored ──────────────────────────────────────────────────

    @Test
    @Timeout(6)
    void dropInvalidJsonFile_producesNoOutput() throws IOException, InterruptedException {
        Path src = watchDir.resolve("broken.json");
        Files.writeString(src, "{\"key\": NOTVALID");

        // Source file must remain untouched
        Thread.sleep(2_500);

        assertTrue(Files.exists(src), "Source file must remain after failed conversion");
        assertFalse(Files.exists(watchDir.resolve("broken.yaml")),
                "No YAML output should exist for invalid JSON");
    }

    // ── .yml extension ────────────────────────────────────────────────────────

    @Test
    @Timeout(10)
    void dropYmlFile_producesJsonFile() throws IOException, InterruptedException {
        String yaml = "app:\n  name: test\n  port: 8080\n";
        Path src = watchDir.resolve("config.yml");
        Files.writeString(src, yaml);

        Path expected = watchDir.resolve("config.json");
        waitForFile(expected, 5);

        assertTrue(Files.exists(expected));
        String content = Files.readString(expected);
        assertTrue(content.contains("8080"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Polls until {@code path} exists or {@code timeoutSeconds} elapses.
     */
    private void waitForFile(Path path, int timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (!Files.exists(path) && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
    }
}
