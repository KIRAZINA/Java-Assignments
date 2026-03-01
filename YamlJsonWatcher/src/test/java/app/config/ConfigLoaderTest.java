package app.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConfigLoader}.
 *
 * <p>Note: Environment variables cannot be set programmatically in Java.
 * Tests cover fallback default-loading and the neutral-override logic via
 * package-private helper methods.
 */
class ConfigLoaderTest {

    private final ConfigLoader loader = new ConfigLoader();

    // ── Default config from config.yaml ───────────────────────────────────────

    @Test
    void load_returnsNonNullConfig() {
        AppConfig config = loader.load();
        assertNotNull(config);
    }

    @Test
    void load_defaultDirectoriesAreSet() {
        AppConfig config = loader.load();
        assertNotNull(config.inputDir());
        assertFalse(config.inputDir().isBlank());
        assertNotNull(config.processingDir());
        assertFalse(config.processingDir().isBlank());
        assertNotNull(config.outputDir());
        assertFalse(config.outputDir().isBlank());
        assertNotNull(config.archiveDir());
        assertFalse(config.archiveDir().isBlank());
        assertNotNull(config.errorDir());
        assertFalse(config.errorDir().isBlank());
    }

    @Test
    void load_defaultExtensionsContainJsonAndYaml() {
        AppConfig config = loader.load();
        List<String> exts = config.extensions();
        assertNotNull(exts);
        assertFalse(exts.isEmpty());
        assertTrue(exts.contains(".json"),  "Extensions must include .json");
        assertTrue(exts.contains(".yaml"),  "Extensions must include .yaml");
    }

    @Test
    void load_defaultDebounceMsIsPositive() {
        AppConfig config = loader.load();
        assertTrue(config.debounceMs() > 0);
    }

    @Test
    void load_defaultLogLevelIsNotNull() {
        AppConfig config = loader.load();
        assertNotNull(config.logLevel());
        assertFalse(config.logLevel().isBlank());
    }

    @Test
    void load_defaultLargeFileRowThresholdIsPositive() {
        AppConfig config = loader.load();
        assertTrue(config.largeFileRowThreshold() > 0,
                "largeFileRowThreshold should default to a positive value");
    }



    @Test
    void resolve_whenEnvVarAbsent_returnsFallback() {
        // env var "YAML_WATCHER_NONEXISTENT_KEY" should not be set in any CI
        String result = loader.resolve("YAML_WATCHER_NONEXISTENT_KEY", "myDefault");
        assertEquals("myDefault", result);
    }

    // ── resolveLong() helper ──────────────────────────────────────────────────

    @Test
    void resolveLong_whenEnvVarAbsent_returnsFallback() {
        long result = loader.resolveLong("YAML_WATCHER_NONEXISTENT_LONG", 1234L);
        assertEquals(1234L, result);
    }
}
